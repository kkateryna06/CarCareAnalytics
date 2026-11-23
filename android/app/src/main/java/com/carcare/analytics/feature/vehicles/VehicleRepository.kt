package com.carcare.analytics.feature.vehicles

import com.carcare.analytics.core.network.*
import javax.inject.Inject
import com.carcare.analytics.feature.reminders.ReminderScheduler

class VehicleRepository @Inject constructor(private val api: CarCareApi,private val reminders: ReminderScheduler? = null,private val cache: ScreenReadCache? = null) {
    suspend fun list(page: Int) = api.vehicles(page)
    suspend fun get(id: Long) = api.vehicle(id)
    suspend fun save(id: Long?, data: VehicleWrite, photoBase64: String? = null, removePhoto: Boolean = false): VehicleDto {
        val saved=if(id==null && photoBase64==null) api.createVehicle(data) else {
            val body=mutableMapOf<String,Any?>("make" to data.make,"model" to data.model,"year" to data.year,"fuelType" to data.fuelType.name,
                "generation" to data.generation,"engineCapacityLiters" to data.engineCapacityLiters,"vin" to data.vin,
                "registrationNumber" to data.registrationNumber,"purchaseDate" to data.purchaseDate)
            if(photoBase64!=null || removePhoto) body["photoBase64"]=photoBase64
            if(id==null) {
                body["currentOdometerKm"]=data.currentOdometerKm
                api.createVehicleWithPhoto(body)
            } else api.updateVehicle(id,body)
        }
        cache?.garage?.invalidate()
        if(id!=null) cache?.invalidate(id)
        return saved
    }
    suspend fun delete(id: Long) {
        api.deleteVehicle(id)
        cache?.garage?.invalidate(discard=true)
        cache?.invalidate(id,discard=true)
        reminders?.vehicleDeleted(id)
    }
}
