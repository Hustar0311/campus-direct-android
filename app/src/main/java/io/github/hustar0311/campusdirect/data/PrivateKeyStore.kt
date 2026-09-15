package io.github.hustar0311.campusdirect.data

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

class PrivateKeyStore(private val context: Context) {
    private val keyFile = context.filesDir.resolve("ssh_private_key.enc")
    private val alias = "campus_direct_private_key_encryption"

    fun exists(): Boolean = keyFile.isFile

    fun import(privateKey: ByteArray) {
        require(privateKey.isNotEmpty()) { "私钥文件为空" }
        require(privateKey.size <= MAX_KEY_BYTES) { "私钥文件过大" }
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, getOrCreateKey())
        val encrypted = cipher.doFinal(privateKey)
        val payload = byteArrayOf(cipher.iv.size.toByte()) + cipher.iv + encrypted
        keyFile.writeBytes(payload)
        privateKey.fill(0)
    }

    fun read(): ByteArray {
        val payload = keyFile.readBytes()
        require(payload.isNotEmpty()) { "私钥存储已损坏" }
        val ivLength = payload[0].toInt() and 0xff
        require(ivLength in 12..16 && payload.size > ivLength + 1) { "私钥存储已损坏" }
        val iv = payload.copyOfRange(1, 1 + ivLength)
        val encrypted = payload.copyOfRange(1 + ivLength, payload.size)
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, getOrCreateKey(), GCMParameterSpec(128, iv))
        return cipher.doFinal(encrypted)
    }

    fun delete() {
        if (keyFile.exists()) keyFile.delete()
    }

    private fun getOrCreateKey(): SecretKey {
        val keyStore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (keyStore.getKey(alias, null) as? SecretKey)?.let { return it }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        generator.init(
            KeyGenParameterSpec.Builder(
                alias,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build(),
        )
        return generator.generateKey()
    }

    private companion object {
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val MAX_KEY_BYTES = 64 * 1024
    }
}
