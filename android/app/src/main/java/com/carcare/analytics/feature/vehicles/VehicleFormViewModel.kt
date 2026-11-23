package com.carcare.analytics.feature.vehicles

import android.net.Uri
import androidx.lifecycle.*
import com.carcare.analytics.R
import com.carcare.analytics.core.network.*
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.CancellationException
import java.math.BigDecimal
import java.time.*
import java.util.Locale
import javax.inject.Inject

val vehicleFields = listOf("make", "model", "year", "fuelType", "currentOdometerKm", "generation", "engineCapacityLiters", "vin", "registrationNumber", "purchaseDate")
data class VehicleFormState(val values: Map<String, String>, val loading: Boolean = false, val loaded: Boolean = true, val saving: Boolean = false, val done: Boolean = false,
    val fields: Map<String, Int> = emptyMap(), val failure: Failure? = null, val photoVersion: String? = null,
    val photoDraft: String? = null, val photoRemoved: Boolean = false, val preparingPhoto: Boolean = false, val photoError: Int? = null)

fun validateVehicle(values: Map<String, String>, today: LocalDate = LocalDate.now()): Map<String, Int> = buildMap {
    fun v(key: String) = values[key].orEmpty().trim()
    for (key in listOf("make", "model")) {
        if (v(key).isBlank()) put(key, R.string.error_required)
        else if (v(key).length > 80) put(key, R.string.error_length)
    }
    if (v("year").toIntOrNull()?.let { it in 1886..(today.year + 1) } != true) put("year", R.string.error_year)
    if (v("currentOdometerKm").toIntOrNull()?.let { it >= 0 } != true) put("currentOdometerKm", R.string.error_odometer)
    val engine = v("engineCapacityLiters").replace(',', '.')
    if (engine.isNotEmpty() && (engine.toBigDecimalOrNull()?.let { it > BigDecimal.ZERO && it <= BigDecimal("20") && it.scale() <= 2 } != true)) put("engineCapacityLiters", R.string.error_engine)
    if (v("vin").isNotEmpty() && !v("vin").uppercase(Locale.ROOT).matches(Regex("[A-HJ-NPR-Z0-9]{17}"))) put("vin", R.string.error_vin)
    if (v("registrationNumber").length > 20) put("registrationNumber", R.string.error_length)
    if (v("generation").length > 80) put("generation", R.string.error_length)
    if (v("purchaseDate").isNotEmpty() && runCatching { !LocalDate.parse(v("purchaseDate")).isAfter(today) }.getOrDefault(false).not()) put("purchaseDate", R.string.error_date)
    if (runCatching { FuelType.valueOf(v("fuelType")) }.isFailure) put("fuelType", R.string.error_required)
}

@HiltViewModel
class VehicleFormViewModel @Inject constructor(private val repository: VehicleRepository, private val errors: ErrorMapper, private val saved: SavedStateHandle, private val photos: VehiclePhotoImages? = null) : ViewModel() {
    val id: Long? = saved.get<String>("vehicleId")?.takeUnless { it == "new" }?.toLongOrNull()
    private val _state = MutableStateFlow(VehicleFormState(vehicleFields.associateWith { saved.get<String>("field_$it") ?: when(it) { "fuelType" -> "PETROL"; "currentOdometerKm" -> "0"; else -> "" } }, loading = id != null && saved.get<Boolean>("loaded") != true, loaded = id == null || saved.get<Boolean>("loaded") == true,
        photoVersion=saved["photo_version"],photoDraft=saved["photo_draft"],photoRemoved=saved["photo_removed"]?:false))
    val state = _state.asStateFlow()
    init { if (_state.value.loading) load() }
    fun change(field: String, value: String) {
        saved["field_$field"] = value
        _state.update { it.copy(values = it.values + (field to value), fields = it.fields - field, failure = null) }
    }
    fun choosePhoto(uri: Uri) {
        val images=photos ?: return
        val state=_state.value
        if(!state.loaded || state.saving || state.done || state.preparingPhoto) return
        _state.update { it.copy(preparingPhoto=true,photoError=null,failure=null) }
        viewModelScope.launch {
            try {
                val draft=images.importPhoto(uri)
                images.discardDraft(_state.value.photoDraft)
                saved["photo_draft"]=draft
                saved["photo_removed"]=false
                _state.update { it.copy(photoDraft=draft,photoRemoved=false,preparingPhoto=false) }
            } catch(cancelled: CancellationException) { throw cancelled }
            catch(_: Exception) { _state.update { it.copy(preparingPhoto=false,photoError=R.string.vehicle_photo_error) } }
        }
    }
    fun removePhoto() {
        val state=_state.value
        if(state.saving || state.preparingPhoto) return
        photos?.discardDraft(state.photoDraft)
        val removed=state.photoVersion!=null
        saved.remove<String>("photo_draft")
        saved["photo_removed"]=removed
        _state.update { it.copy(photoDraft=null,photoRemoved=removed,photoError=null,failure=null) }
    }
    fun load() {
        val vehicleId = id ?: return
        _state.update { it.copy(loading = true, failure = null) }
        viewModelScope.launch {
            try {
                val v = repository.get(vehicleId)
                val values = mapOf("make" to v.make, "model" to v.model, "year" to v.year.toString(), "fuelType" to v.fuelType.name,
                    "currentOdometerKm" to v.currentOdometerKm.toString(), "generation" to v.generation.orEmpty(), "engineCapacityLiters" to v.engineCapacityLiters.orEmpty(),
                    "vin" to v.vin.orEmpty(), "registrationNumber" to v.registrationNumber.orEmpty(), "purchaseDate" to v.purchaseDate.orEmpty())
                values.forEach { (key, value) -> saved["field_$key"] = value }; saved["loaded"] = true
                saved["photo_version"]=v.photoVersion
                _state.update { it.copy(values = values, loading = false, loaded = true,photoVersion=v.photoVersion) }
            } catch (error: Exception) { val failure = errors.map(error); _state.update { it.copy(loading = false, failure = failure) } }
        }
    }
    fun save() {
        val input = _state.value
        if (input.saving || input.loading || input.preparingPhoto || (id != null && saved.get<Boolean>("loaded") != true)) return
        val fields = validateVehicle(input.values)
        if (fields.isNotEmpty()) { _state.update { it.copy(fields = fields) }; return }
        fun v(key: String) = input.values[key].orEmpty().trim()
        fun optional(key: String) = v(key).takeIf(String::isNotEmpty)
        val request = VehicleWrite(v("make"), v("model"), v("year").toInt(), FuelType.valueOf(v("fuelType")), v("currentOdometerKm").toInt(),
            optional("generation"), optional("engineCapacityLiters")?.replace(',', '.'), optional("vin")?.uppercase(Locale.ROOT), optional("registrationNumber"), optional("purchaseDate"))
        _state.update { it.copy(saving = true, failure = null) }
        viewModelScope.launch {
            val photo=try { input.photoDraft?.let { checkNotNull(photos).encodedDraft(it) } }
            catch(cancelled: CancellationException) { throw cancelled }
            catch(_: Exception) {
                _state.update { it.copy(saving=false,photoError=R.string.vehicle_photo_error) }
                return@launch
            }
            try { repository.save(id,request,photo,input.photoRemoved); _state.update { it.copy(saving = false, done = true) } }
            catch (error: Exception) { val failure = errors.map(error); _state.update { it.copy(saving = false, failure = failure) } }
        }
    }

    override fun onCleared() {
        photos?.discardDraft(_state.value.photoDraft)
        super.onCleared()
    }
}
