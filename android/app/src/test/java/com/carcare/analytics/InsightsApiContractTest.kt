package com.carcare.analytics

import com.carcare.analytics.feature.insights.*
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.test.runTest
import okhttp3.mockwebserver.*
import org.junit.*
import org.junit.Assert.*
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory

class InsightsApiContractTest {
    private val server=MockWebServer(); private lateinit var api: InsightsApi
    @Before fun setup() { server.start(); val moshi=Moshi.Builder().addLast(KotlinJsonAdapterFactory()).build(); api=Retrofit.Builder().baseUrl(server.url("/api/v1/")).addConverterFactory(MoshiConverterFactory.create(moshi)).build().create(InsightsApi::class.java) }
    @After fun cleanup() { server.shutdown() }
    @Test fun reminderRequestPassesTimezoneAndCompletionVisibility() = runTest {
        server.enqueue(MockResponse().setBody("""{"asOfDate":"2026-11-12","timeZone":"Europe/Warsaw","currentOdometerKm":1000,"content":[]}"""))
        api.reminders(7,"Europe/Warsaw",true); val url=checkNotNull(server.takeRequest().requestUrl)
        assertEquals("Europe/Warsaw",url.queryParameter("timeZone")); assertEquals("true",url.queryParameter("includeDone"))
    }
    @Test fun completingReminderDoesNotPatchServiceCostOrDate() = runTest {
        server.enqueue(MockResponse().setResponseCode(204)); api.complete(3,mapOf("done" to true)); val request=server.takeRequest()
        assertEquals("PATCH",request.method); assertEquals("/api/v1/services/3/reminder",request.path); assertEquals("{\"done\":true}",request.body.readUtf8())
    }
    @Test fun cacheJsonPayloadCanRoundTripWithoutLosingRules() {
        val moshi=Moshi.Builder().addLast(KotlinJsonAdapterFactory()).build()
        val dto=ReminderCache(5,listOf(CachedVehicleReminders(7,"Toyota",RemindersDto("2026-11-12","Europe/Warsaw",1000,listOf(ReminderDto("DOCUMENT:1:2026-11-19:null","DOCUMENT",1,"INSURANCE","OC","2026-11-19",null,7,null,ReminderStatus.UPCOMING,"D7","key"))),"2026-11-12T00:00:00Z")))
        val adapter=moshi.adapter(ReminderCache::class.java); assertEquals(dto,adapter.fromJson(adapter.toJson(dto)))
    }
}
