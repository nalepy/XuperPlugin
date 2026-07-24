package com.xuper.plugin

import android.util.Base64
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.DESedeKeySpec

/**
 * Reimplementation of the Xuper/BrazilTV app's request-body crypto, recovered
 * from decompiled classes nb.b / rd.c / nb.a / lb.a / lb.b (jadx of app DEX).
 *
 * The app's Retrofit endpoints marked WITHOUT "needEncrypt:false" have their
 * JSON body encrypted by an OkHttp interceptor before send, and their response
 * decrypted after receive. The exact scheme (verified byte-for-byte against
 * nb.b and a Python round-trip):
 *
 *   key      = Base64.decode(keyString)                 // 24 bytes -> 3DES
 *   cipher   = DESede/ECB/PKCS5Padding
 *   ENCRYPT  = toHex( Base64.encode( 3DES_enc(plaintextUtf8) ) )
 *   DECRYPT  = 3DES_dec( Base64.decode( fromHex(wire) ) ) as UTF-8
 *
 * where toHex/fromHex are nb.a.c / nb.a.b (per-char hex of the ASCII base64
 * string), and Base64 is lb.b / lb.a (standard alphabet).
 *
 * Default key is nb.b.a = "2b494e53756c664c2f44465245733572", which is a
 * BASE64 string (NOT hex — the previous version of this file hex-decoded it to
 * 16 bytes and produced plain base64 output; both were wrong). Base64-decoded
 * it is the 24-byte 3DES key d9be3de1ee77ef9e9cebae1cd9fe38e3ae76e39ef7df9ef6.
 */
object XuperCrypto {

    /** nb.b.a — default key, base64 form. Base64-decodes to the 24-byte 3DES key. */
    const val DEFAULT_KEY = "2b494e53756c664c2f44465245733572"

    private fun keyBytes(keyString: String): ByteArray =
        Base64.decode(keyString, Base64.NO_WRAP)

    private fun cipher(mode: Int, keyString: String): Cipher {
        val spec = DESedeKeySpec(keyBytes(keyString))
        val secret = SecretKeyFactory.getInstance("DESede").generateSecret(spec)
        return Cipher.getInstance("DESede/ECB/PKCS5Padding").apply { init(mode, secret) }
    }

    /** Encrypt a request body. Mirrors nb.b.b(plain, key). */
    fun encryptBody(plaintext: String, keyString: String = DEFAULT_KEY): String {
        val ct = cipher(Cipher.ENCRYPT_MODE, keyString).doFinal(plaintext.toByteArray(Charsets.UTF_8))
        val b64 = Base64.encodeToString(ct, Base64.NO_WRAP).replace("\r", "").replace("\n", "")
        return toHex(b64)
    }

    /** Decrypt a response/body. Mirrors nb.b.a(wire, key). Returns null on failure. */
    fun decryptBody(wire: String, keyString: String = DEFAULT_KEY): String? = try {
        val b64 = fromHex(wire)
        val ct = Base64.decode(b64, Base64.NO_WRAP)
        String(cipher(Cipher.DECRYPT_MODE, keyString).doFinal(ct), Charsets.UTF_8)
    } catch (_: Exception) {
        null
    }

    /** nb.a.c — per-char hex of an ASCII string (base64 chars are all single-byte). */
    private fun toHex(s: String): String {
        val sb = StringBuilder(s.length * 2)
        for (ch in s) {
            val h = Integer.toHexString(ch.code)
            if (h.length == 1) sb.append('0')
            sb.append(h)
        }
        return sb.toString()
    }

    /** nb.a.b — hex string -> ASCII string (inverse of toHex). */
    private fun fromHex(hex: String): String {
        require(hex.length % 2 == 0) { "hex length must be even" }
        val bytes = ByteArray(hex.length / 2)
        var i = 0
        while (i < hex.length) {
            bytes[i / 2] = ((hexVal(hex[i]) shl 4) or hexVal(hex[i + 1])).toByte()
            i += 2
        }
        return String(bytes, Charsets.UTF_8)
    }

    private fun hexVal(c: Char): Int = when (c) {
        in '0'..'9' -> c - '0'
        in 'a'..'f' -> c - 'a' + 10
        in 'A'..'F' -> c - 'A' + 10
        else -> throw IllegalArgumentException("Invalid hex char: $c")
    }
}
