package com.carcare.analytics

import com.carcare.analytics.core.network.*
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.*
import org.junit.*
import org.junit.Assert.*
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory

class ApiContractTest {
    private val server = MockWebServer()
    private lateinit var api: CarCareApi
    @Before fun setup() {
        server.start()
        val moshi = Moshi.Builder().addLast(KotlinJsonAdapterFactory()).build()
        api = Retrofit.Builder().baseUrl(server.url("/api/v1/")).client(OkHttpClient())
            .addConverterFactory(MoshiConverterFactory.create(moshi).withNullSerialization()).build().create(CarCareApi::class.java)
    }
    @After fun cleanup() { server.shutdown() }
    @Test fun readsServerDecimalNumberAsExactString() = runTest {
        server.enqueue(MockResponse().setBody("""{"id":1,"make":"Toyota","model":"Corolla","year":2020,"generation":null,"fuelType":"HYBRID","engineCapacityLiters":1.80,"vin":null,"registrationNumber":null,"purchaseDate":null,"currentOdometerKm":0,"createdAt":"2026-11-11T12:00:00Z","updatedAt":"2026-11-11T12:00:00Z"}"""))
        assertEquals("1.80", api.vehicle(1).engineCapacityLiters)
        assertEquals("/api/v1/vehicles/1", server.takeRequest().path)
    }
    @Test fun editSendsExplicitNullToClearOptionalFields() = runTest {
        server.enqueue(MockResponse().setResponseCode(204))
        // Inspect serialized payload even though 204 has no response body.
        runCatching { api.updateVehicle(1, mapOf("make" to "Toyota","vin" to null,"purchaseDate" to null)) }
        val request = server.takeRequest()
        assertEquals("PATCH", request.method)
        val body = request.body.readUtf8()
        assertTrue(body.contains("\"vin\":null")); assertTrue(body.contains("\"purchaseDate\":null"))
    }
}
