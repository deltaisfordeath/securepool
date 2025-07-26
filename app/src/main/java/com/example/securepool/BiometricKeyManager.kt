package com.example.securepool

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import android.util.Log
import com.example.securepool.api.RetrofitClient
import com.example.securepool.model.BiometricRegisterRequest
import java.io.IOException
import java.security.*
import java.security.cert.CertificateException
import java.security.spec.ECGenParameterSpec

class BiometricKeyManager(context: Context) {
    private val KEY_NAME = "my_biometric_key"
    private val ANDROID_KEYSTORE = "AndroidKeyStore"
    private val SIGNATURE_ALGORITHM = "SHA256withECDSA"
    private val _context = context

    /**
     * Generates a new public/private key pair and stores it in the Android Keystore.
     * The private key can only be used after the user has authenticated with biometrics.
     */
    suspend fun generateKeyPair(): String? {
        try {
            val keyPairGenerator = KeyPairGenerator.getInstance(
                KeyProperties.KEY_ALGORITHM_EC,
                ANDROID_KEYSTORE
            )

            val parameterSpec = KeyGenParameterSpec.Builder(
                KEY_NAME,
                KeyProperties.PURPOSE_SIGN
            )
                .setDigests(KeyProperties.DIGEST_SHA256)
                .setAlgorithmParameterSpec(ECGenParameterSpec("secp256r1"))
                .setUserAuthenticationRequired(true) // Require user authentication to use the key
                .build()

            keyPairGenerator.initialize(parameterSpec)
            val keyPair = keyPairGenerator.generateKeyPair()

            val publicKey = keyPair.public
            val publicKeyBytes = publicKey.encoded
            val publicKeyBase64 = Base64.encodeToString(publicKeyBytes, Base64.NO_WRAP)

            return publicKeyBase64

        } catch (e: Exception) {
            e.printStackTrace()
        }
        return null
    }

    private suspend fun registerPublicKey(): Boolean {
        val publicKey = getPublicKey()
        if (publicKey != null) {
            val publicKeyBytes = publicKey.encoded
            val publicKeyBase64 = Base64.encodeToString(publicKeyBytes, Base64.DEFAULT)

            val apiService = RetrofitClient.create(_context)
            val response = apiService.registerBiometric(BiometricRegisterRequest(publicKeyBase64))
            val body = response.body()
            return response.isSuccessful && body != null && body.success
        }
        return false
    }

    fun keyPairExists(): Boolean {
        return try {
            val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE)
            keyStore.load(null)
            keyStore.containsAlias(KEY_NAME)
        } catch (e: Exception) {
            Log.e("MY_APP_TAG", "Error checking for key existence", e)
            false
        }
    }

    fun deleteKeyPair() {
        try {
            val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE)
            keyStore.load(null)
            keyStore.deleteEntry(KEY_NAME)
        } catch (e: Exception) {
            Log.e("MY_APP_TAG", "Error deleting key pair", e)
        }
    }

    /**
     * Retrieves the private key from the Android Keystore.
     */
    private fun getPrivateKey(): PrivateKey? {
        try {
            val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE)
            keyStore.load(null)
            return keyStore.getKey(KEY_NAME, null) as PrivateKey?
        } catch (e: KeyStoreException) {
            e.printStackTrace()
        } catch (e: CertificateException) {
            e.printStackTrace()
        } catch (e: NoSuchAlgorithmException) {
            e.printStackTrace()
        } catch (e: IOException) {
            e.printStackTrace()
        } catch (e: UnrecoverableKeyException) {
            e.printStackTrace()
        }
        return null
    }

    fun getPublicKey(): PublicKey? {
        return try {
            val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE)
            keyStore.load(null)
            val certificate = keyStore.getCertificate(KEY_NAME)
            certificate?.publicKey
        } catch (e: Exception) {
            Log.e("MY_APP_TAG", "Error retrieving public key", e)
            null
        }
    }

    fun signChallenge(challenge: String): Signature? {

        try {
            val signature = Signature.getInstance(SIGNATURE_ALGORITHM)
            val privateKey = getPrivateKey() ?: return null
            signature.initSign(privateKey)
            signature.update(challenge.toByteArray())

            return signature

        } catch (e: Exception) {
            e.printStackTrace()
        }
        return null
    }
}