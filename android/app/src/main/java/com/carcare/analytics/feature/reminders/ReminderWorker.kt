package com.carcare.analytics.feature.reminders

import android.content.Context
import androidx.work.*
import com.carcare.analytics.core.network.CarCareApi
import com.carcare.analytics.core.security.TokenStore
import com.carcare.analytics.feature.insights.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import retrofit2.HttpException
import java.time.*
import javax.inject.*

class ReminderWorker(context: Context,params: WorkerParameters,private val tokens: TokenStore,private val store: NotificationStore,
    private val vehicles: CarCareApi,private val api: InsightsApi,private val notifier: ReminderNotifier): CoroutineWorker(context,params) {
    override suspend fun doWork(): Result {
        val user=inputData.getLong("user_id",-1); if(user<1) return Result.success()
        withTimeout(20_000) { tokens.ready.first { it } }
        val preference=store.state.first(); if(preference.userId!=user || !preference.enabled || !notifier.allowed()) return Result.success()
        val session=tokens.session.value; var stale=true; var retry=false
        if(session?.userId==user) {
            try {
                val data=mutableListOf<CachedVehicleReminders>(); var page=0; var pages=1
                while(page<pages) {
                    val cars=vehicles.vehicles(page,100); pages=cars.totalPages; page++
                    for(car in cars.content) {
                        try { data.add(CachedVehicleReminders(car.id,"${car.make} ${car.model}",api.reminders(car.id,ZoneId.systemDefault().id,true),Instant.now().toString())) }
                        catch(ex: HttpException) { if(ex.code()!=404) throw ex }
                    }
                }
                if(tokens.session.value?.accessToken==session.accessToken) { store.replaceCache(ReminderCache(user,data)); stale=false }
            } catch(ex: CancellationException) { throw ex }
            catch(ex: Exception) { retry=ex !is HttpException || ex.code()>=500 }
        }
        store.cache(user)?.let { notifier.publish(it,stale) }
        return if(retry && runAttemptCount<3) Result.retry() else Result.success()
    }
}
@Singleton
class ReminderWorkerFactory @Inject constructor(private val tokens: TokenStore,private val store: NotificationStore,private val vehicles: CarCareApi,private val api: InsightsApi,private val notifier: ReminderNotifier): WorkerFactory() {
    override fun createWorker(appContext: Context,workerClassName: String,workerParameters: WorkerParameters): ListenableWorker? =
        if(workerClassName==ReminderWorker::class.java.name) ReminderWorker(appContext,workerParameters,tokens,store,vehicles,api,notifier) else null
}
