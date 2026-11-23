package com.carcare.analytics.feature.logbook

import androidx.lifecycle.*
import com.carcare.analytics.core.network.*
import com.carcare.analytics.feature.vehicles.VehicleRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.ZoneOffset
import java.util.UUID
import javax.inject.Inject

data class OperationFormState(val values: Map<String,String>,val loading: Boolean = false,val loaded: Boolean = true,
    val saving: Boolean = false,val done: Boolean = false,val fields: Map<String,Int> = emptyMap(),val failure: Failure? = null)

data class FormVehicleState(val vehicle: VehicleDto? = null,val failure: Failure? = null)

internal fun latestOperationDate(kind: EntryKind): LocalDate {
    val today=LocalDate.now()
    return if(kind==EntryKind.ODOMETER) minOf(today,LocalDate.now(ZoneOffset.UTC)) else today
}

@HiltViewModel
class OperationFormViewModel @Inject constructor(private val repository: JournalRepository,private val errors: ErrorMapper,private val saved: SavedStateHandle,private val vehicles: VehicleRepository): ViewModel() {
    val vehicleId: Long = checkNotNull(saved.get<String>("vehicleId")).toLong()
    val kind: EntryKind = EntryKind.valueOf(checkNotNull(saved.get<String>("kind")))
    val id: Long? = saved.get<String>("recordId")?.toLongOrNull()
    private val restored=id==null || saved.get<Boolean>("operation_loaded")==true
    private val _state=MutableStateFlow(OperationFormState(fieldsFor(kind).associate { field -> field.key to (saved.get<String>("op_${field.key}") ?: when(field.key) {
        "date" -> latestOperationDate(kind).toString(); "validUntil" -> LocalDate.now().toString()
        "category" -> "OTHER"; "serviceType" -> "REPAIR"; "type" -> "INSURANCE"; "isFullTank" -> "false"; else -> ""
    }) },loading=!restored,loaded=restored))
    val state=_state.asStateFlow()
    private val _formVehicle=MutableStateFlow(FormVehicleState())
    val formVehicle=_formVehicle.asStateFlow()
    private var vehicleJob: Job?=null
    init { if(saved.get<String>("request_key")==null) saved["request_key"]=UUID.randomUUID().toString(); if(!restored) load() }
    fun loadFormVehicle() {
        if(vehicleJob?.isActive==true || _formVehicle.value.vehicle!=null) return
        _formVehicle.value=FormVehicleState()
        vehicleJob=viewModelScope.launch {
            try { _formVehicle.value=FormVehicleState(vehicle=vehicles.get(vehicleId)) }
            catch(error: Exception) { _formVehicle.value=FormVehicleState(failure=errors.map(error)) }
        }
    }
    fun change(field: String,value: String) {
        if(_state.value.saving) return
        saved["op_$field"]=value; saved["request_key"]=UUID.randomUUID().toString()
        _state.update { it.copy(values=it.values+(field to value),fields=it.fields-field,failure=null) }
    }
    fun load() {
        val record=id?:return
        _state.update { it.copy(loading=true,failure=null) }
        viewModelScope.launch {
            try {
                val dto=repository.get(kind,record)
                check(dto.vehicleId==vehicleId) { "Vehicle mismatch" }
                val values=dto.fields().filterKeys { key -> fieldsFor(kind).any { it.key==key } }
                values.forEach { (key,value) -> saved["op_$key"]=value }; saved["operation_loaded"]=true
                _state.update { it.copy(values=values,loading=false,loaded=true) }
            } catch(error: Exception) { val failure=errors.map(error); _state.update { it.copy(loading=false,failure=failure) } }
        }
    }
    fun save() {
        val input=_state.value
        if(!input.loaded || input.loading || input.saving) return
        val fields=validateOperation(kind,input.values,latestOperationDate(kind))
        if(fields.isNotEmpty()) { _state.update { it.copy(fields=fields) }; return }
        val payload=operationPayload(kind,input.values)
        val key=checkNotNull(saved.get<String>("request_key"))
        _state.update { it.copy(saving=true,failure=null) }
        viewModelScope.launch {
            try { repository.save(vehicleId,kind,id,payload,key); _state.update { it.copy(saving=false,done=true) } }
            catch(error: Exception) { val failure=errors.map(error); _state.update { it.copy(saving=false,failure=failure) } }
        }
    }
    fun delete() {
        val record=id?:return
        if(!_state.value.loaded || _state.value.loading || _state.value.saving) return
        _state.update { it.copy(saving=true,failure=null) }
        viewModelScope.launch {
            try { repository.delete(vehicleId,kind,record); _state.update { it.copy(saving=false,done=true) } }
            catch(error: Exception) { val failure=errors.map(error); _state.update { it.copy(saving=false,failure=failure) } }
        }
    }

}
