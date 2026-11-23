package com.carcare.analytics.feature.logbook

import retrofit2.http.*

interface JournalApi {
    @GET("vehicles/{vehicleId}/timeline") suspend fun timeline(@Path("vehicleId") vehicleId: Long,@Query("page") page: Int,
        @Query("kind") kind: String?,@Query("category") category: String?,@Query("dateFrom") dateFrom: String?,@Query("dateTo") dateTo: String?,
        @Query("size") size: Int? = null): JournalPage<TimelineDto>
    @GET("vehicles/{vehicleId}/documents") suspend fun documents(@Path("vehicleId") vehicleId: Long,@Query("page") page: Int,@Query("type") type: String?): JournalPage<JournalRecordDto>
    @GET("{resource}/{id}") suspend fun get(@Path("resource") resource: String,@Path("id") id: Long): JournalRecordDto
    @POST("vehicles/{vehicleId}/{resource}") suspend fun create(@Path("vehicleId") vehicleId: Long,@Path("resource") resource: String,@Body data: Map<String,@JvmSuppressWildcards Any?>,@Header("Idempotency-Key") key: String): JournalRecordDto
    @PATCH("{resource}/{id}") suspend fun update(@Path("resource") resource: String,@Path("id") id: Long,@Body data: Map<String,@JvmSuppressWildcards Any?>): JournalRecordDto
    @DELETE("{resource}/{id}") suspend fun delete(@Path("resource") resource: String,@Path("id") id: Long)
}
