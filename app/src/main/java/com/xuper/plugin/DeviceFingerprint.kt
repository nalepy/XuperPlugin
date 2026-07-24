package com.xuper.plugin

import android.annotation.SuppressLint
import android.content.Context
import android.os.Build
import android.provider.Settings
import android.util.Base64
import java.io.BufferedReader
import java.io.File
import java.io.FileInputStream
import java.io.InputStreamReader
import java.util.Properties
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * Device fingerprint collection mirroring kb/f0 + r2/g from the Xuper/BrazilTV app.
 *
 * ## What the app collects (kb/f0)
 *
 * | Method | Source | Encrypted with |
 * |--------|--------|---------------|
 * | c()    | Settings.System ANDROID_ID | rd.c.d(value, "combrasiltv...") |
 * | d()    | j.j() → device serial/IMEI | rd.c.d(...) |
 * | e()    | /proc/cpuinfo "Serial" | rd.c.d(...) |
 * | a()    | /sys/class/sunxi_info/sys_info (Allwinner SoC) | rd.c.d(...) |
 * | b()    | /proc/cpu_chipid "Serial" | rd.c.d(...) |
 *
 * ## What r2/g manages (device token persistence)
 *
 * r2/g stores encrypted key-value pairs to /sdcard/.properties using the SAME
 * 3DES key (nb.b / XuperCrypto.DEFAULT_KEY). Keys include:
 *   key_device_id, key_sn_token, key_u, key_p, key_v, key_t, key_c
 *
 * ## Crypto chain
 *
 * kb/f0 encrypts raw device IDs with rd.c.d() → AES-ECB (key="combrasiltvaslgklxckbcombrasiltv")
 * r2/g encrypts stored values with nb.b.b() → 3DES-ECB (key=2b494e53...) = XuperCrypto
 *
 * ## Relevance to d=/s=/t= cookies
 *
 * The snToken endpoint (/api/portalCore/v3/snToken) accepts encrypted device fields
 * and returns sn/snToken/userId. These feed into the user session that ultimately
 * produces the d=/s=/t= cookies. The exact assembly point for d/s/t is still being
 * traced through the packer's native interceptor layer.
 */
object DeviceFingerprint {

    /** kb/f0 hardcoded AES key for device ID encryption (rd.c uses AES, not 3DES). */
    private const val F0_AES_KEY = "combrasiltvaslgklxckbcombrasiltv"

    /** r2/g hardcoded fallback ANDROID_ID hex blob when Settings.System is empty. */
    private const val FALLBACK_ANDROID_ID_HEX = "4b4d354a69546a7636736d2f73776a2b705834316d3874536576774470327448"

    data class Snapshot(
        val androidId: String = "",
        val deviceSerial: String = "",
        val cpuSerial: String = "",
        val sunxiSerial: String = "",
        val chipIdSerial: String = "",
        val buildModel: String = Build.MODEL,
        val buildBrand: String = Build.BRAND,
        val buildManufacturer: String = Build.MANUFACTURER,
        val sdkInt: Int = Build.VERSION.SDK_INT,
        val storedDeviceId: String = "",
        val storedSnToken: String = "",
        val storedU: String = "",
        val storedP: String = "",
        val storedV: String = "",
        val storedT: String = "",
        val storedC: String = ""
    )

    /**
     * Collect all device identifiers the app gathers.
     * Mirrors kb/f0 + r2/g collection patterns.
     */
    @SuppressLint("HardwareIds")
    fun collect(context: Context): Snapshot {
        val appCtx = context.applicationContext

        // kb/f0.c() — ANDROID_ID
        val androidId = try {
            val id = Settings.System.getString(appCtx.contentResolver, Settings.Secure.ANDROID_ID)
                ?: Settings.Secure.getString(appCtx.contentResolver, Settings.Secure.ANDROID_ID)
            if (id.isNullOrBlank()) {
                // fallback: decrypt the hardcoded hex blob with F0 AES key
                f0Decrypt(FALLBACK_ANDROID_ID_HEX)
            } else {
                id
            }
        } catch (_: Exception) { "" }

        // kb/f0.e() → /proc/cpuinfo "Serial" → fallback a() → fallback b()
        val cpuSerial = readProcCpuinfoSerial()

        // kb/f0.a() → /sys/class/sunxi_info/sys_info "sunxi_serial"
        val sunxiSerial = readSunxiSerial()

        // kb/f0.b() → /proc/cpu_chipid "Serial"
        val chipIdSerial = readCpuChipId()

        // kb/f0.d() → j.j() device serial / IMEI variant
        val deviceSerial = try {
            @Suppress("DEPRECATION")
            Build.getSerial() ?: Build.SERIAL ?: ""
        } catch (_: Exception) { Build.SERIAL ?: "" }

        // r2/g — read encrypted properties from /sdcard/.properties
        val props = readStoredProperties()

        return Snapshot(
            androidId = androidId,
            deviceSerial = deviceSerial,
            cpuSerial = cpuSerial,
            sunxiSerial = sunxiSerial,
            chipIdSerial = chipIdSerial,
            storedDeviceId = props["key_device_id"] ?: "",
            storedSnToken = props["key_sn_token"] ?: "",
            storedU = props["key_u"] ?: "",
            storedP = props["key_p"] ?: "",
            storedV = props["key_v"] ?: "",
            storedT = props["key_t"] ?: "",
            storedC = props["key_c"] ?: ""
        )
    }

