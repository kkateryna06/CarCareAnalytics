package com.carcare.analytics.core.network

import com.carcare.analytics.core.security.Session
import com.carcare.analytics.core.security.TokenStore
import com.carcare.analytics.feature.forecast.ForecastDto
import com.carcare.analytics.feature.insights.CachedVehicleReminders
import com.carcare.analytics.feature.insights.DashboardDto
import com.carcare.analytics.feature.insights.MonthlyDto
import com.carcare.analytics.feature.logbook.JournalFilters
import com.carcare.analytics.feature.logbook.JournalPage
import com.carcare.analytics.feature.logbook.JournalRecordDto
import com.carcare.analytics.feature.logbook.TimelineDto
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton

internal data class DashboardQuery(val from: String, val to: String, val timeZone: String)
internal data class ForecastQuery(val months: Int, val date: LocalDate, val timeZone: String)
internal data class RemindersQuery(val date: LocalDate, val timeZone: String)
internal data class JournalRead(val vehicle: VehicleDto, val page: JournalPage<TimelineDto>)
internal data class DocumentsRead(val vehicle: VehicleDto, val page: JournalPage<JournalRecordDto>)

@Singleton
class ScreenReadCache internal constructor(session: () -> Session?, now: () -> Long) {
    @Inject constructor(tokens: TokenStore) : this({ tokens.session.value }, { System.nanoTime() / 1_000_000 })

    internal val dashboards = ScreenReads<Long, DashboardQuery, DashboardDto>(session, now)
    internal val dashboardCharts = ScreenReads<Long, DashboardQuery, MonthlyDto>(session, now)
    internal val journals = ScreenReads<Long, JournalFilters, JournalRead>(session, now)
    internal val documents = ScreenReads<Long, String, DocumentsRead>(session, now)
    internal val garage = ScreenReads<Unit, Unit, VehiclePage>(session, now)
    internal val reminders = ScreenReads<Long, RemindersQuery, CachedVehicleReminders>(session, now)
    internal val forecasts = ScreenReads<Long, ForecastQuery, ForecastDto>(session, now)
    internal val forecastVehicles = ScreenReads<Long, Unit, VehicleDto>(session, now)

    fun invalidate(vehicle: Long, discard: Boolean = false) {
        dashboards.invalidate(vehicle, discard)
        dashboardCharts.invalidate(vehicle, discard)
        journals.invalidate(vehicle, discard)
        documents.invalidate(vehicle, discard)
        reminders.invalidate(vehicle, discard)
        forecasts.invalidate(vehicle, discard)
        forecastVehicles.invalidate(vehicle, discard)
    }
}

internal class ScreenReads<Scope : Any, Query, Value : Any>(
    private val session: () -> Session?,
    private val now: () -> Long
) {
    data class Snapshot<Value>(val value: Value, val fresh: Boolean)

    private data class Key<Scope, Query>(val scope: Scope, val query: Query)
    private class Slot<Value> {
        var value: Value? = null
        var fetchedAt: Long? = null
        var request: Any? = null
    }

    private var owner: Session? = null
    private val slots = LinkedHashMap<Key<Scope, Query>, Slot<Value>>(16, 0.75f, true)

    private fun currentOwner(): Session? {
        val current = session()
        if (current !== owner) {
            slots.clear()
            owner = current
        }
        return owner
    }

    @Synchronized
    fun snapshot(scope: Scope, query: Query): Snapshot<Value>? {
        currentOwner() ?: return null
        val slot = slots[Key(scope, query)] ?: return null
        val value = slot.value ?: return null
        return Snapshot(value, slot.fetchedAt?.let { now() - it < 60_000 } ?: false)
    }

    suspend fun fetch(scope: Scope, query: Query, complete: (Value) -> Boolean = { true }, load: suspend () -> Value): Value {
        val key = Key(scope, query)
        val request = Any()
        val account: Session?
        val slot = synchronized(this) {
            account = currentOwner()
            if (account == null) null else slots.getOrPut(key) { Slot() }.also {
                it.request = request
                if (slots.size > 32) slots.remove(slots.keys.first())
            }
        }
        val value = load()
        currentCoroutineContext().ensureActive()
        synchronized(this) {
            if (slot != null && currentOwner() === account && slots[key] === slot && slot.request === request) {
                slot.value = value
                slot.fetchedAt = now().takeIf { complete(value) }
                slot.request = null
            }
        }
        return value
    }

    @Synchronized
    fun invalidate(scope: Scope? = null, discard: Boolean = false) {
        currentOwner()
        val entries = slots.iterator()
        while (entries.hasNext()) {
            val (key, slot) = entries.next()
            if (scope != null && key.scope != scope) continue
            if (discard) entries.remove() else {
                slot.fetchedAt = null
                slot.request = null
            }
        }
    }
}
