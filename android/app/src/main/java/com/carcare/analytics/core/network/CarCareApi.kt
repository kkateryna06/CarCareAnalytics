package com.carcare.analytics.core.network

import retrofit2.http.*
import okhttp3.ResponseBody

interface CarCareApi {
    @POST("auth/register") suspend fun register(@Body request: AuthRequest): AuthResponse
    @POST("auth/login") suspend fun login(@Body request: AuthRequest): AuthResponse
    @GET("users/me") suspend fun me(): UserDto
    @GET("vehicles") suspend fun vehicles(@Query("page") page: Int, @Query("size") size: Int = 20): VehiclePage
    @GET("vehicles/{id}") suspend fun vehicle(@Path("id") id: Long): VehicleDto
    @POST("vehicles") suspend fun createVehicle(@Body request: VehicleWrite): VehicleDto
    @POST("vehicles") suspend fun createVehicleWithPhoto(@Body request: Map<String,@JvmSuppressWildcards Any?>): VehicleDto
    @Streaming @GET("vehicles/{id}/photo") suspend fun vehiclePhoto(@Path("id") id: Long,@Query("version") version: String): ResponseBody
    @PATCH("vehicles/{id}") suspend fun updateVehicle(@Path("id") id: Long, @Body request: Map<String,@JvmSuppressWildcards Any?>): VehicleDto
    @DELETE("vehicles/{id}") suspend fun deleteVehicle(@Path("id") id: Long)
}