    /**
     * Build the field map for /api/portalCore/v3/snToken POST body.
     * The app sends encrypted device identifiers. Here we send them raw
     * since our XuperApiClient.postJson handles needEncrypt wrapping.
     *
     * Real app sends (from packet capture analysis):
     *   deviceId, deviceModel, deviceBrand, cpuId, androidId, ...
     * encrypted per-field with the 3DES scheme.
     */
    fun snTokenFields(context: Context): Map<String, String> {
        val snap = collect(context)

        // Encrypt each field the way the app does — via XuperCrypto (3DES-ECB)
        // so the server receives the expected ciphertext format.
        fun enc(v: String): String = if (v.isBlank()) "" else XuperCrypto.encryptBody(v)

        return mapOf(
            "deviceId" to enc(snap.androidId),
            "deviceModel" to enc(snap.buildModel),
            "deviceBrand" to enc(snap.buildBrand),
            "androidId" to enc(snap.androidId),
            "cpuId" to enc(
                listOf(snap.cpuSerial, snap.sunxiSerial, snap.chipIdSerial)
                    .firstOrNull { it.isNotBlank() } ?: snap.deviceSerial
            ),
            "deviceSerial" to enc(snap.deviceSerial),
            "sdkInt" to snap.sdkInt.toString()
        )
    }

    // ── kb/f0 device ID readers ──

    /** kb/f0.e() — read "Serial" from /proc/cpuinfo, fallback to a(), then b(). */
    private fun readProcCpuinfoSerial(): String {
        val serial = readProcFileLine("/proc/cpuinfo", "Serial", 6)
        if (serial.isNotBlank()) return serial
        val sunxi = readSunxiSerial()
        if (sunxi.isNotBlank()) return sunxi
        return readCpuChipId()
    }

    /** kb/f0.a() — read "sunxi_serial" from /sys/class/sunxi_info/sys_info. */
    private fun readSunxiSerial(): String {
        return readProcFileLine("/sys/class/sunxi_info/sys_info", "sunxi_serial", 12)
    }

    /** kb/f0.b() — read "Serial" from /proc/cpu_chipid. */
    private fun readCpuChipId(): String {
        return readProcFileLine("/proc/cpu_chipid", "Serial", 6)
    }

    private fun readProcFileLine(path: String, key: String, keyLen: Int): String {
        return try {
            val file = File(path)
            if (!file.exists()) return ""
            BufferedReader(InputStreamReader(FileInputStream(file))).use { reader ->
                var line: String?
                while (reader.readLine().also { line = it } != null) {
                    val l = line ?: continue
                    if (l.contains(key)) {
                        val idx = l.indexOf(key) + keyLen
                        if (idx < l.length) {
                            return l.substring(idx)
                                .replace(":", "")
                                .replace(" ", "")
                                .trim()
                        }
                    }
                }
            }
            ""
        } catch (_: Exception) { "" }
    }

    // ── r2/g stored properties reader ──

    private fun readStoredProperties(): Map<String, String> {
        val paths = listOf(
            "/sdcard/.properties",
            "/sdcard/Android/.config",
            "/sdcard/.config"
        )
        val result = mutableMapOf<String, String>()
        for (path in paths) {
            try {
                val file = File(path)
                if (!file.exists()) continue
                val props = Properties()
                FileInputStream(file).use { props.load(it) }
                for (key in props.stringPropertyNames()) {
                    val encrypted = props.getProperty(key) ?: continue
                    // Try 3DES decrypt (same key XuperCrypto uses)
                    val decrypted = XuperCrypto.decryptBody(encrypted)
                    if (!decrypted.isNullOrBlank()) {
                        result[key] = decrypted
                    }
                }
            } catch (_: Exception) { }
        }
        return result
    }

    // ── kb/f0 rd.c.d() — AES-ECB decrypt (NOT 3DES) ──

    /**
     * rd.c.d(hexCiphertext, passphrase) — AES-ECB decrypt used by kb/f0
     * for device ID protection. Different from the 3DES used by nb.b!
     *
     * Key derivation: passphrase → first 16 bytes → AES-128-ECB.
     * Ciphertext is hex-encoded.
     */
    private fun f0Decrypt(hexCiphertext: String): String {
        return try {
            val keyBytes = F0_AES_KEY.toByteArray(Charsets.UTF_8).copyOf(16)
            val ciphertext = hexToBytes(hexCiphertext)
            val cipher = Cipher.getInstance("AES/ECB/PKCS5Padding")
            cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(keyBytes, "AES"))
            String(cipher.doFinal(ciphertext), Charsets.UTF_8)
        } catch (_: Exception) { "" }
    }

    private fun hexToBytes(hex: String): ByteArray {
        val len = hex.length
        val data = ByteArray(len / 2)
        var i = 0
        while (i < len) {
            data[i / 2] = ((hexVal(hex[i]) shl 4) or hexVal(hex[i + 1])).toByte()
            i += 2
        }
        return data
    }

    private fun hexVal(c: Char): Int = when (c) {
        in '0'..'9' -> c - '0'
        in 'a'..'f' -> c - 'a' + 10
        in 'A'..'F' -> c - 'A' + 10
        else -> 0
    }
}
