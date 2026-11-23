package com.carcare.analytics.feature.insights

import com.carcare.analytics.core.network.CarCareApi
import com.carcare.analytics.core.network.ScreenReadCache
import com.carcare.analytics.core.security.TokenStore
import com.carcare.analytics.feature.reminders.NotificationStore
import com.carcare.analytics.feature.reminders.ReminderScheduler
import java.time.*
import javax.inject.Inject

interface InsightsDataSource {
    suspend fun dashboard(vehicle: Long,from: String,to: String): DashboardDto
    suspend fun monthly(vehicle: Long, from: String, to: String, timeZone: String): MonthlyDto
    suspend fun reminders(vehicle: Long,includeDone: Boolean): CachedVehicleReminders
    suspend fun cached(vehicle: Long,includeDone: Boolean): CachedVehicleReminders?
    suspend fun complete(vehicle: Long,id: Long,done: Boolean)
}
class InsightsRepository @Inject constructor(private val api: InsightsApi,private val vehicles: CarCareApi,private val tokens: TokenStore,private val store: NotificationStore,private val scheduler: ReminderScheduler,private val screenCache: ScreenReadCache): InsightsDataSource {
    override suspend fun dashboard(vehicle: Long,from: String,to: String): DashboardDto {
        val session=tokens.session.value
        val data=api.dashboard(vehicle,from,to,ZoneId.systemDefault().id)
        if(session!=null && tokens.session.value?.accessToken==session.accessToken) store.cacheVehicle(session.userId,CachedVehicleReminders(vehicle,"${data.vehicle.make} ${data.vehicle.model}",data.reminders,Instant.now().toString()))
        return data
    }
    override suspend fun monthly(vehicle: Long, from: String, to: String, timeZone: String): MonthlyDto =
        api.monthly(vehicle, from, to, timeZone)

    override suspend fun reminders(vehicle: Long,includeDone: Boolean): CachedVehicleReminders {
        val session=tokens.session.value
        val car=vehicles.vehicle(vehicle)
        val all=api.reminders(vehicle,ZoneId.systemDefault().id,true)
        val cached=CachedVehicleReminders(vehicle,"${car.make} ${car.model}",all,Instant.now().toString())
        if(session!=null && tokens.session.value?.accessToken==session.accessToken) store.cacheVehicle(session.userId,cached)
        return cached.copy(reminders=all.copy(content=all.content.filter { includeDone || it.status!=ReminderStatus.DONE }))
    }
    override suspend fun cached(vehicle: Long,includeDone: Boolean): CachedVehicleReminders? {
        val user=tokens.session.value?.userId?:return null
        return store.cache(user)?.vehicles?.firstOrNull { it.vehicleId==vehicle }?.let { it.copy(reminders=it.reminders.copy(content=it.reminders.content.filter { item -> includeDone || item.status!=ReminderStatus.DONE })) }
    }
    override suspend fun complete(vehicle: Long,id: Long,done: Boolean) {
        api.complete(id,mapOf("done" to done))
        screenCache.dashboards.invalidate(vehicle)
        screenCache.reminders.invalidate(vehicle)
        scheduler.syncSoon()
    }
}
