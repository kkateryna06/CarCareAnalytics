package com.carcare.analytics.feature.reminders

import android.content.Context
import android.security.keystore.*
import android.util.Base64
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import com.carcare.analytics.feature.insights.*
import com.squareup.moshi.Moshi
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.*
import java.security.KeyStore
import javax.crypto.*
import javax.crypto.spec.GCMParameterSpec
import javax.inject.*

private val Context.notificationData by preferencesDataStore("reminders")
data class NotificationPreference(val userId: Long?,val enabled: Boolean)
@Singleton
class NotificationStore @Inject constructor(@param:ApplicationContext private val context: Context,moshi: Moshi) {
    private val active=longPreferencesKey("active_account")
    private val enabled=stringSetPreferencesKey("enabled_accounts")
    private val cacheKey=stringPreferencesKey("encrypted_cache")
    private val adapter=moshi.adapter(ReminderCache::class.java)
    val state: Flow<NotificationPreference> = context.notificationData.data.map { p -> p[active].let { NotificationPreference(it,it!=null && p[enabled].orEmpty().contains(it.toString())) } }.distinctUntilChanged()
    suspend fun selectAccount(user: Long) { context.notificationData.edit { p -> if(p[active]!=user) { p.remove(cacheKey); p[active]=user } } }
    suspend fun enable(value: Boolean) { context.notificationData.edit { p -> p[active]?.let { user -> p[enabled]=if(value) p[enabled].orEmpty()+user.toString() else p[enabled].orEmpty()-user.toString() } } }
    suspend fun logout() { context.notificationData.edit { p -> p.remove(active); p.remove(cacheKey) } }
    suspend fun cache(user: Long): ReminderCache? {
        val p=context.notificationData.data.first(); if(p[active]!=user) return null
        return decode(p[cacheKey])?.takeIf { it.userId==user }
    }
    suspend fun replaceCache(value: ReminderCache) { context.notificationData.edit { p -> if(p[active]==value.userId) p[cacheKey]=encrypt(adapter.toJson(value)) } }
    suspend fun cacheVehicle(user: Long,vehicle: CachedVehicleReminders) {
        context.notificationData.edit { p -> if(p[active]==user) {
            val old=decode(p[cacheKey])?.takeIf { it.userId==user }?.vehicles.orEmpty()
            p[cacheKey]=encrypt(adapter.toJson(ReminderCache(user,old.filterNot { it.vehicleId==vehicle.vehicleId }+vehicle)))
        } }
    }
    suspend fun removeVehicle(vehicle: Long) { context.notificationData.edit { p -> decode(p[cacheKey])?.let { p[cacheKey]=encrypt(adapter.toJson(it.copy(vehicles=it.vehicles.filterNot { v -> v.vehicleId==vehicle }))) } } }
    suspend fun deliverOnce(user: Long,key: String,deliver: ()->Boolean) {
        context.notificationData.edit { p ->
            val seenKey=stringSetPreferencesKey("seen_$user"); val seen=p[seenKey].orEmpty()
            if(p[active]==user && p[enabled].orEmpty().contains(user.toString()) && key !in seen && deliver()) p[seenKey]=(seen+key).toList().takeLast(1024).toSet()
        }
    }
    private fun decode(value: String?): ReminderCache? = value?.let { runCatching { adapter.fromJson(decrypt(it)) }.getOrNull() }
    private fun key(): SecretKey {
        val store=KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        return (store.getKey("carcare_reminders_v1",null) as? SecretKey) ?: KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES,"AndroidKeyStore").run {
            init(KeyGenParameterSpec.Builder("carcare_reminders_v1",KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT).setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).build()); generateKey()
        }
    }
    private fun encrypt(value: String): String {
        val cipher=Cipher.getInstance("AES/GCM/NoPadding").apply { init(Cipher.ENCRYPT_MODE,key()) }
        return Base64.encodeToString(cipher.iv+cipher.doFinal(value.toByteArray(Charsets.UTF_8)),Base64.NO_WRAP)
    }
    private fun decrypt(value: String): String {
        val bytes=Base64.decode(value,Base64.NO_WRAP); require(bytes.size>12)
        val cipher=Cipher.getInstance("AES/GCM/NoPadding").apply { init(Cipher.DECRYPT_MODE,key(),GCMParameterSpec(128,bytes.copyOfRange(0,12))) }
        return cipher.doFinal(bytes.copyOfRange(12,bytes.size)).toString(Charsets.UTF_8)
    }
}
