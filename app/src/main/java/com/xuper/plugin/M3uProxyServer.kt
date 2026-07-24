package com.xuper.plugin

import android.content.Context
import android.util.Log
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.InputStream
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit

class M3uProxyServer(private val context: Context) {

    var onCookiesRefreshed: ((d: String, s: String, t: String) -> Unit)? = null
    var onCookiesExpired: (() -> Unit)? = null
    var onStatusChanged: ((String) -> Unit)? = null

    private var serverSocket: ServerSocket? = null
    private val executor = Executors.newFixedThreadPool(4)
    private var scheduler: ScheduledExecutorService? = null
    private val TAG = "XuperProxy"

    @Volatile
    private var running = false

    @Volatile
    private var currentPlaylistUrl: String = ""

    @Volatile
    private var currentSegmentUrl: String = ""

    @Volatile
    private var cookieD: String = ""

    @Volatile
    private var cookieS: String = ""

    @Volatile
    private var cookieT: String = ""

    @Volatile
    private var port: Int = 0

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(15, TimeUnit.SECONDS)
        .followRedirects(true)
        .build()

    fun start(
        playlistUrl: String,
        segmentUrl: String,
        cookieD: String,
        cookieS: String,
        cookieT: String
    ) {
        if (running) stop()

        this.currentPlaylistUrl = playlistUrl
        this.currentSegmentUrl = segmentUrl
        this.cookieD = cookieD
        this.cookieS = cookieS
        this.cookieT = cookieT

        serverSocket = ServerSocket(0)
        port = serverSocket!!.localPort
        running = true

        notifyStatus("Listening on :$port")

        Thread {
            try {
                while (running) {
                    val client = serverSocket?.accept() ?: continue
                    executor.submit { handleClient(client) }
                }
            } catch (e: Exception) {
                if (running) {
                    Log.e(TAG, "Accept loop error", e)
                    notifyStatus("Error: ${e.message}")
                }
            }
        }.start()

        startCookieRefresh()
        notifyStatus("Proxy started on :$port")
        Log.d(TAG, "Started on port $port")
    }

    fun stop() {
        running = false
        scheduler?.shutdownNow()
        scheduler = null
        try {
            serverSocket?.close()
        } catch (_: Exception) {}
        serverSocket = null
        executor.shutdownNow()
        Log.d(TAG, "Stopped")
        notifyStatus("Stopped")
    }

    fun isRunning(): Boolean = running

    fun getProxyUrl(): String = "http://127.0.0.1:$port/playlist.m3u"

    fun updateCookies(d: String, s: String, t: String) {
        cookieD = d
        cookieS = s
        cookieT = t
        Log.d(TAG, "Cookies updated")
    }

    private fun handleClient(socket: Socket) {
        try {
            val input = socket.getInputStream().bufferedReader()
            val requestLine = input.readLine() ?: return
            val parts = requestLine.split(" ")
            if (parts.size < 2) return

            val method = parts[0]
            val path = parts[1]

            if (method != "GET") {
                sendResponse(socket, 405, "text/plain", "Method Not Allowed".toByteArray())
                return
            }

            val headers = mutableMapOf<String, String>()
            while (true) {
                val line = input.readLine() ?: break
                if (line.isEmpty()) break
                val colon = line.indexOf(':')
                if (colon > 0) {
                    headers[line.substring(0, colon).trim().lowercase()] =
                        line.substring(colon + 1).trim()
                }
            }

            when {
                path == "/playlist.m3u" || path == "/playlist.m3u?" -> handlePlaylist(socket)
                path.startsWith("/segment/") -> handleSegment(socket, path, headers)
                else -> sendResponse(socket, 404, "text/plain", "Not Found".toByteArray())
            }
        } catch (e: Exception) {
            Log.e(TAG, "Client error", e)
            try {
                sendResponse(socket, 500, "text/plain", "Internal Error".toByteArray())
            } catch (_: Exception) {}
        } finally {
            try { socket.close() } catch (_: Exception) {}
        }
    }

