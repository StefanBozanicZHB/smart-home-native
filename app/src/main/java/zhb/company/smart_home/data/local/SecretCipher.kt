package zhb.company.smart_home.data.local

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.security.KeyStore
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/** Encrypts small secrets (the sign-in session) before they are written to disk. */
interface SecretCipher {
    fun encrypt(plain: String): String

    /** Null when [sealed] cannot be decrypted (other key, e.g. restored from another phone). */
    fun decrypt(sealed: String): String?
}

/**
 * AES-GCM with a key generated in the Android Keystore. The key never leaves this phone and is not
 * part of backups, so a copied preferences file is useless elsewhere. The key does not require the
 * user to authenticate: the widget and notification feedback refresh the session in the background.
 */
class KeystoreCipher(private val alias: String = "smart_home_session") : SecretCipher {

    override fun encrypt(plain: String): String {
        val cipher = Cipher.getInstance(TRANSFORMATION).apply { init(Cipher.ENCRYPT_MODE, key()) }
        val sealed = cipher.doFinal(plain.toByteArray(Charsets.UTF_8))
        return base64(cipher.iv) + SEPARATOR + base64(sealed)
    }

    override fun decrypt(sealed: String): String? = runCatching {
        val (iv, data) = sealed.split(SEPARATOR).also { require(it.size == 2) }.map { Base64.getDecoder().decode(it) }
        val key = existingKey() ?: return null
        val cipher = Cipher.getInstance(TRANSFORMATION).apply { init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(TAG_BITS, iv)) }
        String(cipher.doFinal(data), Charsets.UTF_8)
    }.getOrNull()

    private fun keyStore() = KeyStore.getInstance(KEYSTORE).apply { load(null) }

    private fun existingKey(): SecretKey? = keyStore().getKey(alias, null) as? SecretKey

    private fun key(): SecretKey = existingKey() ?: KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, KEYSTORE).run {
        init(
            KeyGenParameterSpec.Builder(alias, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build()
        )
        generateKey()
    }

    private fun base64(bytes: ByteArray) = Base64.getEncoder().encodeToString(bytes)

    private companion object {
        const val KEYSTORE = "AndroidKeyStore"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val TAG_BITS = 128
        const val SEPARATOR = ":"
    }
}
