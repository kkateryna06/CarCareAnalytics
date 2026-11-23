package com.carcare.analytics

import androidx.lifecycle.SavedStateHandle
import com.carcare.analytics.core.network.ErrorMapper
import com.carcare.analytics.core.network.CarCareApi
import com.carcare.analytics.feature.logbook.*
import com.carcare.analytics.feature.vehicles.VehicleRepository
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.*
import okhttp3.mockwebserver.*
import org.junit.*
import org.junit.Assert.*
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import java.time.LocalDate

@OptIn(ExperimentalCoroutinesApi::class)
class OperationFormStateTest {
    private val dispatcher=UnconfinedTestDispatcher()
    private val server=MockWebServer()
    private lateinit var repository: JournalRepository
    private lateinit var vehicles: VehicleRepository
    private lateinit var errors: ErrorMapper
    @Before fun setup() {
        Dispatchers.setMain(dispatcher); server.start()
        val moshi=Moshi.Builder().addLast(KotlinJsonAdapterFactory()).build(); errors=ErrorMapper(moshi)
        val retrofit=Retrofit.Builder().baseUrl(server.url("/api/v1/")).addConverterFactory(MoshiConverterFactory.create(moshi).withNullSerialization()).build()
        repository=JournalRepository(retrofit.create(JournalApi::class.java))
        vehicles=VehicleRepository(retrofit.create(CarCareApi::class.java))
    }
    @After fun cleanup() { server.shutdown(); Dispatchers.resetMain() }
    private fun handle()=SavedStateHandle(mapOf("vehicleId" to "7","kind" to "EXPENSE","recordId" to "new"))
    private fun fill(vm: OperationFormViewModel) { vm.change("date",LocalDate.now().toString()); vm.change("category","PARKING"); vm.change("amountPln","10,25"); vm.change("title","Парковка") }
    @Test fun draftAndIdempotencyKeySurviveSavedStateRestoration() {
        val saved=handle(); val vm=OperationFormViewModel(repository,errors,saved,vehicles); fill(vm)
        val key=saved.get<String>("request_key"); val restored=SavedStateHandle(saved.keys().associateWith { saved.get<Any?>(it) })
        val recreated=OperationFormViewModel(repository,errors,restored,vehicles)
        assertEquals("10,25",recreated.state.value.values["amountPln"]); assertEquals(key,restored.get<String>("request_key"))
    }
    @Test fun failedSaveRetainsInputAndRetryUsesSameKey() = runTest(dispatcher) {
        server.enqueue(MockResponse().setResponseCode(503).setBody("{}")); server.enqueue(MockResponse().setBody("""{"id":1,"vehicleId":7}"""))
        val saved=handle(); val vm=OperationFormViewModel(repository,errors,saved,vehicles); fill(vm); vm.save()
        withContext(Dispatchers.Default) { withTimeout(5000) { vm.state.first { !it.saving } } }
        assertNotNull(vm.state.value.failure); assertEquals("Парковка",vm.state.value.values["title"])
        vm.save(); withContext(Dispatchers.Default) { withTimeout(5000) { vm.state.first { !it.saving } } }
        assertTrue(vm.state.value.done)
        val first=server.takeRequest(); val second=server.takeRequest(); assertEquals(first.getHeader("Idempotency-Key"),second.getHeader("Idempotency-Key"))
    }
    @Test fun editingPayloadStartsANewIdempotencyKey() {
        val saved=handle(); val vm=OperationFormViewModel(repository,errors,saved,vehicles); val before=saved.get<String>("request_key")
        vm.change("title","Другая запись"); assertNotEquals(before,saved.get<String>("request_key"))
    }
    @Test fun failedDeleteKeepsFormAndRetryDeletesSourceRecord() = runTest(dispatcher) {
        server.enqueue(MockResponse().setResponseCode(503).setBody("{}"))
        server.enqueue(MockResponse().setResponseCode(204))
        val saved=SavedStateHandle(mapOf("vehicleId" to "7","kind" to "REFUELING","recordId" to "42","operation_loaded" to true,"op_liters" to "30"))
        val vm=OperationFormViewModel(repository,errors,saved,vehicles)
        vm.delete()
        withContext(Dispatchers.Default) { withTimeout(5000) { vm.state.first { !it.saving } } }
        assertNotNull(vm.state.value.failure);assertFalse(vm.state.value.done);assertEquals("30",vm.state.value.values["liters"])
        vm.delete()
        withContext(Dispatchers.Default) { withTimeout(5000) { vm.state.first { !it.saving } } }
        assertTrue(vm.state.value.done)
        repeat(2) { val request=server.takeRequest();assertEquals("DELETE",request.method);assertEquals("/api/v1/refuelings/42",request.path) }
    }

}
