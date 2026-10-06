package online.assadawut.atom.security

import android.util.Base64
import com.google.gson.Gson
import online.assadawut.atom.database.MemoryRecord
import online.assadawut.atom.database.ProfileRecord
import java.security.MessageDigest
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

data class BackupPackage(
    val profile: ProfileRecord?,
    val memories: List<MemoryRecord>,
    val timestamp: Long = System.currentTimeMillis()
)

class EncryptedBackupManager {

    private val gson = Gson()

    private fun deriveKey(secret: String): SecretKeySpec {
        val digest = MessageDigest.getInstance("SHA-256")
        val bytes = digest.digest(secret.toByteArray(Charsets.UTF_8))
        return SecretKeySpec(bytes, "AES")
    }

    fun createEncryptedBackup(backupData: BackupPackage, passphrase: String): String {
        val json = gson.toJson(backupData)
        val key = deriveKey(passphrase)

        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key)
        val iv = cipher.iv
        val encryptedBytes = cipher.doFinal(json.toByteArray(Charsets.UTF_8))

        val combined = ByteArray(iv.size + encryptedBytes.size)
        System.arraycopy(iv, 0, combined, 0, iv.size)
        System.arraycopy(encryptedBytes, 0, combined, iv.size, encryptedBytes.size)

        return Base64.encodeToString(combined, Base64.NO_WRAP)
    }

    fun restoreEncryptedBackup(encryptedBase64: String, passphrase: String): Result<BackupPackage> = runCatching {
        val combined = Base64.decode(encryptedBase64, Base64.NO_WRAP)
        val ivSize = 12 // Standard GCM IV size
        val iv = ByteArray(ivSize)
        val encryptedBytes = ByteArray(combined.size - ivSize)

        System.arraycopy(combined, 0, iv, 0, ivSize)
        System.arraycopy(combined, ivSize, encryptedBytes, 0, encryptedBytes.size)

        val key = deriveKey(passphrase)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        val spec = GCMParameterSpec(128, iv)
        cipher.init(Cipher.DECRYPT_MODE, key, spec)

        val decryptedBytes = cipher.doFinal(encryptedBytes)
        val json = String(decryptedBytes, Charsets.UTF_8)

        gson.fromJson(json, BackupPackage::class.java)
    }
}