    private fun handlePlaylist(socket: Socket) {
        if (currentPlaylistUrl.isBlank()) {
            sendResponse(socket, 503, "text/plain", "No playlist URL set".toByteArray())
            return
        }

        try {
            val request = Request.Builder()
                .url(currentPlaylistUrl)
                .addHeader("Cookie", cookieString())
                .addHeader("User-Agent", "okhttp/4.12.0")
                .addHeader("Accept", "*/*")
                .get()
                .build()

            val response = httpClient.newCall(request).execute()
            val body = response.body?.string() ?: ""

            if (!response.isSuccessful) {
                Log.w(TAG, "Playlist fetch failed: ${response.code}")
                sendResponse(
                    socket,
                    response.code,
                    "text/plain",
                    "Upstream error ${response.code}".toByteArray()
                )
                return
            }

            val rewritten = rewriteM3u(body)
            val bytes = rewritten.toByteArray(Charsets.UTF_8)
            sendResponse(socket, 200, "audio/x-mpegurl", bytes)
            Log.d(TAG, "Served rewritten playlist (${bytes.size} bytes, ${countSegments(rewritten)} segments)")

        } catch (e: Exception) {
            Log.e(TAG, "Playlist rewrite failed", e)
            sendResponse(socket, 502, "text/plain", "Upstream error".toByteArray())
        }
    }

    private fun rewriteM3u(content: String): String {
        val sb = StringBuilder()
        var segmentIndex = 0
        var pendingStart = -1L
        var pendingEnd = -1L

        for (line in content.lines()) {
            val trimmed = line.trim()

            if (trimmed.startsWith("#EXT-SEGMENT:")) {
                val tagBody = trimmed.substringAfter("#EXT-SEGMENT:")
                val parts = tagBody.split(",")
                if (parts.size >= 2) {
                    pendingStart = parts[0].trim().toLongOrNull() ?: -1L
                    pendingEnd = parts[1].trim().toLongOrNull() ?: -1L
                }
                sb.appendLine(trimmed)
                continue
            }

            if (trimmed.startsWith("#")) {
                sb.appendLine(trimmed)
                continue
            }

            if (trimmed.isEmpty()) {
                sb.appendLine()
                continue
            }

            if (pendingStart >= 0 && pendingEnd >= 0) {
                sb.appendLine("/segment/$segmentIndex?range=$pendingStart-$pendingEnd")
                segmentIndex++
                pendingStart = -1L
                pendingEnd = -1L
            } else {
                sb.appendLine(trimmed)
            }
        }

        return sb.toString()
    }

    private fun countSegments(content: String): Int {
        return content.lines().count { it.trim().startsWith("/segment/") }
    }

    private fun handleSegment(socket: Socket, path: String, headers: Map<String, String>) {
        if (currentSegmentUrl.isBlank()) {
            sendResponse(socket, 503, "text/plain", "No segment URL set".toByteArray())
            return
        }

        val queryStart = path.indexOf('?')
        val query = if (queryStart >= 0) path.substring(queryStart + 1) else ""

        val rangeParam = query.split("&")
            .firstOrNull { it.startsWith("range=") }
            ?.substringAfter("=") ?: ""

        if (rangeParam.isBlank()) {
            sendResponse(socket, 400, "text/plain", "Missing range parameter".toByteArray())
            return
        }

        val rangeParts = rangeParam.split("-")
        if (rangeParts.size != 2) {
            sendResponse(socket, 400, "text/plain", "Invalid range format".toByteArray())
            return
        }

        val rangeStart = rangeParts[0]
        val rangeEnd = rangeParts[1]

        try {
            val request = Request.Builder()
                .url(currentSegmentUrl)
                .addHeader("Cookie", cookieString())
                .addHeader("Range", "bytes=$rangeStart-$rangeEnd")
                .addHeader("User-Agent", "okhttp/4.12.0")
                .addHeader("Accept", "*/*")
                .get()
                .build()

            val response = httpClient.newCall(request).execute()

            if (!response.isSuccessful && response.code != 206) {
                Log.w(TAG, "Segment fetch failed: ${response.code} range=$rangeStart-$rangeEnd")
                sendResponse(
                    socket,
                    response.code,
                    "text/plain",
                    "Upstream error ${response.code}".toByteArray()
                )
                return
            }

            val responseBody = response.body ?: run {
                sendResponse(socket, 502, "text/plain", "Empty upstream body".toByteArray())
                return
            }

            val contentType = response.header("Content-Type") ?: "video/mp2t"
            val contentLength = responseBody.contentLength()

            val headerBuilder = StringBuilder()
            headerBuilder.append("HTTP/1.1 206 Partial Content\r\n")
            headerBuilder.append("Content-Type: $contentType\r\n")
            if (contentLength > 0) {
                headerBuilder.append("Content-Length: $contentLength\r\n")
            }
            headerBuilder.append("Connection: close\r\n")
            headerBuilder.append("Accept-Ranges: bytes\r\n")
            headerBuilder.append("\r\n")

            val out = socket.getOutputStream()
            out.write(headerBuilder.toString().toByteArray())
            out.flush()

            val buffer = ByteArray(8192)
            val inputStream: InputStream = responseBody.byteStream()
            var bytesRead: Int
            while (inputStream.read(buffer).also { bytesRead = it } != -1) {
                out.write(buffer, 0, bytesRead)
            }
            out.flush()

            Log.d(TAG, "Served segment range=$rangeStart-$rangeEnd ($contentLength bytes)")

        } catch (e: Exception) {
            Log.e(TAG, "Segment proxy error", e)
            try {
                sendResponse(socket, 502, "text/plain", "Segment fetch failed".toByteArray())
            } catch (_: Exception) {}
        }
    }

