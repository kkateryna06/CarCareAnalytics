package com.carcare.analytics.feature.forecast

import okhttp3.ResponseBody
import retrofit2.http.*

interface ForecastApi {
    @GET("vehicles/{vehicleId}/forecast")
    suspend fun forecast(@Path("vehicleId") vehicle: Long,@Query("historyMonths") historyMonths: Int,@Query("timeZone") zone: String): ForecastDto
    @Streaming @GET("vehicles/{vehicleId}/expenses/export.csv")
    suspend fun export(@Path("vehicleId") vehicle: Long,@Query("dateFrom") from: String,@Query("dateTo") to: String,@Query("timeZone") zone: String): ResponseBody
}
