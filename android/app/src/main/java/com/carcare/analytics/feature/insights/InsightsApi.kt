package com.carcare.analytics.feature.insights

import retrofit2.http.*

interface InsightsApi {
    @GET("vehicles/{vehicleId}/analytics/summary")
    suspend fun summary(@Path("vehicleId") vehicleId: Long, @Query("dateFrom") from: String, @Query("dateTo") to: String): SummaryDto

    @GET("vehicles/{vehicleId}/analytics/dashboard") suspend fun dashboard(@Path("vehicleId") vehicleId: Long,@Query("dateFrom") from: String,@Query("dateTo") to: String,@Query("timeZone") timeZone: String): DashboardDto
    @GET("vehicles/{vehicleId}/analytics/monthly")
    suspend fun monthly(
        @Path("vehicleId") vehicleId: Long,
        @Query("dateFrom") from: String,
        @Query("dateTo") to: String,
        @Query("timeZone") timeZone: String
    ): MonthlyDto
    @GET("vehicles/{vehicleId}/reminders") suspend fun reminders(@Path("vehicleId") vehicleId: Long,@Query("timeZone") timeZone: String,@Query("includeDone") includeDone: Boolean = false): RemindersDto
    @PATCH("services/{id}/reminder") suspend fun complete(@Path("id") id: Long,@Body value: Map<String,Boolean>)
}
