package com.carcare.analytics

import com.carcare.analytics.feature.logbook.*
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.test.runTest
import okhttp3.mockwebserver.*
import org.junit.*
import org.junit.Assert.*
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory

class JournalApiContractTest {
    private val server=MockWebServer()
    private lateinit var api: JournalApi
    @Before fun setup() {
        server.start(); val moshi=Moshi.Builder().addLast(KotlinJsonAdapterFactory()).build()
        api=Retrofit.Builder().baseUrl(server.url("/api/v1/")).addConverterFactory(MoshiConverterFactory.create(moshi).withNullSerialization()).build().create(JournalApi::class.java)
    }
    @After fun cleanup() { server.shutdown() }
    @Test fun preservesMoneyInTimelineWithoutDoubleConversion() = runTest {
        server.enqueue(MockResponse().setBody("""{"content":[{"id":1,"kind":"EXPENSE","date":"2026-11-11","title":"Parking","amountPln":9999999999.99,"odometerKm":null,"category":"PARKING"}],"page":0,"size":20,"totalElements":1,"totalPages":1}"""))
        assertEquals("9999999999.99",api.timeline(7,0,"EXPENSE","PARKING","2026-11-01","2026-11-30").content.single().amountPln)
        val path=server.takeRequest().path.orEmpty(); assertTrue(path.contains("kind=EXPENSE")); assertTrue(path.contains("dateFrom=2026-11-01"))
    }
    @Test fun creationIncludesIdempotencyKeyAndDecimalString() = runTest {
        server.enqueue(MockResponse().setBody("""{"id":1,"vehicleId":7}"""))
        api.create(7,"refuelings",mapOf("totalCostPln" to "150.25","station" to null),"stable-key")
        val request=server.takeRequest(); assertEquals("stable-key",request.getHeader("Idempotency-Key"))
        val body=request.body.readUtf8(); assertTrue(body.contains("\"150.25\"")); assertTrue(body.contains("\"station\":null"))
    }
    @Test fun readsDocumentActiveStatusAndType() = runTest {
        server.enqueue(MockResponse().setBody("""{"content":[{"id":2,"vehicleId":7,"type":"INSURANCE","validUntil":"2026-11-18","active":true,"status":"UPCOMING"}],"page":0,"size":20,"totalElements":1,"totalPages":1}"""))
        val document=api.documents(7,0,"INSURANCE").content.single()
        assertTrue(document.active); assertEquals(DocumentType.INSURANCE,document.type); assertEquals("UPCOMING",document.status)
    }
}
