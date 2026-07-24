package com.xuper.plugin

import android.content.Context
import android.content.SharedPreferences
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.put
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.util.concurrent.TimeUnit

@Serializable
data class XuperConfig(
    val apiHost: String = "23.94.64.155:30822",
    val apiHostBackup: String = "",
    val useHttps: Boolean = false,
    val cookieD: String = "",
    val cookieS: String = "",
    val cookieT: String = "",
    val userId: String = "",
    val userToken: String = "",
    val portalCode: String = "",
    val streamUserKey: String = "cyx_93531158996778016",
    val cdnMain: String = "magloud.y6oseldsc.online",
    val cdnBackup: String = "caeo.wvdbozpfc.com",
    val email: String = "",
    val password: String = "",
    val playlistPath: String = "",
    val segmentPath: String = ""
)

@Serializable
data class XuperChannel(
    val id: Long = 0,
    val name: String = "",
    val logo: String = "",
    val streamUrl: String = "",
    val groupTitle: String = "",
    val epgChannelId: String = "",
    val number: Int = 0,
    val channelCode: String = "",
    val columnId: Int = 0
)

class XuperApiClient(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences("xuper_config", Context.MODE_PRIVATE)

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        coerceInputValues = true
    }

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(45, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .followRedirects(true)
        .build()

    var config: XuperConfig
        get() = try {
            json.decodeFromString(prefs.getString("config", "{}") ?: "{}")
        } catch (_: Exception) {
            XuperConfig()
        }
        set(value) {
            prefs.edit().putString("config", json.encodeToString(value)).apply()
        }

    fun isSessionReady(): Boolean {
        val c = config
        return c.cookieS.isNotBlank() && c.cookieT.isNotBlank() && c.cookieD.isNotBlank()
    }

    fun cookieHeader(): String {
        val c = config
        return "d=${c.cookieD}; s=${c.cookieS}; t=${c.cookieT}"
    }

    private fun baseUrl(host: String): String {
        val scheme = if (config.useHttps) "https" else "http"
        return "$scheme://$host"
    }

    private fun requestBuilder(url: String): Request.Builder {
        return Request.Builder()
            .url(url)
            .addHeader("Accept", "*/*")
            .addHeader("User-Agent", "okhttp/4.12.0")
            .apply {
                if (isSessionReady()) {
                    addHeader("Cookie", cookieHeader())
                }
            }
    }

    /** Logical portalCore path (pre-encryption). Wire path is encrypted by app interceptors. */
    fun portalUrl(host: String, logicalPath: String): String {
        val path = if (logicalPath.startsWith("/")) logicalPath else "/$logicalPath"
        return "${baseUrl(host)}$path"
    }

    /** Build a URL using an opaque encrypted path against a host. */
    fun opaqueUrl(host: String, path: String): String {
        val h = host.ifBlank { config.apiHost.ifBlank { "23.94.64.155:30822" } }
        val p = if (path.startsWith("/")) path else "/$path"
        return "${baseUrl(h)}$p"
    }

    /**
     * POST a portalCore call. For needEncrypt endpoints (the default — only the
     * sports/gameId endpoints are annotated needEncrypt:false in jd/a), the JSON
     * body is 3DES-encrypted on the wire and the response is 3DES-decrypted, via
     * XuperCrypto (recovered from app classes nb.b/rd.c). Response decrypt falls
     * back to raw if it wasn't actually encrypted.
     */
    private fun postJson(
        host: String,
        logicalPath: String,
        body: String,
        encrypt: Boolean = true
    ): Pair<Int, String?> {
        val url = portalUrl(host, logicalPath)
        val media = "application/json; charset=utf-8".toMediaType()
        val wireBody = if (encrypt) XuperCrypto.encryptBody(body) else body
        val req = requestBuilder(url)
            .post(wireBody.toRequestBody(media))
            .addHeader("Content-Type", "application/json;charset=utf-8")
            .addHeader("Cache-Control", "no-store")
            .build()
        return try {
            client.newCall(req).execute().use { resp ->
                val raw = resp.body?.string()
                val decoded = if (encrypt && !raw.isNullOrBlank()) {
                    XuperCrypto.decryptBody(raw) ?: raw
                } else {
                    raw
                }
                resp.code to decoded
            }
        } catch (e: IOException) {
            -1 to e.message
        }
    }

    private fun get(host: String, path: String): Pair<Int, String?> {
        val url = portalUrl(host, path)
        val req = requestBuilder(url).get().build()
        return try {
            client.newCall(req).execute().use { resp ->
                resp.code to resp.body?.string()
            }
        } catch (e: IOException) {
            -1 to e.message
        }
    }

    fun testSession(): Result<String> {
        if (!isSessionReady()) {
            return Result.failure(IOException("Paste cookies d, s, t first"))
        }
        val c = config
        val host = c.apiHost.ifBlank { "23.94.64.155:30822" }

        // try opaque playlist path first if set
        if (c.playlistPath.isNotBlank()) {
            val (code, body) = get(host, c.playlistPath)
            if (code in 200..399) {
                val preview = body?.take(200) ?: ""
                return Result.success("Opaque playlist OK: HTTP $code (${body?.length ?: 0} bytes)\n$preview")
            }
            if (code == 410) {
                return Result.failure(IOException("Session expired (410) — capture fresh cookies from XTV app"))
            }
            if (code == 409) {
                return Result.failure(IOException("Auth rejected (409) — cookies invalid or IP mismatch"))
            }
            // opaque path failed, fall through to legacy test
        }

        // legacy: try plain root or token-based path
        val (code2, _) = get(host, "/")
        return if (code2 > 0) {
            Result.failure(IOException("Connected ($host) but path rejected. code=$code2. Need opaque playlist path in config."))
        } else {
            Result.failure(IOException("Cannot reach $host: code=$code2"))
        }
    }

    fun probeCdn(): String {
        val key = config.streamUserKey.ifBlank { return "no stream key" }
        val paths = listOf(
            "http://${config.cdnMain}/live/$key/",
            "http://${config.cdnBackup}/live/$key/"
        )
        val out = mutableListOf<String>()
        for (u in paths) {
            try {
                val req = Request.Builder().url(u).head().build()
                client.newCall(req).execute().use { resp ->
                    out += "${resp.code} $u"
                }
            } catch (e: Exception) {
                out += "err ${e.message} $u"
            }
        }
        return out.joinToString(" | ")
    }

    /** Fetch raw M3U8 content via the opaque playlist path. */
    fun fetchRawM3u8(): Result<String> {
        val c = config
        if (c.playlistPath.isBlank()) return Result.failure(IOException("No playlist path set"))
        val host = c.apiHost.ifBlank { "23.94.64.155:30822" }
        val url = opaqueUrl(host, c.playlistPath)
        val req = requestBuilder(url).get().build()
        return try {
            client.newCall(req).execute().use { resp ->
                val body = resp.body?.string() ?: ""
                if (resp.code in 200..399) {
                    Result.success(body)
                } else {
                    Result.failure(IOException("HTTP ${resp.code}: ${body.take(200)}"))
                }
            }
        } catch (e: IOException) {
            Result.failure(e)
        }
    }

    /**
     * Attempt logical login once path-encryption is bypassed or host accepts plain portalCore.
     * Real wire traffic uses encrypted paths — this is best-effort.
     */
    fun loginV8(): Result<String> {
        val c = config
        if (c.email.isBlank() || c.password.isBlank()) {
            return Result.failure(IOException("email/password empty"))
        }
        val body = buildJsonObject {
            put("email", c.email)
            put("password", c.password)
        }.toString()
        val (code, resp) = postJson(c.apiHost, "/api/portalCore/v8/login", body)
        if (code <= 0) return Result.failure(IOException("network: $resp"))
        if (resp.isNullOrBlank()) return Result.failure(IOException("HTTP $code empty body"))

        // capture Set-Cookie if any was stored by cookie jar — we parse body for tokens too
        return try {
            val obj = json.parseToJsonElement(resp).jsonObject
            val returnCode = obj["returnCode"]?.jsonPrimitive?.contentOrNull
                ?: obj["code"]?.jsonPrimitive?.contentOrNull
            val data = obj["data"]?.jsonObject
            val token = data?.get("userToken")?.jsonPrimitive?.contentOrNull
                ?: data?.get("token")?.jsonPrimitive?.contentOrNull
                ?: ""
            val userId = data?.get("userId")?.jsonPrimitive?.contentOrNull ?: ""
            if (!token.isNullOrBlank()) {
                config = config.copy(userToken = token, userId = userId)
            }
            Result.success("HTTP $code returnCode=$returnCode token=${token.take(12)}…")
        } catch (e: Exception) {
            Result.success("HTTP $code raw=${resp.take(200)}")
        }
    }

    fun getLiveData(): Result<List<XuperChannel>> {
        val c = config
        val body = buildJsonObject {
            if (c.userId.isNotBlank()) put("userId", c.userId)
            if (c.userToken.isNotBlank()) put("userToken", c.userToken)
            if (c.portalCode.isNotBlank()) put("portalCode", c.portalCode)
        }.toString()

        val (code, resp) = postJson(c.apiHost, "/api/portalCore/v6/getLiveData", body)
        if (code <= 0) return Result.failure(IOException("network: $resp"))
        if (resp.isNullOrBlank()) {
            // plain path likely blocked/encrypted — fall back to synthetic channel from known stream key
            return Result.success(syntheticChannelsFromStreamKey())
        }

        return try {
            val root = json.parseToJsonElement(resp)
            val channels = parseChannels(root)
            if (channels.isEmpty()) Result.success(syntheticChannelsFromStreamKey())
            else Result.success(channels)
        } catch (_: Exception) {
            Result.success(syntheticChannelsFromStreamKey())
        }
    }

    private fun parseChannels(root: JsonElement): List<XuperChannel> {
        val obj = root as? JsonObject ?: return emptyList()
        val data = obj["data"]
        val listEl: JsonArray? = when (data) {
            is JsonArray -> data
            is JsonObject -> data["channelList"] as? JsonArray
            else -> null
        } ?: obj["channelList"] as? JsonArray

        if (listEl == null) return emptyList()

        return listEl.mapNotNull { el ->
            val ch = el as? JsonObject ?: return@mapNotNull null
            val name = ch.str("name") ?: ch.str("channelName") ?: return@mapNotNull null
            val code = ch.str("channelCode") ?: ch.str("code") ?: ""
            val logo = ch.str("logo") ?: ch.str("icon") ?: ""
            val group = ch.str("categoryName") ?: ch.str("columnName") ?: ch.str("group") ?: ""
            val num = ch["number"]?.jsonPrimitive?.intOrNull
                ?: ch["channelNumber"]?.jsonPrimitive?.intOrNull
                ?: 0
            val id = ch["id"]?.jsonPrimitive?.longOrNull ?: num.toLong()
            val stream = resolveStreamUrl(code, ch.str("playUrl") ?: ch.str("url"))
            XuperChannel(
                id = id,
                name = name,
                logo = logo,
                streamUrl = stream,
                groupTitle = group,
                epgChannelId = code,
                number = num,
                channelCode = code,
                columnId = ch["columnId"]?.jsonPrimitive?.intOrNull ?: 0
            )
        }
    }

    private fun JsonObject.str(key: String): String? =
        this[key]?.jsonPrimitive?.contentOrNull?.takeIf { it.isNotBlank() }

    private fun resolveStreamUrl(channelCode: String, direct: String?): String {
        if (!direct.isNullOrBlank()) return direct
        val key = config.streamUserKey
        if (key.isBlank()) return ""
        // known CDN layout from pcap — channel-specific segment names unknown without startPlayLive
        return "http://${config.cdnMain}/live/$key/"
    }

    fun syntheticChannelsFromStreamKey(): List<XuperChannel> {
        val key = config.streamUserKey.ifBlank { return emptyList() }
        return listOf(
            XuperChannel(
                id = 1,
                name = "Xuper Live (main CDN)",
                streamUrl = "http://${config.cdnMain}/live/$key/",
                groupTitle = "Xuper",
                channelCode = key,
                number = 1
            ),
            XuperChannel(
                id = 2,
                name = "Xuper Live (backup CDN)",
                streamUrl = "http://${config.cdnBackup}/live/$key/",
                groupTitle = "Xuper",
                channelCode = key,
                number = 2
            )
        )
    }

    fun startPlayLive(channelCode: String, columnId: Int = 0): Result<String> {
        val c = config
        val body = buildJsonObject {
            put("channelCode", channelCode)
            put("type", "live")
            if (c.portalCode.isNotBlank()) put("portalCode", c.portalCode)
            if (c.userId.isNotBlank()) put("userId", c.userId)
            if (c.userToken.isNotBlank()) put("userToken", c.userToken)
            if (columnId > 0) put("columnId", columnId)
        }.toString()
        val (code, resp) = postJson(c.apiHost, "/api/portalCore/v4/startPlayLive", body)
        if (code <= 0) return Result.failure(IOException("network: $resp"))
        if (resp.isNullOrBlank()) return Result.failure(IOException("HTTP $code empty (path encrypt?)"))
        return try {
            val obj = json.parseToJsonElement(resp).jsonObject
            val data = obj["data"]?.jsonObject
            val list = data?.get("liveAddressList")?.jsonArray
            val first = list?.firstOrNull()?.jsonObject
            val play = first?.str("playCode")
                ?: first?.str("url")
                ?: data?.str("url")
                ?: ""
            if (play.isBlank()) Result.failure(IOException("no play url in response"))
            else Result.success(play)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun generateM3u(): Result<String> {
        val channelsResult = getLiveData()
        if (channelsResult.isFailure) {
            return Result.failure(channelsResult.exceptionOrNull()!!)
        }
        val channels = channelsResult.getOrNull().orEmpty()
        if (channels.isEmpty()) {
            return Result.failure(IOException("No channels — set stream user key or fix session"))
        }

        val sb = StringBuilder()
        sb.appendLine("#EXTM3U")
        for (ch in channels) {
            val url = ch.streamUrl
            if (url.isBlank()) continue
            sb.append("#EXTINF:-1")
            if (ch.epgChannelId.isNotEmpty()) sb.append(" tvg-id=\"${ch.epgChannelId}\"")
            if (ch.name.isNotEmpty()) sb.append(" tvg-name=\"${ch.name}\"")
            if (ch.logo.isNotEmpty()) sb.append(" tvg-logo=\"${ch.logo}\"")
            if (ch.groupTitle.isNotEmpty()) sb.append(" group-title=\"${ch.groupTitle}\"")
            if (ch.number > 0) sb.append(" tvg-chno=\"${ch.number}\"")
            sb.appendLine(",${ch.name}")
            sb.appendLine(url)
        }
        return Result.success(sb.toString())
    }
}
