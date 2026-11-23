package com.carcare.analytics

import androidx.lifecycle.SavedStateHandle
import com.carcare.analytics.core.network.*
import com.carcare.analytics.core.security.Session
import com.carcare.analytics.feature.insights.*
import com.carcare.analytics.feature.reminders.*
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.*
import org.junit.*
import org.junit.Assert.*
import java.io.IOException
import java.time.Instant
import java.time.LocalDate

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class InsightsAndNotificationsTest {
    private val today=LocalDate.of(2026,11,12)
    private val dispatcher=UnconfinedTestDispatcher()
    private val moshi=Moshi.Builder().addLast(KotlinJsonAdapterFactory()).build()
    private fun rule(date: String? = "2026-11-19",km: Int? = null,status: ReminderStatus = ReminderStatus.UPCOMING)=ReminderDto("SERVICE:1:$date:$km","SERVICE",1,"OIL_CHANGE","Oil",date,km,null,null,status,null,null)
    @Before fun setup() { Dispatchers.setMain(dispatcher) }
    @After fun cleanup() { Dispatchers.resetMain() }
    @Test fun calendarThresholdsAreInclusiveAndFarFutureIsSilent() {
        for(days in listOf(31,30,7,1,0,-1)) {
            val r=rule(today.plusDays(days.toLong()).toString()); val local=evaluateReminder(r,today,10000)
            assertEquals(when { days<0 -> ReminderStatus.OVERDUE; days==0 -> ReminderStatus.DUE; days<=30 -> ReminderStatus.UPCOMING; else -> ReminderStatus.SCHEDULED },local.status)
            assertEquals(when { days<0 -> "DATE_OVERDUE"; days==0 -> "DATE_DUE"; days<=1 -> "D1"; days<=7 -> "D7"; days<=30 -> "D30"; else -> null },local.stage)
        }
    }
    @Test fun cachedRuleProgressesAfterSessionExpiresWithoutNetwork() {
        val cached=rule("2026-11-13")
        assertEquals("D1",evaluateReminder(cached,today,0).stage)
        assertEquals("DATE_DUE",evaluateReminder(cached,today.plusDays(1),0).stage)
        assertEquals("DATE_OVERDUE",evaluateReminder(cached,today.plusDays(2),0).stage)
    }
    @Test fun reminderListUsesCurrentUrgencyAndKeepsCompletedLast() {
        val completed=rule("2026-11-01",status=ReminderStatus.DONE)
        val later=rule("2027-01-01")
        val mileageDue=rule(null,10000)
        val justExpired=rule("2026-11-13")
        val sooner=rule("2026-11-16")
        val cached=RemindersDto("2026-11-12","Europe/Warsaw",10000,listOf(completed,later,mileageDue,sooner,justExpired))

        val sorted=sortedReminders(cached,today.plusDays(2))

        assertEquals(listOf(justExpired,mileageDue,sooner,later,completed),sorted.map { it.reminder })
        assertEquals(listOf(ReminderStatus.OVERDUE,ReminderStatus.DUE,ReminderStatus.UPCOMING,ReminderStatus.SCHEDULED,ReminderStatus.DONE),sorted.map { it.timing.status })
    }
    @Test fun sameThresholdProducesSameDeduplicationKeyAcrossDays() {
        val cached=rule("2026-11-19")
        assertEquals(evaluateReminder(cached,today,0).notificationKey,evaluateReminder(cached,today.plusDays(1),0).notificationKey)
        assertNotEquals(evaluateReminder(cached,today,0).notificationKey,evaluateReminder(cached,today.plusDays(6),0).notificationKey)
    }
    @Test fun mileageUsesLastSyncedReadingAndCombinesWithDate() {
        val cached=rule("2026-11-19",10500)
        assertEquals("D7|K500",evaluateReminder(cached,today,10000).stage)
        assertEquals("D7|K100",evaluateReminder(cached,today,10400).stage)
        assertEquals(ReminderStatus.OVERDUE,evaluateReminder(cached,today,10501).status)
    }
    @Test fun completedRulesNeverNotifyAndChangedDeadlineHasNewKey() {
        assertNull(evaluateReminder(rule("2026-11-01",9000,ReminderStatus.DONE),today,10000).notificationKey)
        assertNotEquals(evaluateReminder(rule("2026-11-19"),today,0).notificationKey,evaluateReminder(rule("2026-11-18"),today,0).notificationKey)
    }
    @Test fun analyticsPeriodValidatesMissingReversedAndOversizedRanges() {
        assertTrue(validateAnalyticsPeriod("2026-01-01","2026-01-31").isEmpty())
        assertTrue(validateAnalyticsPeriod("","2026-01-01").containsKey("dateFrom"))
        assertTrue(validateAnalyticsPeriod("2026-02-01","2026-01-31").containsKey("dateTo"))
        assertTrue(validateAnalyticsPeriod("1000-01-01","2026-01-31").containsKey("dateFrom"))
    }
    @Test fun jsonPreservesExactDecimalAndUnavailableMetric() {
        val json="""{"period":{"dateFrom":"2026-01-01","dateTo":"2026-01-31","monthCount":1},"totalExpensesPln":9999999999.99,"monthAveragePln":9999999999.99,"distanceKm":null,"costPerKmPln":null,"recordCount":1,"distanceWindow":null,"warnings":["NO_ODOMETER_PAIR"]}"""
        val dto=checkNotNull(moshi.adapter(SummaryDto::class.java).fromJson(json))
        assertEquals("9999999999.99",dto.totalExpensesPln); assertNull(dto.costPerKmPln)
    }
    @Test fun refreshErrorKeepsPreviousDashboardWithStaleFlag() {
        val source=FakeInsights(); val vm=DashboardViewModel(source,ErrorMapper(moshi),SavedStateHandle(mapOf("vehicleId" to "7")))
        vm.refresh(); val previous=vm.state.value.data; assertNotNull(previous)
        source.fail=true; vm.refresh()
        assertEquals(previous,vm.state.value.data); assertTrue(vm.state.value.stale); assertNotNull(vm.state.value.failure)
    }
    @Test fun restoredDraftDoesNotSilentlyChangeAppliedPeriod() {
        val source=FakeInsights(); val saved=SavedStateHandle(mapOf("vehicleId" to "7")); val vm=DashboardViewModel(source,ErrorMapper(moshi),saved)
        vm.changeFrom("2026-01-01"); vm.changeTo("2026-01-31"); vm.apply(); vm.changeFrom("2025-01-01")
        val restored=SavedStateHandle(saved.keys().associateWith { saved.get<Any?>(it) })
        val recreated=DashboardViewModel(source,ErrorMapper(moshi),restored); recreated.refresh()
        assertEquals("2025-01-01",recreated.state.value.from); assertEquals("2026-01-01",source.from)
    }
    @Test fun dashboardReadsCacheUntilExpiryAndDoesNotReuseItAcrossSessions() {
        var session=Session("first",Instant.MAX,1)
        var now=0L
        val cache=ScreenReadCache({ session },{ now })
        val source=FakeInsights()
        fun screen()=DashboardViewModel(source,ErrorMapper(moshi),SavedStateHandle(mapOf("vehicleId" to "7")),cache)
        screen().resume()
        val reopened=screen()
        assertFalse(reopened.state.value.loading)
        assertNotNull(reopened.state.value.data)
        reopened.resume()
        assertEquals(1,source.dashboardCalls)

        cache.invalidate(7)
        reopened.resume()
        assertEquals(2,source.dashboardCalls)
        now+=60_000
        reopened.resume()
        assertEquals(3,source.dashboardCalls)

        session=Session("second",Instant.MAX,2)
        assertNull(screen().state.value.data)
    }
    @Test fun chartUsesCalendarMonthsAndReusesCachedReportUntilAnExpenseChanges() {
        val session = Session("demo", Instant.MAX, 1)
        val cache = ScreenReadCache({ session }, { 0L })
        val source = FakeInsights()
        val saved = SavedStateHandle(mapOf("vehicleId" to "7", "active_from" to "2026-07-12", "active_to" to "2026-09-14"))
        val vm = DashboardViewModel(source, ErrorMapper(moshi), saved, cache)

        vm.resume()
        assertEquals("2025-10-01", vm.chart.value.data?.period?.dateFrom)
        assertEquals("2026-09-30", vm.chart.value.data?.period?.dateTo)
        assertEquals("2026-07-12" to "2026-09-14", source.dashboardRanges.single())
        assertNull(vm.chart.value.selection)

        vm.selectMonth("2026-02")
        assertEquals("2026-02-01" to "2026-02-28", source.dashboardRanges.last())
        assertNotNull(vm.chart.value.selection?.data)
        vm.selectMonth("2026-09")
        assertEquals("2026-09-01" to "2026-09-30", source.dashboardRanges.last())
        vm.selectMonth("2026-02")
        assertEquals("2026-02", vm.chart.value.selection?.month.toString())
        assertEquals(3, source.dashboardCalls)
        assertEquals(1, source.monthlyCalls)

        cache.invalidate(7)
        vm.resume()
        assertEquals(2, source.monthlyCalls)
        assertEquals(5, source.dashboardCalls)
        assertEquals("2026-02-01" to "2026-02-28", source.dashboardRanges.last())
    }
    @Test fun remindersFallBackToCachedRulesWhenNetworkFails() {
        val source=FakeInsights(); source.fail=true
        val vm=RemindersViewModel(source,ErrorMapper(moshi),SavedStateHandle(mapOf("vehicleId" to "7")))
        vm.refresh(); assertTrue(vm.state.value.stale); assertNotNull(vm.state.value.data); assertNotNull(vm.state.value.failure)
    }
    @Test fun warmRemindersAndCompletedToggleReuseTheSameFullList() {
        val session=Session("demo",Instant.MAX,1)
        val cache=ScreenReadCache({ session },{ 0L })
        val source=FakeInsights()
        val completed=rule(status=ReminderStatus.DONE)
        source.reminders=source.reminders.copy(content=listOf(completed))
        fun screen()=RemindersViewModel(source,ErrorMapper(moshi),SavedStateHandle(mapOf("vehicleId" to "7")),cache)
        screen().resume()
        val reopened=screen()
        assertFalse(reopened.state.value.loading)
        reopened.resume()
        reopened.showDone(true)
        assertEquals(listOf(completed),reopened.state.value.data?.reminders?.content)
        assertTrue(reopened.state.value.includeDone)
        assertEquals(1,source.remindersCalls)

        cache.invalidate(7)
        reopened.resume()
        assertEquals(2,source.remindersCalls)
    }
    private class FakeInsights: InsightsDataSource {
        var fail=false; var from=""
        var dashboardCalls=0
        val dashboardRanges = mutableListOf<Pair<String, String>>()
        var monthlyCalls = 0
        var remindersCalls=0
        val p=AnalyticsPeriod("2026-01-01","2026-01-31",1)
        var reminders=RemindersDto("2026-11-12","Europe/Warsaw",1000,emptyList())
        override suspend fun dashboard(vehicle: Long,from: String,to: String): DashboardDto {
            dashboardCalls++
            dashboardRanges.add(from to to)
            this.from=from; if(fail) throw IOException("offline")
            val car=VehicleDto(7,"Toyota","Corolla",2020,null,FuelType.HYBRID,null,null,null,null,1000,"2026-01-01T00:00:00Z","2026-01-01T00:00:00Z")
            return DashboardDto(car,SummaryDto(p,"0.00","0.00",null,null,0,null,emptyList()),CategoriesDto(p,emptyList()),MonthlyDto(p,emptyList()),FuelDto(p,"0","0",null,null,0,"0",0,0,emptyList()),"0","0",reminders,emptyList())
        }
        override suspend fun monthly(vehicle: Long, from: String, to: String, timeZone: String): MonthlyDto {
            monthlyCalls++
            if (fail) throw IOException("offline")
            return MonthlyDto(AnalyticsPeriod(from, to, 12), emptyList())
        }
        override suspend fun reminders(vehicle: Long,includeDone: Boolean): CachedVehicleReminders {
            remindersCalls++
            if(fail) throw IOException("offline")
            return checkNotNull(cached(vehicle,includeDone))
        }
        override suspend fun cached(vehicle: Long,includeDone: Boolean)=CachedVehicleReminders(vehicle,"Toyota",reminders,"2026-11-12T00:00:00Z")
        override suspend fun complete(vehicle: Long,id: Long,done: Boolean) {}
    }
}
