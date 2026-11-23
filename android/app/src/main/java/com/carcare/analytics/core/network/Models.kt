package com.carcare.analytics.core.network

data class AuthRequest(val email: String, val password: String)
data class UserDto(val id: Long, val email: String, val createdAt: String)
data class AuthResponse(val accessToken: String, val tokenType: String, val expiresAt: String, val user: UserDto)
data class ApiError(val code: String, val message: String, val fieldErrors: Map<String, String> = emptyMap())
enum class FuelType { PETROL, DIESEL, LPG, HYBRID, ELECTRIC, OTHER }
data class VehicleDto(
    val id: Long, val make: String, val model: String, val year: Int,
    val generation: String?, val fuelType: FuelType, val engineCapacityLiters: String?,
    val vin: String?, val registrationNumber: String?, val purchaseDate: String?,
    val currentOdometerKm: Int, val createdAt: String, val updatedAt: String, val photoVersion: String? = null
)
data class VehicleWrite(
    val make: String, val model: String, val year: Int, val fuelType: FuelType,
    val currentOdometerKm: Int, val generation: String? = null, val engineCapacityLiters: String? = null,
    val vin: String? = null, val registrationNumber: String? = null, val purchaseDate: String? = null
)
data class VehiclePage(val content: List<VehicleDto>, val page: Int, val size: Int, val totalElements: Long, val totalPages: Int)
