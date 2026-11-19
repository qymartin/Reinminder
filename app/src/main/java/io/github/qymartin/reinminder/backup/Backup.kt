package io.github.qymartin.reinminder.backup

import io.github.qymartin.reinminder.data.Codec
import io.github.qymartin.reinminder.data.Event
import org.json.JSONObject
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

class WrongPasswordException : Exception()
class BadBackupException : Exception()

/**
 * Backup file: the events as JSON, optionally encrypted with a password.
 * Encrypted layout: "RMBK1" | salt (16) | iv (12) | AES-256-GCM ciphertext; key = PBKDF2-HMAC-SHA256.
 */
object Backup {
    private val MAGIC = "RMBK1".toByteArray(Charsets.US_ASCII)
    private const val ITERATIONS = 120_000

    fun toJson(events: List<Event>, exportedAt: Long): String = JSONObject().apply {
        put("app", "reinminder")
        put("format", 1)
        put("exportedAt", exportedAt)
        put("events", Codec.listToJson(events))
    }.toString()

    fun fromJson(json: String): List<Event> = try {
        val o = JSONObject(json)
        if (o.optString("app") != "reinminder") throw BadBackupException()
        Codec.listFromJson(o.getJSONArray("events"))
    } catch (e: BadBackupException) {
        throw e
    } catch (e: Exception) {
        throw BadBackupException()
    }

    fun isEncrypted(bytes: ByteArray): Boolean =
        bytes.size > MAGIC.size && bytes.copyOfRange(0, MAGIC.size).contentEquals(MAGIC)

    fun encrypt(plain: ByteArray, password: CharArray): ByteArray {
        val rnd = SecureRandom()
        val salt = ByteArray(16).also { rnd.nextBytes(it) }
        val iv = ByteArray(12).also { rnd.nextBytes(it) }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key(password, salt), GCMParameterSpec(128, iv))
        return MAGIC + salt + iv + cipher.doFinal(plain)
    }

    fun decrypt(bytes: ByteArray, password: CharArray): ByteArray {
        if (!isEncrypted(bytes) || bytes.size < MAGIC.size + 28 + 16) throw BadBackupException()
        var p = MAGIC.size
        val salt = bytes.copyOfRange(p, p + 16).also { p += 16 }
        val iv = bytes.copyOfRange(p, p + 12).also { p += 12 }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, key(password, salt), GCMParameterSpec(128, iv))
        return try {
            cipher.doFinal(bytes, p, bytes.size - p)
        } catch (e: Exception) {
            throw WrongPasswordException()
        }
    }

    private fun key(password: CharArray, salt: ByteArray): SecretKeySpec {
        val spec = PBEKeySpec(password, salt, ITERATIONS, 256)
        val raw = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded
        spec.clearPassword()
        return SecretKeySpec(raw, "AES")
    }
}