    private fun startCookieRefresh() {
        scheduler = Executors.newSingleThreadScheduledExecutor()
        scheduler?.scheduleAtFixedRate({
            refreshCookies()
        }, 90, 90, TimeUnit.SECONDS)
    }

    private fun refreshCookies() {
        if (currentPlaylistUrl.isBlank() || !running) return

        try {
            val request = Request.Builder()
                .url(currentPlaylistUrl)
                .addHeader("Cookie", cookieString())
                .addHeader("User-Agent", "okhttp/4.12.0")
                .addHeader("Accept", "*/*")
                .get()
                .build()

            val response = httpClient.newCall(request).execute()

            if (response.code == 409 || response.code == 410) {
                Log.w(TAG, "Cookie refresh: session expired (HTTP ${response.code})")
                notifyStatus("Cookies expired (${response.code})")
                onCookiesExpired?.invoke()
                return
            }

            if (!response.isSuccessful) {
                Log.w(TAG, "Cookie refresh failed: HTTP ${response.code}")
                return
            }

            val setCookies = response.headers("Set-Cookie")
            if (setCookies.isNotEmpty()) {
                var newD = cookieD
                var newS = cookieS
                var newT = cookieT

                for (cookie in setCookies) {
                    val nameValue = cookie.substringBefore(";").trim()
                    val name = nameValue.substringBefore("=").trim()
                    val value = nameValue.substringAfter("=").trim()

                    when (name) {
                        "d" -> newD = value
                        "s" -> newS = value
                        "t" -> newT = value
                    }
                }

                if (newD != cookieD || newS != cookieS || newT != cookieT) {
                    cookieD = newD
                    cookieS = newS
                    cookieT = newT
                    Log.d(TAG, "Cookies refreshed from Set-Cookie headers")
                    onCookiesRefreshed?.invoke(cookieD, cookieS, cookieT)
                    notifyStatus("Cookies refreshed")
                }
            }

            response.close()
        } catch (e: Exception) {
            Log.e(TAG, "Cookie refresh error", e)
        }
    }

    private fun cookieString(): String {
        return "d=$cookieD; s=$cookieS; t=$cookieT"
    }

    private fun sendResponse(socket: Socket, code: Int, contentType: String, body: ByteArray) {
        val statusText = when (code) {
            200 -> "OK"
            206 -> "Partial Content"
            400 -> "Bad Request"
            404 -> "Not Found"
            405 -> "Method Not Allowed"
            500 -> "Internal Server Error"
            502 -> "Bad Gateway"
            503 -> "Service Unavailable"
            else -> "Error"
        }

        val header = "HTTP/1.1 $code $statusText\r\n" +
            "Content-Type: $contentType\r\n" +
            "Content-Length: ${body.size}\r\n" +
            "Connection: close\r\n\r\n"

        val out = socket.getOutputStream()
        out.write(header.toByteArray())
        out.write(body)
        out.flush()
    }

    private fun notifyStatus(status: String) {
        Log.d(TAG, status)
        onStatusChanged?.invoke(status)
    }
}
