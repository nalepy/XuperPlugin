package com.xuper.plugin

import android.app.Service
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.Message
import android.os.Messenger
import android.util.Log

class PluginService : Service() {

    private val binder = Messenger(PluginHandler(this))
    private lateinit var apiClient: XuperApiClient
    private var proxyServer: M3uProxyServer? = null

    override fun onCreate() {
        super.onCreate()
        apiClient = XuperApiClient(this)
        Log.d("XuperPlugin", "PluginService created")
    }

    override fun onDestroy() {
        proxyServer?.stop()
        proxyServer = null
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? {
        if (intent?.action == PluginContract.ACTION_PLUGIN_SERVICE) {
            return binder.binder
        }
        return null
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        return START_NOT_STICKY
    }

    private class PluginHandler(service: PluginService) : Handler(Looper.getMainLooper()) {
        private val serviceRef = service

        override fun handleMessage(msg: Message) {
            val requestData = msg.data ?: Bundle()
            val requestId = requestData.getString(PluginContract.KEY_REQUEST_ID, "")
            val replyTo = msg.replyTo

            val response = Bundle().apply {
                putInt(PluginContract.KEY_API_VERSION, PluginContract.API_VERSION)
                putString(PluginContract.KEY_REQUEST_ID, requestId)
            }

            when (msg.what) {
                PluginContract.MSG_GET_MANIFEST -> {
                    response.putString(PluginContract.KEY_MANIFEST_JSON, PluginContract.MANIFEST_JSON)
                    response.putBoolean(PluginContract.KEY_SUCCESS, true)
                }

                PluginContract.MSG_SET_ENABLED -> {
                    val enabled = requestData.getBoolean(PluginContract.KEY_ENABLED, true)
                    response.putBoolean(PluginContract.KEY_SUCCESS, true)
                    Log.d("XuperPlugin", "Plugin enabled: $enabled")
                }

                PluginContract.MSG_GET_STATUS -> {
                    val ready = serviceRef.apiClient.isSessionReady()
                    val proxyRunning = serviceRef.proxyServer?.isRunning() == true
                    response.putString(
                        PluginContract.KEY_STATUS_LABEL,
                        when {
                            !ready -> "Need cookies d/s/t"
                            proxyRunning -> "Session set, proxy running"
                            else -> "Session cookies set"
                        }
                    )
                    response.putString(
                        PluginContract.KEY_MESSAGE,
                        when {
                            !ready -> "Open plugin settings and paste d, s, t from pcap"
                            proxyRunning -> "Proxy active — playlist served locally"
                            else -> "Ready — open config to start proxy or refresh cookies"
                        }
                    )
                    response.putBoolean(PluginContract.KEY_SUCCESS, true)
                }

                PluginContract.MSG_GET_PROVIDER_URL -> {
                    Thread {
                        val proxy = serviceRef.ensureProxyRunning()
                        val asyncResponse = Bundle().apply {
                            putInt(PluginContract.KEY_API_VERSION, PluginContract.API_VERSION)
                            putString(PluginContract.KEY_REQUEST_ID, requestId)
                            if (proxy != null) {
                                val url = proxy.getProxyUrl()
                                putString(PluginContract.KEY_URL, url)
                                putString(PluginContract.KEY_PROVIDER_NAME, "Xuper BrazilTV")
                                putBoolean(PluginContract.KEY_SUCCESS, true)
                            } else {
                                putBoolean(PluginContract.KEY_SUCCESS, false)
                                putString(PluginContract.KEY_MESSAGE, "Proxy not running — open plugin config and start proxy")
                            }
                        }
                        try {
                            replyTo?.send(Message.obtain(null, 0).apply { data = asyncResponse })
                        } catch (e: Exception) {
                            Log.e("XuperPlugin", "reply failed", e)
                        }
                    }.start()
                    return
                }

                PluginContract.MSG_PREPARE_PLAYBACK -> {
                    val inputUrl = requestData.getString(PluginContract.KEY_INPUT_URL, "") ?: ""
                    val handled = inputUrl.contains("magloud") ||
                        inputUrl.contains("caeo") ||
                        inputUrl.contains("y6oseldsc") ||
                        inputUrl.contains("wvdbozpfc") ||
                        inputUrl.contains("portalCore") ||
                        inputUrl.contains("cyx_") ||
                        inputUrl.contains("127.0.0.1")
                    if (handled) {
                        response.putString(PluginContract.KEY_OUTPUT_URL, inputUrl)
                        response.putString(PluginContract.KEY_STREAM_TYPE, "MPEG_TS")
                        response.putBoolean(PluginContract.KEY_HANDLED, true)
                    } else {
                        response.putBoolean(PluginContract.KEY_HANDLED, false)
                    }
                    response.putBoolean(PluginContract.KEY_SUCCESS, true)
                }

                else -> {
                    response.putBoolean(PluginContract.KEY_SUCCESS, false)
                    response.putString(PluginContract.KEY_MESSAGE, "Unknown message: ${msg.what}")
                }
            }

            try {
                replyTo?.send(Message.obtain(null, 0).apply { this.data = response })
            } catch (e: Exception) {
                Log.e("XuperPlugin", "Failed to send response", e)
            }
        }
    }

    private fun ensureProxyRunning(): M3uProxyServer? {
        val existing = proxyServer
        if (existing != null && existing.isRunning()) return existing

        val c = apiClient.config
        if (c.playlistPath.isBlank() || !apiClient.isSessionReady()) {
            Log.w("XuperPlugin", "Cannot start proxy: playlistPath=${c.playlistPath.isBlank()}, sessionReady=${apiClient.isSessionReady()}")
            return null
        }

        val proxy = M3uProxyServer(this)
        proxy.onCookiesRefreshed = { d, s, t ->
            apiClient.config = apiClient.config.copy(cookieD = d, cookieS = s, cookieT = t)
        }
        proxy.onStatusChanged = { status ->
            Log.d("XuperPlugin", "Proxy: $status")
        }

        val host = c.apiHost.ifBlank { "23.94.64.155:30822" }
        val scheme = if (c.useHttps) "https" else "http"
        val playlistUrl = "$scheme://$host${c.playlistPath}"
        val segmentUrl = if (c.segmentPath.isNotBlank()) "$scheme://$host${c.segmentPath}" else ""

        proxy.start(playlistUrl, segmentUrl, c.cookieD, c.cookieS, c.cookieT)
        proxyServer = proxy
        Log.d("XuperPlugin", "Proxy started → ${proxy.getProxyUrl()}")
        return proxy
    }
}
