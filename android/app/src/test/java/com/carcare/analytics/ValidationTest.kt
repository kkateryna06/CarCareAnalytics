package com.carcare.analytics

import com.carcare.analytics.feature.auth.validateCredentials
import com.carcare.analytics.feature.vehicles.validateVehicle
import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate

class ValidationTest {
    private val today = LocalDate.of(2026, 11, 11)
    private fun valid() = mapOf("make" to "Toyota", "model" to "Corolla", "year" to "2020", "fuelType" to "HYBRID", "currentOdometerKm" to "123000")
    @Test fun acceptsValidMinimalVehicle() { assertTrue(validateVehicle(valid(), today).isEmpty()) }
    @Test fun rejectsImpossibleYearAndOverflowedOdometer() {
        val errors = validateVehicle(valid() + mapOf("year" to "2028", "currentOdometerKm" to "99999999999"), today)
        assertTrue(errors.containsKey("year")); assertTrue(errors.containsKey("currentOdometerKm"))
    }
    @Test fun acceptsNextModelYearAndZeroMileage() { assertTrue(validateVehicle(valid() + mapOf("year" to "2027", "currentOdometerKm" to "0"), today).isEmpty()) }
    @Test fun rejectsInvalidVinAndFutureDate() {
        val errors = validateVehicle(valid() + mapOf("vin" to "IIIIIIIIIIIIIIIII", "purchaseDate" to "2026-11-12"), today)
        assertTrue(errors.containsKey("vin")); assertTrue(errors.containsKey("purchaseDate"))
    }
    @Test fun validatesDecimalCapacityWithoutFloatingPoint() {
        assertTrue(validateVehicle(valid() + ("engineCapacityLiters" to "1,80"), today).isEmpty())
        for (value in listOf("0", "20.01", "1.234", "bad")) assertTrue(validateVehicle(valid() + ("engineCapacityLiters" to value), today).containsKey("engineCapacityLiters"))
    }
    @Test fun unicodePasswordLimitUsesUtf8Bytes() {
        assertTrue(validateCredentials("me@example.com", "я".repeat(36), "я".repeat(36), true).isEmpty())
        assertTrue(validateCredentials("me@example.com", "я".repeat(37), "я".repeat(37), true).containsKey("password"))
    }
    @Test fun requiresMatchingConfirmationOnlyForRegistration() {
        assertTrue(validateCredentials("me@example.com", "password123", "", true).containsKey("confirmation"))
        assertTrue(validateCredentials("me@example.com", "password123", "", false).isEmpty())
    }
}
