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
import kotlinx.serialization.json.JsonObjectBuilder
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
    val cookieD: String = "ca0e53edac957b8f6f187528933355f1",
    val cookieS: String = "QDtRcPPKDAwtROdnoGlxRgXpj64ElYpBBNH0TIZO20TIcc",
    val cookieT: String = "kzDQKAgQI3UlOy-bl3ScQrOcu3NIHFGAY5PZ6xuoZ3z",
    val userId: String = "169355704",
    val userToken: String = "42eebacb-1a56-46d4-8f8e-94ba32e5b99d",
    // portalCode is the literal string "masnew" (captured from getAuthInfo request),
    // NOT the old hex 6e54356f76774c54574b303d (that was a different/derived value).
    val portalCode: String = "masnew",
    val streamUserKey: String = "cyx_93531158996778016",
    val cdnMain: String = "magloud.y6oseldsc.online",
    val cdnBackup: String = "caeo.wvdbozpfc.com",
    val email: String = "nestor.ale@gmail.com",
    val password: String = "Ian20jesus",
    val playlistPath: String = "",
    val segmentPath: String = "",
    // --- getAuthInfo/getLiveData request envelope (captured device fields, V76PRO) ---
    val appId: String = "com.android.msandroid",
    val apkVersion: String = "43405",
    val appLanguage: String = "es",
    val model: String = "V76PRO",
    val product: String = "walley",
    val cpu: String = "armeabi-v7a",
    val hardwareInfo: String = "sun50iw9p1",
    val sysVersion: String = "2024-11-15 19:08:51_29_14.1_4.9.170",
    val sdkVer: Int = 29,
    val loginType: String = "2",
    val sn: String = "ca0e53edac957b8f6f187528933355f1",
    // b29 / reserve1 = captured base64-in-hex device blobs. Reused as-is with a saved
    // session; regeneration (if the server rotates them) is a later concern.
    val b29: String = "4f6f786b4b5a7a3933666842554e6c55717338584b71325a3635436b4e463736583442714b345572434a504c556e72384136647252773d3d",
    val reserve1: String = "76356c476568424f4a38334761645a697957757344673d3d",
    // --- filled at runtime by getSlbInfo / getAuthInfo ---
    val portalHost: String = "",
    val sessionId: String = "",
    val authId: String = ""
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

    private val appContext = context.applicationContext

    private val prefs: SharedPreferences =
        context.getSharedPreferences("xuper_config", Context.MODE_PRIVATE)

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        coerceInputValues = true
    }

    /** Captures Set-Cookie d/s/t if the server ever rotates them. */
    private val cookieJar = object : okhttp3.CookieJar {
        private val store = mutableListOf<okhttp3.Cookie>()
        override fun saveFromResponse(url: okhttp3.HttpUrl, cookies: List<okhttp3.Cookie>) {
            synchronized(store) {
                store.removeAll { c -> cookies.any { it.name == c.name && it.matches(url) } }
                store.addAll(cookies)
            }
            var d = config.cookieD
            var s = config.cookieS
            var t = config.cookieT
            var changed = false
            for (c in cookies) {
                when (c.name) {
                    "d" -> if (c.value.isNotBlank() && c.value != d) { d = c.value; changed = true }
                    "s" -> if (c.value.isNotBlank() && c.value != s) { s = c.value; changed = true }
                    "t" -> if (c.value.isNotBlank() && c.value != t) { t = c.value; changed = true }
                }
            }
            if (changed) {
                config = config.copy(cookieD = d, cookieS = s, cookieT = t)
            }
        }
        override fun loadForRequest(url: okhttp3.HttpUrl): List<okhttp3.Cookie> {
            synchronized(store) {
                return store.filter { it.matches(url) }
            }
        }
    }

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(45, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .followRedirects(true)
        .cookieJar(cookieJar)
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
        return c.cookieD.isNotBlank() && c.cookieS.isNotBlank() && c.cookieT.isNotBlank()
    }

    /** Returns which cookies are missing for diagnostics. */
    fun missingCookies(): List<String> {
        val c = config
        return listOfNotNull(
            if (c.cookieD.isBlank()) "d" else null,
            if (c.cookieS.isBlank()) "s" else null,
            if (c.cookieT.isBlank()) "t" else null
        )
    }

    /** Quick connectivity check — just hit root with d cookie to see if routing works. */
    fun checkRoute(): Result<String> {
        val c = config
        if (c.cookieD.isBlank()) return Result.failure(IOException("Set cookie d first (any non-empty value for route test)"))
        val host = c.apiHost.ifBlank { "23.94.64.155:30822" }
        val (code, body) = get(host, "/")
        return when {
            code == 404 -> Result.failure(IOException("Route blocked (404) — server requires d cookie for path resolution. Your d value may be invalid."))
            code in 200..399 -> Result.success("Route OK (HTTP $code) — d cookie resolves, server responding")
            code == 400 -> Result.failure(IOException("Route resolves (HTTP 400) but server rejects session. d/s/t cookies likely expired — recapture via MITM."))
            code in 401..409 -> Result.failure(IOException("Auth rejected (HTTP $code) — s/t cookies expired. Recapture via MITM."))
            code > 0 -> Result.failure(IOException("Server responded HTTP $code: ${body?.take(100)}"))
            else -> Result.failure(IOException("Cannot reach $host: ${body ?: "no response"}"))
        }
    }

    fun cookieHeader(): String {
        val c = config
        return "d=${c.cookieD}; s=${c.cookieS}; t=${c.cookieT}"
    }

    /** kb.f0 device snapshot (for snToken / diagnostics). */
    fun deviceFingerprint(): DeviceFingerprint.Snapshot =
        DeviceFingerprint.collect(appContext)

    /**
     * POST /api/portalCore/v3/snToken with encrypted device fields (kb.f0 → SnTokenBean).
     * Returns server sn/snToken/userId when the host accepts plain (or needEncrypt) bodies.
     * Does NOT produce streaming cookies d/s/t — those come from packer-extracted interceptors.
     */
    fun requestSnToken(): Result<String> {
        val fields = DeviceFingerprint.snTokenFields(appContext)
        val body = buildJsonObject {
            for ((k, v) in fields) {
                if (v.isNotBlank()) put(k, v)
            }
        }.toString()
        val host = config.apiHost.ifBlank { "23.94.64.155:30822" }
        val (code, resp) = postJson(host, "/api/portalCore/v3/snToken", body, encrypt = true)
        if (code <= 0) return Result.failure(IOException("network: $resp"))
        if (resp.isNullOrBlank()) return Result.failure(IOException("HTTP $code empty body"))
        return try {
            val obj = json.parseToJsonElement(resp).jsonObject
            val returnCode = obj["returnCode"]?.jsonPrimitive?.contentOrNull
            val data = obj["data"]?.jsonObject
            val sn = data?.get("sn")?.jsonPrimitive?.contentOrNull.orEmpty()
            val snToken = data?.get("snToken")?.jsonPrimitive?.contentOrNull.orEmpty()
            val userId = data?.get("userId")?.jsonPrimitive?.contentOrNull.orEmpty()
            if (userId.isNotBlank()) {
                config = config.copy(userId = userId)
            }
            Result.success("HTTP $code returnCode=$returnCode sn=$sn userId=$userId snToken=${snToken.take(16)}…")
        } catch (e: Exception) {
            Result.success("HTTP $code raw=${resp.take(200)}")
        }
    }

    fun applyCookies(d: String, s: String, t: String) {
        config = config.copy(
            cookieD = d.trim(),
            cookieS = s.trim(),
            cookieT = t.trim()
        )
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
        android.util.Log.i("XuperPlugin", "GET $url")
        return try {
            client.newCall(req).execute().use { resp ->
                val body = resp.body?.string()
                android.util.Log.i("XuperPlugin", "HTTP ${resp.code} body=${body?.take(200)}")
                resp.code to body
            }
        } catch (e: IOException) {
            android.util.Log.e("XuperPlugin", "GET failed: ${e.message}")
            -1 to e.message
        }
    }

    fun testSession(): Result<String> {
        val c = config
        val host = c.apiHost.ifBlank { "23.94.64.155:30822" }

        // First: quick route check
        if (c.cookieD.isNotBlank()) {
            val routeResult = checkRoute()
            android.util.Log.i("XuperPlugin", "Route check: ${routeResult.getOrNull() ?: routeResult.exceptionOrNull()?.message}")
            if (routeResult.isFailure) return routeResult
        }

        if (!isSessionReady()) {
            val missing = missingCookies()
            android.util.Log.e("XuperPlugin", "Session NOT ready — missing cookies: $missing")
            return Result.failure(IOException("Missing cookies: ${missing.joinToString(", ")}. Capture all three (d/s/t) via MITM."))
        }
        android.util.Log.i("XuperPlugin", "Session ready, testing...")

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
        android.util.Log.i("XuperPlugin", "Trying legacy GET / ...")
        val (code2, body2) = get(host, "/")
        android.util.Log.i("XuperPlugin", "Legacy GET / = $code2 body=${body2?.take(100)}")

        // If 401/409, try v8/login with email/password
        if (code2 == 401 || code2 == 409) {
            android.util.Log.i("XuperPlugin", "Cookies rejected (401/409), trying v8/login...")
            val loginResult = loginV8()
            android.util.Log.i("XuperPlugin", "Login result: ${loginResult.getOrNull() ?: loginResult.exceptionOrNull()?.message}")
            if (loginResult.isSuccess) {
                // Retry with fresh token
                val (code3, body3) = get(host, "/")
                android.util.Log.i("XuperPlugin", "Retry after login: $code3 body=${body3?.take(100)}")
                return if (code3 in 200..399) {
                    Result.success("Login OK! HTTP $code3 body=${body3?.take(200)}")
                } else {
                    Result.failure(IOException("Login succeeded but GET / still $code3"))
                }
            }
            return Result.failure(IOException("Login failed: ${loginResult.exceptionOrNull()?.message}"))
        }

        return if (code2 > 0) {
            Result.failure(IOException("Connected ($host) but path rejected. code=$code2"))
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
            android.util.Log.e("XuperPlugin", "loginV8: email/password empty")
            return Result.failure(IOException("email/password empty"))
        }
        android.util.Log.i("XuperPlugin", "loginV8: trying ${c.email}...")
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

    /**
     * Common portalCore request envelope — the exact device-field set captured from the
     * live getAuthInfo request (heap_live.bin). `extra` merges call-specific fields on top.
     */
    private fun envelope(extra: JsonObjectBuilder.() -> Unit = {}): String {
        val c = config
        return buildJsonObject {
            put("apkVersion", c.apkVersion)
            put("appId", c.appId)
            put("appLanguage", c.appLanguage)
            put("b29", c.b29)
            put("contentType", "application/json;charset=utf-8")
            put("cpu", c.cpu)
            put("deviceToken", "")
            put("hardwareInfo", c.hardwareInfo)
            put("loginType", c.loginType)
            put("model", c.model)
            put("portalCode", c.portalCode)
            put("product", c.product)
            put("reserve1", c.reserve1)
            put("sdkVer", c.sdkVer)
            put("sn", c.sn)
            put("sysVersion", c.sysVersion)
            put("lang", c.appLanguage)
            put("type", "1")
            put("userId", c.userId)
            put("userToken", c.userToken)
            extra()
        }.toString()
    }

    private fun portalHost(): String = config.portalHost.ifBlank { config.apiHost }

    /**
     * POST /api/portalCore/v15/getSlbInfo — resolves the serving portalCore host.
     * Call first (bootstrap host = decrypted domain|DES config or a known rotating host).
     */
    fun getSlbInfo(): Result<String> {
        val (code, resp) = postJson(portalHost(), "/api/portalCore/v15/getSlbInfo", envelope())
        if (code <= 0) return Result.failure(IOException("network: $resp"))
        if (resp.isNullOrBlank()) return Result.failure(IOException("HTTP $code empty (encrypt/path?)"))
        return try {
            val obj = json.parseToJsonElement(resp).jsonObject
            val returnCode = obj["returnCode"]?.jsonPrimitive?.contentOrNull
            val data = obj["data"]?.jsonObject
            val host = data?.str("host") ?: data?.str("mainAddr") ?: data?.str("slbHost") ?: ""
            if (host.isNotBlank()) config = config.copy(portalHost = host)
            Result.success("HTTP $code returnCode=$returnCode host=$host raw=${resp.take(160)}")
        } catch (e: Exception) {
            Result.success("HTTP $code raw=${resp.take(200)}")
        }
    }

    /**
     * POST /api/portalCore/v9/getAuthInfo — bootstraps session_id + auth_id from the
     * logged-in userToken. Body = the captured device envelope (3DES-wrapped by postJson).
     */
    fun getAuthInfo(): Result<String> {
        val c = config
        val (code, resp) = postJson(portalHost(), "/api/portalCore/v9/getAuthInfo", envelope())
        if (code <= 0) return Result.failure(IOException("network: $resp"))
        if (resp.isNullOrBlank()) return Result.failure(IOException("HTTP $code empty (encrypt/path?)"))
        return try {
            val obj = json.parseToJsonElement(resp).jsonObject
            val returnCode = obj["returnCode"]?.jsonPrimitive?.contentOrNull
            val data = obj["data"]?.jsonObject
            val sessionId = data?.str("session_id") ?: data?.str("sessionId") ?: ""
            val authId = data?.str("auth_id") ?: data?.str("authId")
                ?: "${c.userId}_${c.appId}__0"
            config = config.copy(sessionId = sessionId, authId = authId)
            Result.success("HTTP $code returnCode=$returnCode session_id=$sessionId auth_id=$authId")
        } catch (e: Exception) {
            Result.success("HTTP $code raw=${resp.take(200)}")
        }
    }

    fun getLiveData(): Result<List<XuperChannel>> {
        val c = config
        // Full envelope + live-specific fields (channelID/columnId/liveType per captured beans).
        val body = envelope {
            put("liveType", "1")
        }

        val (code, resp) = postJson(portalHost(), "/api/portalCore/v6/getLiveData", body)
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
