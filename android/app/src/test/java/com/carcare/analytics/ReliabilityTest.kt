package com.carcare.analytics

import androidx.lifecycle.SavedStateHandle
import com.carcare.analytics.core.network.*
import com.carcare.analytics.core.security.Session
import com.carcare.analytics.feature.vehicles.*
import com.carcare.analytics.feature.logbook.*
import com.carcare.analytics.feature.insights.*
import com.carcare.analytics.feature.reminders.*
import com.carcare.analytics.feature.forecast.*
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.test.*
import okhttp3.mockwebserver.*
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.*
import org.junit.Assert.*
import org.junit.rules.TemporaryFolder
import retrofit2.*
import retrofit2.converter.moshi.MoshiConverterFactory
import java.io.IOException
import java.time.Instant

@OptIn(ExperimentalCoroutinesApi::class)
class ReliabilityTest {
    @get:Rule val files=TemporaryFolder()
    private val dispatcher=UnconfinedTestDispatcher()
    private val server=MockWebServer()
    private val moshi=Moshi.Builder().addLast(KotlinJsonAdapterFactory()).build()
    private val errors=ErrorMapper(moshi)
    private lateinit var api: CarCareApi
    private lateinit var journalApi: JournalApi
    private lateinit var journal: JournalRepository
    private val car=VehicleDto(7,"Toyota","Corolla",2020,null,FuelType.PETROL,null,null,null,null,1000,"2026-01-01T00:00:00Z","2026-01-01T00:00:00Z")
    @Before fun setup() {
        Dispatchers.setMain(dispatcher); server.start()
        val retrofit=Retrofit.Builder().baseUrl(server.url("/api/v1/")).addConverterFactory(MoshiConverterFactory.create(moshi)).build()
        api=retrofit.create(CarCareApi::class.java)
        journalApi=retrofit.create(JournalApi::class.java); journal=JournalRepository(journalApi)
    }
    @After fun cleanup() { server.shutdown(); Dispatchers.resetMain() }
    private fun json(text: String)=MockResponse().setHeader("Content-Type","application/json").setBody(text)
    private fun garage(cars: List<VehicleDto>,page: Int=0,pages: Int=1)=json(moshi.adapter(VehiclePage::class.java).toJson(VehiclePage(cars,page,20,cars.size.toLong(),pages)))
    private fun vehicle()=json(moshi.adapter(VehicleDto::class.java).toJson(car))
    private fun rows()=json("""{"content":[],"page":0,"size":20,"totalElements":0,"totalPages":0}""")
    private fun history(rows: List<String>,page: Int=0,total: Int=rows.size)=json(
        """{"content":[${rows.joinToString(",")}],"page":$page,"size":50,"totalElements":$total,"totalPages":${(total+49)/50}}""")
    private fun historyRow(id: Int,date: String)=
        """{"id":$id,"kind":"EXPENSE","date":"$date","title":"Parking","amountPln":"1.00","category":"PARKING"}"""
    private suspend fun loaded(vm: VehicleListViewModel)=withContext(Dispatchers.Default) { withTimeout(8000) { vm.state.first { !it.loading && !it.loadingMore && it.deleting==null } } }
    private suspend fun loaded(vm: LogbookViewModel)=withContext(Dispatchers.Default) { withTimeout(8000) { vm.state.first { !it.loading && !it.loadingMore && !it.refreshing && !it.deleting } } }
    @Test fun emptyGarageIsLoadedWithoutError()=runTest(dispatcher) {
        server.enqueue(garage(emptyList(),pages=0)); val vm=VehicleListViewModel(VehicleRepository(api),errors); vm.refresh()
        val state=loaded(vm); assertTrue(state.items.isEmpty()); assertNull(state.failure); assertNull(state.activeVehicle); assertEquals(0,state.totalPages)
    }
    @Test fun garageResolvesAnActiveVehicleWithoutOpeningItsCard()=runTest(dispatcher) {
        val second=car.copy(id=8)
        val vm=VehicleListViewModel(VehicleRepository(api),errors)
        server.enqueue(garage(listOf(car,second)))
        vm.refresh()
        assertEquals(car,loaded(vm).activeVehicle)
        server.takeRequest()

        server.enqueue(garage(listOf(car),pages=2))
        server.enqueue(json(moshi.adapter(VehicleDto::class.java).toJson(second)))
        vm.refresh(selectedId=second.id)
        assertEquals(second,loaded(vm).activeVehicle)
        server.takeRequest()
        assertEquals("/api/v1/vehicles/8",server.takeRequest().path)

        server.enqueue(garage(listOf(car),pages=2))
        server.enqueue(MockResponse().setResponseCode(404))
        vm.refresh(selectedId=second.id)
        assertEquals(car,loaded(vm).activeVehicle)
    }
    @Test fun firstLoadFailureStopsSpinnerAndOffersError()=runTest(dispatcher) {
        server.enqueue(MockResponse().setResponseCode(503)); val vm=VehicleListViewModel(VehicleRepository(api),errors); vm.refresh()
        val state=loaded(vm); assertTrue(state.items.isEmpty()); assertEquals(R.string.error_server,state.failure!!.fallback)
    }
    @Test fun warmGarageSkipsFetchUntilVehicleIsEditedAndKeepsSelection()=runTest(dispatcher) {
        val session=Session("demo",Instant.MAX,1)
        val cache=ScreenReadCache({ session },{ 0L })
        val repository=VehicleRepository(api,cache=cache)
        val active=car.copy(id=8)
        val vm=VehicleListViewModel(repository,errors,cache)
        server.enqueue(garage(listOf(car,active)))
        vm.resume(active.id)
        assertEquals(active,loaded(vm).activeVehicle)
        vm.resume(active.id)
        assertEquals(1,server.requestCount)

        val edited=active.copy(model="Yaris")
        server.enqueue(json(moshi.adapter(VehicleDto::class.java).toJson(edited)))
        repository.save(edited.id,VehicleWrite(edited.make,edited.model,edited.year,edited.fuelType,edited.currentOdometerKm))
        server.enqueue(garage(listOf(car,edited)))
        vm.resume(edited.id)
        assertEquals(edited,loaded(vm).activeVehicle)
        assertEquals(3,server.requestCount)
    }
    @Test fun refreshFailureRetainsGarageAndRetryRecovers()=runTest(dispatcher) {
        val vm=VehicleListViewModel(VehicleRepository(api),errors); server.enqueue(garage(listOf(car))); vm.refresh(); loaded(vm)
        server.enqueue(MockResponse().setResponseCode(503)); vm.refresh(); assertEquals(listOf(car),loaded(vm).items)
        server.enqueue(garage(emptyList(),pages=0)); vm.refresh(); val state=loaded(vm); assertTrue(state.items.isEmpty()); assertNull(state.failure)
    }
    @Test fun failedPaginationKeepsRowsAndRetriesSamePage()=runTest(dispatcher) {
        val vm=VehicleListViewModel(VehicleRepository(api),errors); server.enqueue(garage(listOf(car),pages=2)); vm.refresh(); loaded(vm); server.takeRequest()
        server.enqueue(MockResponse().setResponseCode(503)); vm.more(); assertEquals(1,loaded(vm).nextPage); assertEquals("1",server.takeRequest().requestUrl!!.queryParameter("page"))
        server.enqueue(garage(listOf(car,car.copy(id=8)),page=1,pages=2)); vm.more(); val state=loaded(vm)
        assertEquals(listOf(7L,8L),state.items.map { it.id }); assertEquals("1",server.takeRequest().requestUrl!!.queryParameter("page"))
    }
    @Test fun failedDeleteDoesNotRemoveVehicle()=runTest(dispatcher) {
        val vm=VehicleListViewModel(VehicleRepository(api),errors); server.enqueue(garage(listOf(car))); vm.refresh(); loaded(vm)
        val deleted=CompletableDeferred<Long>()
        server.enqueue(MockResponse().setResponseCode(503)); vm.delete(7) { deleted.complete(it) }
        val state=loaded(vm); assertEquals(listOf(car),state.items); assertNotNull(state.failure)
        assertFalse(deleted.isCompleted)
    }
    @Test fun vehicleSelectionCanBeClearedAfterConfirmedDeletion()=runTest(dispatcher) {
        val vm=VehicleListViewModel(VehicleRepository(api),errors)
        server.enqueue(garage(listOf(car))); vm.refresh(); loaded(vm)
        server.enqueue(MockResponse().setResponseCode(204)); server.enqueue(garage(emptyList(),pages=0))
        val deleted=CompletableDeferred<Long>()
        vm.delete(7) { deleted.complete(it) }
        assertEquals(7L,withContext(Dispatchers.Default) { withTimeout(5000) { deleted.await() } })
        val state=loaded(vm)
        assertTrue(state.items.isEmpty())
        assertNull(state.activeVehicle)
    }
    @Test fun applyingDocumentTypeDoesNotApplyInvalidHistoryDraft()=runTest(dispatcher) {
        val history=LogbookViewModel(journal,api,errors,SavedStateHandle(mapOf("vehicleId" to "7")))
        history.changeFilters(JournalFilters(dateFrom="2026-11-02",dateTo="2026-11-01"))
        val documents=LogbookViewModel(journal,api,errors,SavedStateHandle(mapOf("vehicleId" to "7","tab" to "documents")))
        server.enqueue(vehicle()); server.enqueue(rows()); documents.documentFilter(DocumentType.INSURANCE); loaded(documents); server.takeRequest()
        val filtered=server.takeRequest().requestUrl!!
        assertEquals("INSURANCE",filtered.queryParameter("type"))
        assertNull(filtered.queryParameter("dateFrom"))
        server.enqueue(vehicle()); server.enqueue(rows()); history.refresh(); loaded(history); server.takeRequest()
        val request=server.takeRequest().requestUrl!!
        assertNull(request.queryParameter("dateFrom"))
        assertNull(request.queryParameter("dateTo"))
        assertEquals("2026-11-02",history.state.value.filters.dateFrom)
    }
    @Test fun logbookRestoresDraftWithoutChangingAppliedRange()=runTest(dispatcher) {
        val saved=SavedStateHandle(mapOf("vehicleId" to "7","filter_from" to "2026-01-01","filter_to" to "2026-01-31"))
        val first=LogbookViewModel(journal,api,errors,saved); first.changeFilters(first.state.value.filters.copy(dateFrom="2025-01-01"))
        val restored=LogbookViewModel(journal,api,errors,SavedStateHandle(saved.keys().associateWith { saved.get<Any?>(it) }))
        assertEquals("2025-01-01",restored.state.value.filters.dateFrom)
        server.enqueue(vehicle()); server.enqueue(rows()); restored.refresh(); loaded(restored); server.takeRequest()
        assertEquals("2026-01-01",server.takeRequest().requestUrl!!.queryParameter("dateFrom"))
    }
    @Test fun journalQuickFilterCoversHistoryWithoutDates()=runTest(dispatcher) {
        val vm=LogbookViewModel(journal,api,errors,SavedStateHandle(mapOf("vehicleId" to "7")))
        server.enqueue(vehicle()); server.enqueue(rows())
        vm.quickFilter(EntryKind.SERVICE.name)
        val state=loaded(vm)
        assertTrue(state.timeline.isEmpty())
        server.takeRequest()
        val list=server.takeRequest().requestUrl!!
        assertEquals("SERVICE",list.queryParameter("kind"))
        assertNull(list.queryParameter("dateFrom"))
        assertNull(list.queryParameter("dateTo"))
    }
    @Test fun warmJournalOpensImmediatelyAndSavingFuelRefreshesTheRows()=runTest(dispatcher) {
        val session=Session("demo",Instant.MAX,1)
        val cache=ScreenReadCache({ session },{ 0L })
        val repository=JournalRepository(journalApi,cache=cache)
        fun screen()=LogbookViewModel(repository,api,errors,SavedStateHandle(mapOf("vehicleId" to "7")),cache)

        val first=screen()
        server.enqueue(vehicle()); server.enqueue(history(listOf(historyRow(1,"2026-11-03"))))
        first.resume()
        assertEquals(1L,loaded(first).timeline.single().id)

        val reopened=screen()
        assertFalse(reopened.state.value.loading)
        assertEquals(1L,reopened.state.value.timeline.single().id)
        reopened.resume()
        assertEquals(2,server.requestCount)

        server.enqueue(json("""{"id":31,"vehicleId":7}""").setResponseCode(201))
        repository.save(7,EntryKind.REFUELING,null,emptyMap(),"request-key")
        val fuel="""{"id":31,"kind":"REFUELING","date":"2026-11-04","title":"ORLEN","amountPln":"210.00","category":"FUEL"}"""
        server.enqueue(vehicle()); server.enqueue(history(listOf(fuel,historyRow(1,"2026-11-03"))))
        reopened.resume()
        assertFalse(reopened.state.value.loading)
        val updated=loaded(reopened)
        assertEquals(31L,updated.timeline.first().id)
        assertEquals("210.00",updated.timeline.first().amountPln)
        assertEquals(5,server.requestCount)
    }
    @Test fun warmDocumentsSkipFetchAndReopenAfterSavingADocument()=runTest(dispatcher) {
        val session=Session("demo",Instant.MAX,1)
        val cache=ScreenReadCache({ session },{ 0L })
        val repository=JournalRepository(journalApi,cache=cache)
        fun screen()=LogbookViewModel(repository,api,errors,SavedStateHandle(mapOf("vehicleId" to "7","tab" to "documents")),cache)

        val first=screen()
        server.enqueue(vehicle()); server.enqueue(rows())
        first.resume()
        assertTrue(loaded(first).documents.isEmpty())

        val reopened=screen()
        assertFalse(reopened.state.value.loading)
        assertEquals(car,reopened.state.value.vehicle)
        reopened.resume()
        assertEquals(2,server.requestCount)

        val document="""{"id":9,"vehicleId":7,"type":"INSURANCE","validUntil":"2027-01-01"}"""
        server.enqueue(json(document).setResponseCode(201))
        repository.save(7,EntryKind.DOCUMENT,null,emptyMap(),"doc-key")
        server.enqueue(vehicle()); server.enqueue(json("""{"content":[$document],"page":0,"size":20,"totalElements":1,"totalPages":1}"""))
        reopened.resume()
        assertFalse(reopened.state.value.loading)
        val updated=withContext(Dispatchers.Default) { withTimeout(8000) { reopened.state.first { it.documents.isNotEmpty() } } }
        assertEquals(9L,updated.documents.single().id)
        assertEquals(5,server.requestCount)
    }
    @Test fun journalLoadsFiftyAtATimeAndAppendsOlderMonthsAfterRetry()=runTest(dispatcher) {
        val vm=LogbookViewModel(journal,api,errors,SavedStateHandle(mapOf("vehicleId" to "7")))
        server.enqueue(vehicle())
        server.enqueue(history((1..50).map { id -> historyRow(id,if(id<48) "2026-11-02" else "2026-10-12") },total=53))
        vm.resume()
        val first=loaded(vm)
        assertEquals(50,first.timeline.size)
        server.takeRequest()
        val firstRequest=server.takeRequest().requestUrl!!
        assertEquals("50",firstRequest.queryParameter("size"))
        assertNull(firstRequest.queryParameter("dateFrom"))
        assertNull(firstRequest.queryParameter("dateTo"))
        assertNull(firstRequest.queryParameter("expensesOnly"))

        server.enqueue(MockResponse().setResponseCode(503))
        vm.more()
        val failed=loaded(vm)
        assertNotNull(failed.failure)
        assertEquals(first.timeline,failed.timeline)
        assertEquals("1",server.takeRequest().requestUrl!!.queryParameter("page"))

        server.enqueue(history(listOf(historyRow(51,"2026-10-02"),historyRow(52,"2026-09-29"),historyRow(53,"2026-09-25")),page=1,total=53))
        vm.retry()
        val more=loaded(vm)
        assertEquals((1L..53L).toList(),more.timeline.map { it.id })
        assertEquals(listOf("2026-11","2026-10","2026-09"),more.timeline.map { it.date.take(7) }.distinct())
        val retry=server.takeRequest().requestUrl!!
        assertEquals("1",retry.queryParameter("page"))
        assertEquals("50",retry.queryParameter("size"))
        vm.more()
        assertEquals(4,server.requestCount)
    }
    @Test fun unavailableNetworkAndCacheLeaveReminderErrorStateWithoutCrash() {
        val source=object: InsightsDataSource {
            override suspend fun dashboard(vehicle: Long,from: String,to: String): DashboardDto=throw IOException()
            override suspend fun monthly(vehicle: Long, from: String, to: String, timeZone: String): MonthlyDto = throw IOException()
            override suspend fun reminders(vehicle: Long,includeDone: Boolean): CachedVehicleReminders=throw IOException()
            override suspend fun cached(vehicle: Long,includeDone: Boolean): CachedVehicleReminders=throw IOException("Cache read failed")
            override suspend fun complete(vehicle: Long,id: Long,done: Boolean) {}
        }
        val vm=RemindersViewModel(source,errors,SavedStateHandle(mapOf("vehicleId" to "7"))); vm.refresh()
        assertFalse(vm.state.value.loading); assertNull(vm.state.value.data); assertEquals(R.string.error_network,vm.state.value.failure!!.fallback)
    }
    @Test fun errorMappingPreservesValidationFieldsAndHandlesNonJsonBody() {
        val exception=HttpException(Response.error<Any>(422,"""{"code":"VALIDATION_ERROR","message":"Проверьте поля","fieldErrors":{"amountPln":"Сумма должна быть положительной"}}""".toResponseBody()))
        assertEquals("Сумма должна быть положительной",errors.map(exception).fields["amountPln"])
        assertEquals(R.string.error_server,errors.map(HttpException(Response.error<Any>(503,"<html>Offline</html>".toResponseBody()))).fallback)
        assertEquals(R.string.error_not_found,errors.map(HttpException(Response.error<Any>(404,"".toResponseBody()))).fallback)
    }
    @Test fun cancellationIsNeverShownAsUserFailure() {
        try { errors.map(CancellationException()); fail("Expected cancellation") } catch(expected: CancellationException) { }
        assertEquals(R.string.error_network,errors.map(IOException()).fallback)
    }
    @Test fun truncatedCsvIsRejectedAndRemoved() {
        val target=files.newFile()
        try { writeCsvExport(byteArrayOf(0xef.toByte()).inputStream(),target); fail("Expected invalid CSV") }
        catch(expected: InvalidCsvExport) { assertFalse(target.exists()) }
    }
}
