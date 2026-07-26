package com.xuper.plugin

import android.app.Activity
import android.graphics.Color
import android.os.Bundle
import android.text.InputType
import android.util.TypedValue
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.LinearLayout.LayoutParams
import android.widget.ScrollView
import android.widget.Space
import android.widget.TextView
import android.util.Log

class ConfigActivity : Activity() {

    private lateinit var apiClient: XuperApiClient
    private lateinit var apiHostInput: EditText
    private lateinit var cookieDInput: EditText
    private lateinit var cookieSInput: EditText
    private lateinit var cookieTInput: EditText
    private lateinit var streamKeyInput: EditText
    private lateinit var userIdInput: EditText
    private lateinit var userTokenInput: EditText
    private lateinit var emailInput: EditText
    private lateinit var passwordInput: EditText
    private lateinit var playlistPathInput: EditText
    private lateinit var segmentPathInput: EditText
    private lateinit var statusText: TextView
    private var proxyServer: M3uProxyServer? = null
    private lateinit var proxyBtn: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        apiClient = XuperApiClient(this)

        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(24), dp(24), dp(24), dp(24))
            setBackgroundColor(Color.parseColor("#121212"))
            layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT)
        }

        layout.addView(TextView(this).apply {
            text = "Xuper BrazilTV Plugin"
            setTextColor(Color.WHITE)
            textSize = 22f
            setPadding(0, 0, 0, dp(8))
        })
        layout.addView(TextView(this).apply {
            text = "Auth = cookies d / s / t from live capture (not plain email login on wire)"
            setTextColor(Color.parseColor("#AAAAAA"))
            textSize = 12f
            setPadding(0, 0, 0, dp(12))
        })

        apiHostInput = createInput(layout, "API Host", "23.94.64.155:30822")
        cookieDInput = createInput(layout, "Cookie d", "paste d= value")
        cookieSInput = createInput(layout, "Cookie s", "paste s= value")
        cookieTInput = createInput(layout, "Cookie t", "paste t= value")
        streamKeyInput = createInput(layout, "Stream user key (cyx_)", "cyx_93531158996778016")
        playlistPathInput = createInput(layout, "Playlist opaque path", "/vs1/qwfwhijbcqraxaoo/swuvq")
        segmentPathInput = createInput(layout, "Segment opaque path", "/aiejqfv/fmydf")
        userIdInput = createInput(layout, "userId (optional)", "")
        userTokenInput = createInput(layout, "userToken (optional)", "")
        emailInput = createInput(layout, "Email (optional v8/login)", "your@email.com")
        passwordInput = createInput(layout, "Password (optional)", "password", isPassword = true)

        val buttonRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, dp(12), 0, dp(8))
        }

        val testBtn = Button(this).apply {
            text = "Test Session"
            setOnClickListener { testSession() }
        }
        buttonRow.addView(testBtn, LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f).apply {
            marginEnd = dp(4)
        })

        val saveBtn = Button(this).apply {
            text = "Save"
            setOnClickListener { saveConfig() }
        }
        buttonRow.addView(saveBtn, LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f).apply {
            marginEnd = dp(4)
        })

        proxyBtn = Button(this).apply {
            text = "Start Proxy"
            setOnClickListener { toggleProxy() }
        }
        buttonRow.addView(proxyBtn, LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f))
        layout.addView(buttonRow)

        val secondRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, 0, 0, dp(8))
        }

        val fetchBtn = Button(this).apply {
            text = "Fetch M3U8"
            setOnClickListener { fetchM3u8() }
        }
        secondRow.addView(fetchBtn, LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f).apply {
            marginEnd = dp(4)
        })

        val loginBtn = Button(this).apply {
            text = "Try v8/login"
            setOnClickListener { tryLogin() }
        }
        secondRow.addView(loginBtn, LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f))
        layout.addView(secondRow)

        statusText = TextView(this).apply {
            setTextColor(Color.parseColor("#AAAAAA"))
            textSize = 13f
            setPadding(0, dp(12), 0, 0)
        }
        layout.addView(statusText)

        setContentView(ScrollView(this).apply { addView(layout) })
        loadConfig()

        android.util.Log.e("XUPER", "Config loaded. Session ready: " + apiClient.isSessionReady())

        // Auto-test on launch — run on background thread
        layout.postDelayed({
            Thread {
                if (!apiClient.isSessionReady()) {
                    android.util.Log.e("XUPER", "No cookies — trying loginV8...")
                    val r = apiClient.loginV8()
                    val msg = "Login: " + (r.getOrNull() ?: r.exceptionOrNull()?.message ?: "?")
                    android.util.Log.e("XUPER", msg)
                    runOnUiThread { statusText.text = msg }
                } else {
                    testSession()
                }
            }.start()
        }, 3000)
    }

    private fun createInput(
        parent: LinearLayout,
        label: String,
        hint: String,
        isPassword: Boolean = false
    ): EditText {
        parent.addView(TextView(this).apply {
            text = label
            setTextColor(Color.parseColor("#AAAAAA"))
            textSize = 12f
            setPadding(0, 0, 0, dp(4))
        })
        val edit = EditText(this).apply {
            this.hint = hint
            setTextColor(Color.WHITE)
            setHintTextColor(Color.parseColor("#666666"))
            setBackgroundColor(Color.parseColor("#1E1E1E"))
            setPadding(dp(12), dp(8), dp(12), dp(8))
            textSize = 14f
            if (isPassword) {
                inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
            } else {
                inputType = InputType.TYPE_CLASS_TEXT
            }
            layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT)
        }
        parent.addView(edit)
        parent.addView(Space(this).apply {
            layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, dp(6))
        })
        return edit
    }

    private fun loadConfig() {
        val c = apiClient.config
        apiHostInput.setText(c.apiHost)
        cookieDInput.setText(c.cookieD)
        cookieSInput.setText(c.cookieS)
        cookieTInput.setText(c.cookieT)
        streamKeyInput.setText(c.streamUserKey)
        playlistPathInput.setText(c.playlistPath)
        segmentPathInput.setText(c.segmentPath)
        userIdInput.setText(c.userId)
        userTokenInput.setText(c.userToken)
        emailInput.setText(c.email)
        passwordInput.setText(c.password)
        updateProxyButton()
        if (apiClient.isSessionReady()) {
            statusText.text = "Status: cookies present"
            statusText.setTextColor(Color.parseColor("#4CAF50"))
        }
    }

    private fun saveConfig() {
        val prev = apiClient.config
        apiClient.config = prev.copy(
            apiHost = apiHostInput.text.toString().trim().ifBlank { prev.apiHost },
            cookieD = cookieDInput.text.toString().trim(),
            cookieS = cookieSInput.text.toString().trim(),
            cookieT = cookieTInput.text.toString().trim(),
            streamUserKey = streamKeyInput.text.toString().trim(),
            playlistPath = playlistPathInput.text.toString().trim(),
            segmentPath = segmentPathInput.text.toString().trim(),
            userId = userIdInput.text.toString().trim(),
            userToken = userTokenInput.text.toString().trim(),
            email = emailInput.text.toString().trim(),
            password = passwordInput.text.toString()
        )
        statusText.text = "Settings saved"
        statusText.setTextColor(Color.parseColor("#4CAF50"))
    }

    private fun testSession() {
        saveConfig()
        statusText.text = "Testing…"
        statusText.setTextColor(Color.parseColor("#FFEB3B"))
        Thread {
            val result = apiClient.testSession()
            runOnUiThread {
                if (result.isSuccess) {
                    statusText.text = result.getOrNull()
                    statusText.setTextColor(Color.parseColor("#4CAF50"))
                } else {
                    statusText.text = "Failed: ${result.exceptionOrNull()?.message}"
                    statusText.setTextColor(Color.parseColor("#F44336"))
                }
            }
        }.start()
    }

    private fun tryLogin() {
        saveConfig()
        statusText.text = "Trying v8/login…"
        statusText.setTextColor(Color.parseColor("#FFEB3B"))
        Thread {
            val result = apiClient.loginV8()
            runOnUiThread {
                if (result.isSuccess) {
                    statusText.text = result.getOrNull()
                    statusText.setTextColor(Color.parseColor("#4CAF50"))
                    loadConfig()
                } else {
                    statusText.text = "Login failed: ${result.exceptionOrNull()?.message}"
                    statusText.setTextColor(Color.parseColor("#F44336"))
                }
            }
        }.start()
    }

    override fun onDestroy() {
        proxyServer?.stop()
        super.onDestroy()
    }

    private fun toggleProxy() {
        saveConfig()
        val proxy = proxyServer
        if (proxy != null && proxy.isRunning()) {
            proxy.stop()
            proxyServer = null
            updateProxyButton()
            statusText.text = "Proxy stopped"
            statusText.setTextColor(Color.parseColor("#FFEB3B"))
            return
        }

        val c = apiClient.config
        if (c.playlistPath.isBlank()) {
            statusText.text = "Set playlist opaque path first"
            statusText.setTextColor(Color.parseColor("#F44336"))
            return
        }
        if (!apiClient.isSessionReady()) {
            statusText.text = "Set cookies d/s/t first"
            statusText.setTextColor(Color.parseColor("#F44336"))
            return
        }

        statusText.text = "Starting proxy…"
        statusText.setTextColor(Color.parseColor("#FFEB3B"))

        val newProxy = M3uProxyServer(this)
        newProxy.onStatusChanged = { status ->
            runOnUiThread { statusText.text = status }
        }
        newProxy.onCookiesRefreshed = { d, s, t ->
            runOnUiThread {
                apiClient.config = apiClient.config.copy(cookieD = d, cookieS = s, cookieT = t)
                cookieDInput.setText(d)
                cookieSInput.setText(s)
                cookieTInput.setText(t)
            }
        }

        val host = c.apiHost.ifBlank { "23.94.64.155:30822" }
        val scheme = if (c.useHttps) "https" else "http"
        val playlistUrl = "$scheme://$host${c.playlistPath}"
        val segmentUrl = if (c.segmentPath.isNotBlank()) "$scheme://$host${c.segmentPath}" else ""

        newProxy.start(playlistUrl, segmentUrl, c.cookieD, c.cookieS, c.cookieT)
        proxyServer = newProxy

        Thread {
            Thread.sleep(800)
            runOnUiThread {
                updateProxyButton()
                if (newProxy.isRunning()) {
                    statusText.text = "Proxy running → ${newProxy.getProxyUrl()}"
                    statusText.setTextColor(Color.parseColor("#4CAF50"))
                } else {
                    statusText.text = "Proxy failed to start"
                    statusText.setTextColor(Color.parseColor("#F44336"))
                }
            }
        }.start()
    }

    private fun updateProxyButton() {
        val running = proxyServer?.isRunning() == true
        proxyBtn.text = if (running) "Stop Proxy" else "Start Proxy"
    }

    private fun fetchM3u8() {
        saveConfig()
        statusText.text = "Fetching M3U8…"
        statusText.setTextColor(Color.parseColor("#FFEB3B"))
        Thread {
            val result = apiClient.fetchRawM3u8()
            runOnUiThread {
                if (result.isSuccess) {
                    val raw = result.getOrNull() ?: ""
                    statusText.text = "M3U8 (${raw.length} chars):\n${raw.take(500)}"
                    statusText.setTextColor(Color.parseColor("#4CAF50"))
                } else {
                    statusText.text = "Fetch failed: ${result.exceptionOrNull()?.message}"
                    statusText.setTextColor(Color.parseColor("#F44336"))
                }
            }
        }.start()
    }

    private fun dp(value: Int): Int = TypedValue.applyDimension(
        TypedValue.COMPLEX_UNIT_DIP,
        value.toFloat(),
        resources.displayMetrics
    ).toInt()
}
