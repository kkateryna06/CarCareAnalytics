package com.carcare.analytics

import androidx.lifecycle.SavedStateHandle
import com.carcare.analytics.core.network.ErrorMapper
import com.carcare.analytics.core.network.FuelType
import com.carcare.analytics.core.network.ScreenReadCache
import com.carcare.analytics.core.network.VehicleDto
import com.carcare.analytics.core.security.Session
import com.carcare.analytics.feature.forecast.*
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.*
import kotlinx.coroutines.test.*
import okhttp3.mockwebserver.*
import org.junit.*
import org.junit.Assert.*
import org.junit.rules.TemporaryFolder
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import java.io.*
import java.time.Instant

@OptIn(ExperimentalCoroutinesApi::class)
class ForecastTest {
    @get:Rule val temporary=TemporaryFolder()
    private val moshi=Moshi.Builder().addLast(KotlinJsonAdapterFactory()).build()
    private val dispatcher=UnconfinedTestDispatcher()
    @Before fun setup() { Dispatchers.setMain(dispatcher) }
    @After fun cleanup() { Dispatchers.resetMain() }
    private fun data(enough: Boolean=true)=ForecastDto(7,"2026-11-12","Europe/Warsaw","2026-12","2026-11",3,2,if(enough) 6 else 2,12,enough,if(enough) 2 else 0,null,
        emptyList(),listOf(ForecastMethod("SIMPLE_MOVING_AVERAGE",if(enough) "200.00" else null,null),ForecastMethod("WEIGHTED_MOVING_AVERAGE",if(enough) "233.33" else null,null)),emptyList(),emptyList())
    private inner class Source: ForecastDataSource {
        var fail=false; var result=data(); var exports=0; var hold: CompletableDeferred<Unit>?=null
        var forecastCalls=0; var vehicleCalls=0; var forecastHold: CompletableDeferred<Unit>?=null
        val file=temporary.newFile()
        override suspend fun vehicle(vehicle: Long): VehicleDto {
            vehicleCalls++
            return VehicleDto(vehicle,"Toyota","Corolla",2020,null,FuelType.PETROL,null,null,null,null,100000,"","")
        }
        override suspend fun forecast(vehicle: Long,historyMonths: Int): ForecastDto {
            forecastCalls++
            forecastHold?.await()
            if(fail) throw IOException("Offline")
            return result.copy(requestedHistoryMonths=historyMonths)
        }
        override suspend fun export(vehicle: Long,from: String,to: String): CsvArtifact { exports++; hold?.await(); if(fail) throw IOException("Offline"); return CsvArtifact(file) }
    }
    private fun vm(source: Source,saved: SavedStateHandle=SavedStateHandle(mapOf("vehicleId" to "7")))=ForecastViewModel(source,ErrorMapper(moshi),saved)
    @Test fun unavailableForecastStaysUnavailableRatherThanZero() {
        val source=Source(); source.result=data(false); val vm=vm(source); vm.refresh()
        assertFalse(vm.state.value.data!!.sufficientData); assertNull(vm.state.value.data!!.methods.first().forecastPln)
    }
    @Test fun refreshFailureRetainsClearlyStalePreviousResult() {
        val source=Source(); val vm=vm(source); vm.refresh(); val old=vm.state.value.data
        source.fail=true; vm.refresh(); assertEquals(old,vm.state.value.data); assertTrue(vm.state.value.stale); assertNotNull(vm.state.value.failure)
    }
    @Test fun historySelectionAndExportDraftSurviveRecreation() {
        val source=Source(); val saved=SavedStateHandle(mapOf("vehicleId" to "7")); val vm=vm(source,saved)
        vm.history(24); vm.from("2026-01-01"); vm.to("2026-10-31")
        val recreated=vm(source,SavedStateHandle(saved.keys().associateWith { saved.get<Any?>(it) })); recreated.refresh()
        assertEquals(24,recreated.state.value.data!!.requestedHistoryMonths); assertEquals("2026-01-01",recreated.state.value.from); assertEquals("2026-10-31",recreated.state.value.to)
    }
    @Test fun warmForecastSkipsReadsAndKeepsTheLastPredictionDuringAnInvalidatedRefresh()=runTest(dispatcher) {
        val session=Session("demo",Instant.MAX,1)
        val cache=ScreenReadCache({ session },{ 0L })
        val source=Source()
        fun screen()=ForecastViewModel(source,ErrorMapper(moshi),SavedStateHandle(mapOf("vehicleId" to "7")),cache)
        screen().resume()

        val reopened=screen()
        assertFalse(reopened.state.value.loading)
        assertEquals("200.00",reopened.state.value.data?.methods?.first()?.forecastPln)
        assertNotNull(reopened.state.value.vehicle)
        reopened.resume()
        assertEquals(1,source.forecastCalls)
        assertEquals(1,source.vehicleCalls)

        reopened.history(6)
        assertEquals(6,reopened.state.value.data?.requestedHistoryMonths)
        reopened.history(12)
        assertEquals(12,reopened.state.value.data?.requestedHistoryMonths)
        assertEquals(2,source.forecastCalls)

        cache.invalidate(7)
        val pending=CompletableDeferred<Unit>()
        source.forecastHold=pending
        source.result=source.result.copy(methods=listOf(ForecastMethod("SIMPLE_MOVING_AVERAGE","220.00",null)))
        reopened.resume()
        assertTrue(reopened.state.value.loading)
        assertEquals("200.00",reopened.state.value.data?.methods?.first()?.forecastPln)
        pending.complete(Unit)
        runCurrent()
        assertEquals("220.00",reopened.state.value.data?.methods?.first()?.forecastPln)
        assertEquals(3,source.forecastCalls)
    }
    @Test fun invalidDatesPreventNetworkAndPreserveDraft() {
        val source=Source(); val vm=vm(source); vm.from("2026-11-02"); vm.to("2026-11-01"); vm.export()
        assertEquals(0,source.exports); assertTrue(vm.state.value.fields.containsKey("dateTo")); assertEquals("2026-11-02",vm.state.value.from)
    }
    @Test fun repeatedExportTapHasSingleTransferAndSingleConsumableShare()=runTest {
        val source=Source(); source.hold=CompletableDeferred(); val vm=vm(source); vm.from("2026-01-01"); vm.to("2026-10-31")
        vm.export(); vm.export(); assertEquals(1,source.exports); assertTrue(vm.state.value.exporting)
        source.hold!!.complete(Unit); runCurrent(); assertNotNull(vm.state.value.share); vm.export(); assertEquals(1,source.exports)
        vm.shared(); assertNull(vm.state.value.share)
    }
    @Test fun failedExportHasNoShareAndCanBeRetried() {
        val source=Source(); source.fail=true; val vm=vm(source); vm.from("2026-01-01"); vm.to("2026-10-31"); vm.export()
        assertNull(vm.state.value.share); assertFalse(vm.state.value.exporting); assertNotNull(vm.state.value.exportFailure)
        source.fail=false; vm.export(); assertNotNull(vm.state.value.share); assertEquals(2,source.exports)
    }
    @Test fun jsonPreservesLargeDecimalsAndNullableMae() {
        val json=moshi.adapter(ForecastDto::class.java).toJson(data()).replace("200.00","9999999999.99")
        val parsed=moshi.adapter(ForecastDto::class.java).fromJson(json)!!
        assertEquals("9999999999.99",parsed.methods.first().forecastPln); assertNull(parsed.methods.first().maePln)
    }
    @Test fun csvCopyPreservesBomUnicodeQuotesAndCrLf() {
        val bytes="\uFEFF\"date\",\"title\"\r\n\"2026-01-01\",\"Масло, фильтр\"\r\n".toByteArray(Charsets.UTF_8)
        val file=temporary.newFile(); writeCsvExport(bytes.inputStream(),file); assertArrayEquals(bytes,file.readBytes())
    }
    @Test fun invalidAndOversizedCsvNeverLeavePartialFiles() {
        for(bytes in listOf("{\"error\":1}".toByteArray(),"\uFEFF123456789".toByteArray(Charsets.UTF_8))) {
            val file=temporary.newFile()
            try { writeCsvExport(bytes.inputStream(),file,6); fail("Expected invalid response") } catch(ex: InvalidCsvExport) { assertFalse(file.exists()) }
        }
    }
    @Test fun cancellationDuringCopyDeletesPartialFile() {
        val file=temporary.newFile()
        try { writeCsvExport("\uFEFFabc".toByteArray().inputStream(),file) { throw CancellationException("cancelled") }; fail("Expected cancellation") }
        catch(ex: CancellationException) { assertFalse(file.exists()) }
    }
    @Test fun forecastApiCarriesHistoryAndTimeZone()=runTest {
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setHeader("Content-Type","application/json").setBody(moshi.adapter(ForecastDto::class.java).toJson(data())))
            val api=Retrofit.Builder().baseUrl(server.url("/api/v1/")).addConverterFactory(MoshiConverterFactory.create(moshi)).build().create(ForecastApi::class.java)
            assertEquals("2026-12",api.forecast(7,24,"Europe/Warsaw").targetMonth)
            val request=server.takeRequest(); assertEquals("/api/v1/vehicles/7/forecast",request.requestUrl!!.encodedPath)
            assertEquals("24",request.requestUrl!!.queryParameter("historyMonths")); assertEquals("Europe/Warsaw",request.requestUrl!!.queryParameter("timeZone"))
        }
    }
    @Test fun csvApiStreamsExactBytesWithInclusiveDates()=runTest {
        MockWebServer().use { server ->
            val bytes="\uFEFF\"date\",\"amount_pln\"\r\n".toByteArray(Charsets.UTF_8)
            server.enqueue(MockResponse().setHeader("Content-Type","text/csv;charset=UTF-8").setBody(okio.Buffer().write(bytes)))
            val api=Retrofit.Builder().baseUrl(server.url("/api/v1/")).addConverterFactory(MoshiConverterFactory.create(moshi)).build().create(ForecastApi::class.java)
            api.export(7,"2026-01-01","2026-10-31","Europe/Warsaw").use { assertArrayEquals(bytes,it.bytes()) }
            val request=server.takeRequest(); assertEquals("/api/v1/vehicles/7/expenses/export.csv",request.requestUrl!!.encodedPath)
            assertEquals("2026-01-01",request.requestUrl!!.queryParameter("dateFrom")); assertEquals("2026-10-31",request.requestUrl!!.queryParameter("dateTo"))
        }
    }
}
