package com.carcare.analytics.feature.logbook

import javax.inject.Inject
import com.carcare.analytics.core.network.ScreenReadCache
import com.carcare.analytics.feature.reminders.ReminderScheduler

data class JournalFilters(val kind: String = "",val category: String = "",val dateFrom: String = "",val dateTo: String = "",val documentType: String = "")
class JournalRepository @Inject constructor(private val api: JournalApi,private val reminders: ReminderScheduler? = null,private val cache: ScreenReadCache? = null) {
    suspend fun timeline(vehicle: Long,page: Int,filters: JournalFilters) = api.timeline(vehicle,page,filters.kind.ifBlank { null },filters.category.ifBlank { null },filters.dateFrom.ifBlank { null },filters.dateTo.ifBlank { null },size=50)
    suspend fun documents(vehicle: Long,page: Int,filters: JournalFilters) = api.documents(vehicle,page,filters.documentType.ifBlank { null })
    suspend fun get(kind: EntryKind,id: Long) = api.get(kind.resource,id)
    suspend fun save(vehicle: Long,kind: EntryKind,id: Long?,data: Map<String,Any?>,key: String) =
        (if(id==null) api.create(vehicle,kind.resource,data,key) else api.update(kind.resource,id,data)).also {
            cache?.invalidate(vehicle)
            cache?.garage?.invalidate()
            reminders?.syncSoon()
        }
    suspend fun delete(vehicle: Long,kind: EntryKind,id: Long) {
        api.delete(kind.resource,id)
        cache?.invalidate(vehicle)
        cache?.garage?.invalidate()
        reminders?.syncSoon()
    }
}
