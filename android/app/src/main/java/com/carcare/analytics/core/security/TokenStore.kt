package com.carcare.analytics.core.security

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import com.carcare.analytics.core.network.AuthResponse
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONObject
import java.security.KeyStore
import java.time.Instant
import javax.crypto.*
import javax.crypto.spec.GCMParameterSpec
import javax.inject.*

private val Context.sessionData by preferencesDataStore("session")
data class Session(val accessToken: String, val expiresAt: Instant,val userId: Long)

@Singleton
class TokenStore @Inject constructor(@param:ApplicationContext private val context: Context) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val mutex = Mutex()
    private val keyName = stringPreferencesKey("encrypted_session")
    private val _session = MutableStateFlow<Session?>(null)
    val session: StateFlow<Session?> = _session.asStateFlow()
    private val _ready = MutableStateFlow(false)
    val ready: StateFlow<Boolean> = _ready.asStateFlow()
    private var expiration: Job? = null
    init {
        scope.launch {
            mutex.withLock {
                val restored = runCatching {
                    context.sessionData.data.first()[keyName]?.let { encrypted ->
                        val data = JSONObject(decrypt(encrypted))
                        val token=data.getString("token")
                        val subject=JSONObject(String(Base64.decode(token.split('.')[1],Base64.URL_SAFE or Base64.NO_WRAP),Charsets.UTF_8)).getString("sub").toLong()
                        Session(token, Instant.parse(data.getString("expiresAt")),subject)
                    }?.takeIf { it.expiresAt.isAfter(Instant.now()) }
                }.getOrNull()
                update(restored)
                _ready.value = true
            }
        }
    }
    suspend fun save(response: AuthResponse) = mutex.withLock {
        val session = Session(response.accessToken, Instant.parse(response.expiresAt),response.user.id)
        val data = JSONObject().put("token", session.accessToken).put("expiresAt", session.expiresAt.toString()).toString()
        context.sessionData.edit { it[keyName] = encrypt(data) }
        update(session)
    }
    suspend fun clear() = mutex.withLock { context.sessionData.edit { it.remove(keyName) }; update(null) }
    fun invalidate(token: String) {
        scope.launch {
            mutex.withLock {
                if (_session.value?.accessToken == token) {
                    // Clear memory even if storage is temporarily unavailable.
                    runCatching { context.sessionData.edit { it.remove(keyName) } }
                    update(null)
                }
            }
        }
    }
    private fun update(session: Session?) {
        expiration?.cancel()
        _session.value = session
        if (session != null) expiration = scope.launch {
            delay((session.expiresAt.toEpochMilli() - System.currentTimeMillis()).coerceAtLeast(0))
            invalidate(session.accessToken)
        }
    }
    private fun key(): SecretKey {
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        return (store.getKey("carcare_session_v1", null) as? SecretKey) ?: KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore").run {
            init(KeyGenParameterSpec.Builder("carcare_session_v1", KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).build())
            generateKey()
        }
    }
    private fun encrypt(value: String): String {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding").apply { init(Cipher.ENCRYPT_MODE, key()) }
        return Base64.encodeToString(cipher.iv + cipher.doFinal(value.toByteArray(Charsets.UTF_8)), Base64.NO_WRAP)
    }
    private fun decrypt(value: String): String {
        val bytes = Base64.decode(value, Base64.NO_WRAP)
        require(bytes.size > 12)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding").apply { init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, bytes.copyOfRange(0, 12))) }
        return cipher.doFinal(bytes.copyOfRange(12, bytes.size)).toString(Charsets.UTF_8)
    }
}
