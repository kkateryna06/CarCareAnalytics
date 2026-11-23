package com.carcare.analytics.feature.forecast

import androidx.lifecycle.*
import com.carcare.analytics.R
import com.carcare.analytics.core.network.*
import com.carcare.analytics.feature.insights.validateAnalyticsPeriod
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject

data class ForecastState(val historyMonths: Int,val from: String,val to: String,val data: ForecastDto?=null,val loading: Boolean=false,
    val stale: Boolean=false,val failure: Failure?=null,val exporting: Boolean=false,val exportFailure: Failure?=null,
    val fields: Map<String,Int> = emptyMap(),val share: CsvArtifact?=null,val vehicle: VehicleDto?=null)
internal val forecastHistoryOptions=listOf(6,12,24,60)
@HiltViewModel
class ForecastViewModel @Inject constructor(private val source: ForecastDataSource,private val errors: ErrorMapper,private val saved: SavedStateHandle,private val cache: ScreenReadCache? = null): ViewModel() {
    val vehicleId=checkNotNull(saved.get<String>("vehicleId")).toLong()
    private val initialMonths=saved["history_months"]?:12
    private val initial=cache?.forecasts?.snapshot(vehicleId,query(initialMonths))?.value
    private val _state=MutableStateFlow(ForecastState(initialMonths,saved["export_from"]?:LocalDate.now().withDayOfYear(1).toString(),saved["export_to"]?:LocalDate.now().toString(),
        data=initial,loading=initial==null,vehicle=cache?.forecastVehicles?.snapshot(vehicleId,Unit)?.value))
    val state=_state.asStateFlow(); private var forecastLoad: Job?=null
    private var vehicleLoad: Job?=null
    private fun query(months: Int): ForecastQuery {
        val zone=ZoneId.systemDefault()
        return ForecastQuery(months,LocalDate.now(zone),zone.id)
    }

    fun history(months: Int) {
        if(months !in forecastHistoryOptions || _state.value.historyMonths==months) return
        saved["history_months"]=months
        _state.update { it.copy(historyMonths=months) }
        loadForecast(force=false)
    }

    fun resume() {
        loadVehicle()
        loadForecast(force=false)
    }

    fun refresh() {
        loadVehicle()
        loadForecast(force=true)
    }

    private fun loadVehicle() {
        val snapshot=cache?.forecastVehicles?.snapshot(vehicleId,Unit)
        snapshot?.let { entry -> _state.update { it.copy(vehicle=entry.value) } }
        if(snapshot?.fresh==true || (cache==null && _state.value.vehicle!=null)) return
        vehicleLoad?.cancel()
        vehicleLoad=viewModelScope.launch {
            try {
                val fetch: suspend () -> VehicleDto = { source.vehicle(vehicleId) }
                val vehicle=cache?.forecastVehicles?.fetch(vehicleId,Unit,load=fetch) ?: fetch()
                _state.update { it.copy(vehicle=vehicle) }
            }
            catch(cancelled: CancellationException) { throw cancelled }
            catch(_: Exception) { /* The forecast remains usable even if vehicle details fail to load. */ }
        }
    }

    private fun loadForecast(force: Boolean) {
        forecastLoad?.cancel()
        val query=query(_state.value.historyMonths)
        val snapshot=cache?.forecasts?.snapshot(vehicleId,query)
        val needsRefresh=force || snapshot?.fresh!=true
        _state.update { it.copy(data=snapshot?.value ?: it.data,loading=needsRefresh,failure=null,stale=false) }
        if(!needsRefresh) return
        forecastLoad=viewModelScope.launch {
            try {
                val fetch: suspend () -> ForecastDto = { source.forecast(vehicleId,query.months) }
                val data=cache?.forecasts?.fetch(vehicleId,query,load=fetch) ?: fetch()
                _state.update { it.copy(data=data,loading=false,stale=false) }
            }
            catch(ex: Exception) { val failure=errors.map(ex); _state.update { it.copy(loading=false,stale=it.data!=null,failure=failure) } }
        }
    }
    fun from(value: String) { saved["export_from"]=value; _state.update { it.copy(from=value,fields=emptyMap(),exportFailure=null) } }
    fun to(value: String) { saved["export_to"]=value; _state.update { it.copy(to=value,fields=emptyMap(),exportFailure=null) } }
    fun export() {
        val s=_state.value; if(s.exporting || s.share!=null) return
        val fields=validateAnalyticsPeriod(s.from,s.to); if(fields.isNotEmpty()) { _state.update { it.copy(fields=fields) }; return }
        _state.update { it.copy(exporting=true,exportFailure=null) }
        viewModelScope.launch {
            try { val file=source.export(vehicleId,s.from,s.to); _state.update { it.copy(exporting=false,share=file) } }
            catch(ex: Exception) { val failure=if(ex is InvalidCsvExport) Failure(R.string.error_csv_response) else errors.map(ex); _state.update { it.copy(exporting=false,exportFailure=failure) } }
        }
    }
    fun shared() { _state.update { it.copy(share=null) } }
    fun shareFailed() { _state.update { it.copy(share=null,exportFailure=Failure(R.string.error_csv_share)) } }
}
