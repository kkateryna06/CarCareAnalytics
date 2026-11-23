package com.carcare.analytics

import androidx.lifecycle.SavedStateHandle
import com.carcare.analytics.core.network.*
import com.carcare.analytics.feature.vehicles.*
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.*
import kotlinx.coroutines.test.*
import kotlinx.coroutines.flow.first
import okhttp3.mockwebserver.*
import org.junit.*
import org.junit.Assert.*
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory

@OptIn(ExperimentalCoroutinesApi::class)
class VehicleFormStateTest {
    private val dispatcher = UnconfinedTestDispatcher()
    private val server = MockWebServer()
    private lateinit var repository: VehicleRepository
    private lateinit var errors: ErrorMapper
    @Before fun setup() {
        Dispatchers.setMain(dispatcher)
        server.start()
        val moshi = Moshi.Builder().addLast(KotlinJsonAdapterFactory()).build()
        repository = VehicleRepository(Retrofit.Builder().baseUrl(server.url("/api/v1/"))
            .addConverterFactory(MoshiConverterFactory.create(moshi).withNullSerialization()).build().create(CarCareApi::class.java))
        errors = ErrorMapper(moshi)
    }
    @After fun cleanup() { server.shutdown(); Dispatchers.resetMain() }
    @Test fun restoresDraftFieldsFromSavedState() {
        val saved = SavedStateHandle(mapOf("vehicleId" to "new"))
        val first = VehicleFormViewModel(repository, errors, saved)
        first.change("make", "Toyota"); first.change("model", "Corolla"); first.change("year", "2020")
        val restoredHandle = SavedStateHandle(saved.keys().associateWith { saved.get<Any?>(it) })
        val restored = VehicleFormViewModel(repository, errors, restoredHandle)
        assertEquals("Toyota", restored.state.value.values["make"])
        assertEquals("2020", restored.state.value.values["year"])
        assertFalse(restored.state.value.loading)
    }
    @Test fun failedSavePreservesDraftForRetry() = runTest(dispatcher) {
        server.enqueue(MockResponse().setResponseCode(503).setBody("{}"))
        val vm = VehicleFormViewModel(repository, errors, SavedStateHandle(mapOf("vehicleId" to "new")))
        vm.change("make", "Toyota"); vm.change("model", "Corolla"); vm.change("year", "2020")
        vm.save()
        withContext(Dispatchers.Default) { withTimeout(5000) { vm.state.first { !it.saving } } }
        assertEquals("Toyota", vm.state.value.values["make"])
        assertNotNull(vm.state.value.failure)
        assertFalse(vm.state.value.done)
    }
}
