package de.davebook.app

import android.util.Base64
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

/**
 * Alles, was über ntfy.sh läuft, ist mit AES-256-GCM verschlüsselt.
 * Der Schlüssel kommt aus dem gemeinsamen Geheimnis, das nur eure beiden Handys kennen.
 */
object Crypto {
    private val random = SecureRandom()

    fun sha256(s: String): ByteArray =
        MessageDigest.getInstance("SHA-256").digest(s.toByteArray(Charsets.UTF_8))

    fun hex(bytes: ByteArray): String {
        val sb = StringBuilder(bytes.size * 2)
        for (b in bytes) {
            val v = b.toInt() and 0xFF
            sb.append("0123456789abcdef"[v shr 4])
            sb.append("0123456789abcdef"[v and 0x0F])
        }
        return sb.toString()
    }

    fun newSecret(): String {
        val b = ByteArray(32)
        random.nextBytes(b)
        return Base64.encodeToString(b, Base64.NO_WRAP)
    }

    fun newPairCode(): String = (100000 + random.nextInt(900000)).toString()

    fun mainKey(secret: String): SecretKeySpec = SecretKeySpec(sha256("davebook-key:$secret"), "AES")

    fun pairKey(code: String): SecretKeySpec {
        val spec = PBEKeySpec(code.toCharArray(), "davebook-pair-v1".toByteArray(Charsets.UTF_8), 10000, 256)
        val raw = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded
        return SecretKeySpec(raw, "AES")
    }

    fun encrypt(key: SecretKeySpec, plain: String): String {
        val iv = ByteArray(12)
        random.nextBytes(iv)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key, GCMParameterSpec(128, iv))
        val ct = cipher.doFinal(plain.toByteArray(Charsets.UTF_8))
        return Base64.encodeToString(iv + ct, Base64.NO_WRAP)
    }

    fun decrypt(key: SecretKeySpec, data: String): String? {
        return try {
            val all = Base64.decode(data.trim(), Base64.NO_WRAP)
            if (all.size < 29) return null
            val iv = all.copyOfRange(0, 12)
            val ct = all.copyOfRange(12, all.size)
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(128, iv))
            String(cipher.doFinal(ct), Charsets.UTF_8)
        } catch (e: Exception) {
            null
        }
    }
}
