package com.carcare.analytics.feature.reminders

import android.content.Context
import androidx.work.*
import com.carcare.analytics.core.security.TokenStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import java.util.concurrent.TimeUnit
import javax.inject.*

@Singleton
class ReminderScheduler @Inject constructor(@param:ApplicationContext private val context: Context,private val store: NotificationStore,private val notifier: ReminderNotifier) {
    private val scope=CoroutineScope(SupervisorJob()+Dispatchers.IO)
    fun configure(preference: NotificationPreference) {
        stopNow()
        if(preference.enabled && preference.userId!=null) {
            val work=PeriodicWorkRequestBuilder<ReminderWorker>(6,TimeUnit.HOURS).setInputData(workDataOf("user_id" to preference.userId)).build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(PERIODIC,ExistingPeriodicWorkPolicy.UPDATE,work)
            syncSoon()
        }
    }
    fun syncSoon() { scope.launch { val p=store.state.first(); if(p.enabled && p.userId!=null) {
        val work=OneTimeWorkRequestBuilder<ReminderWorker>().setInputData(workDataOf("user_id" to p.userId)).setBackoffCriteria(BackoffPolicy.EXPONENTIAL,15,TimeUnit.MINUTES).build()
        WorkManager.getInstance(context).enqueueUniqueWork(SYNC,ExistingWorkPolicy.REPLACE,work)
    } } }
    suspend fun vehicleDeleted(id: Long) { store.removeVehicle(id); syncSoon() }
    fun stopNow() { val work=WorkManager.getInstance(context); work.cancelUniqueWork(PERIODIC); work.cancelUniqueWork(SYNC); notifier.cancelAll() }
    companion object { private const val PERIODIC="carcare-reminders"; private const val SYNC="carcare-reminders-sync" }
}
@Singleton
class ReminderCoordinator @Inject constructor(private val tokens: TokenStore,private val store: NotificationStore,private val scheduler: ReminderScheduler) {
    private val scope=CoroutineScope(SupervisorJob()+Dispatchers.IO)
    fun start() {
        scope.launch { tokens.ready.first { it }; tokens.session.collect { session -> if(session!=null) store.selectAccount(session.userId) } }
        scope.launch { store.state.collect { scheduler.configure(it) } }
    }
}
