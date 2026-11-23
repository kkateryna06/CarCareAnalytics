package com.carcare.analytics.feature.reminders

import androidx.lifecycle.*
import com.carcare.analytics.core.network.*
import com.carcare.analytics.feature.insights.*
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject

data class RemindersState(val data: CachedVehicleReminders? = null,val includeDone: Boolean = false,val loading: Boolean = true,val busy: Boolean = false,val stale: Boolean = false,val failure: Failure? = null)
@HiltViewModel
class RemindersViewModel @Inject constructor(private val source: InsightsDataSource,private val errors: ErrorMapper,private val saved: SavedStateHandle,private val cache: ScreenReadCache? = null): ViewModel() {
    val vehicleId=checkNotNull(saved.get<String>("vehicleId")).toLong()
    private fun query(): RemindersQuery {
        val zone=ZoneId.systemDefault()
        return RemindersQuery(LocalDate.now(zone),zone.id)
    }
    private var shownQuery=query()
    private val initial=cache?.reminders?.snapshot(vehicleId,shownQuery)?.value
    private val _state=MutableStateFlow(RemindersState(data=initial,includeDone=saved["include_done"]?:false,loading=initial==null))
    val state=_state.asStateFlow()
    private var job: Job?=null
    fun showDone(value: Boolean) {
        saved["include_done"]=value
        _state.update { it.copy(includeDone=value) }
    }
    fun resume() = load(force=false)
    fun refresh() = load(force=true)

    private fun load(force: Boolean) {
        if(_state.value.busy) return
        job?.cancel()
        val query=query()
        val snapshot=cache?.reminders?.snapshot(vehicleId,query)
        val sameQuery=shownQuery==query
        val displayed=snapshot?.value ?: _state.value.data.takeIf { sameQuery }
        shownQuery=query
        val needsRefresh=force || snapshot?.fresh!=true
        _state.update { it.copy(data=displayed,loading=needsRefresh,failure=null,stale=needsRefresh && sameQuery && it.stale) }
        if(!needsRefresh) return
        job=viewModelScope.launch {
            try {
                val fetch: suspend () -> CachedVehicleReminders = { source.reminders(vehicleId,true) }
                val data=cache?.reminders?.fetch(vehicleId,query,load=fetch) ?: fetch()
                _state.update { it.copy(data=data,loading=false,stale=false) }
            }
            catch(ex: Exception) {
                val failure=errors.map(ex)
                val cached=_state.value.data ?: try { source.cached(vehicleId,true) }
                    catch(cacheError: Exception) { if(cacheError is kotlinx.coroutines.CancellationException) throw cacheError; null }
                _state.update { it.copy(data=cached,loading=false,stale=cached!=null,failure=failure) }
            }
        }
    }
    fun complete(id: Long,done: Boolean) {
        if(_state.value.busy) return
        job?.cancel(); _state.update { it.copy(busy=true,failure=null,loading=false) }
        viewModelScope.launch {
            try { source.complete(vehicleId,id,done); _state.update { it.copy(busy=false) }; refresh() }
            catch(ex: Exception) { val failure=errors.map(ex); _state.update { it.copy(busy=false,failure=failure) } }
        }
    }
}
