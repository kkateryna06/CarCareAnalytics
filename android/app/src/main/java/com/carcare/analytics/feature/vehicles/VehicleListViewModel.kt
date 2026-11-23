package com.carcare.analytics.feature.vehicles

import androidx.lifecycle.*
import com.carcare.analytics.core.network.*
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import retrofit2.HttpException
import javax.inject.Inject

data class VehicleListState(val items: List<VehicleDto> = emptyList(), val loading: Boolean = true,
    val loadingMore: Boolean = false, val nextPage: Int = 0, val totalPages: Int = 0, val deleting: Long? = null, val failure: Failure? = null,
    val activeVehicle: VehicleDto? = null)

@HiltViewModel
class VehicleListViewModel @Inject constructor(private val repository: VehicleRepository, private val errors: ErrorMapper, private val cache: ScreenReadCache? = null) : ViewModel() {
    private var firstPage=cache?.garage?.snapshot(Unit,Unit)?.value
    private val _state = MutableStateFlow(firstPage?.let {
        VehicleListState(items=it.content,loading=false,nextPage=1,totalPages=it.totalPages,activeVehicle=it.content.firstOrNull())
    } ?: VehicleListState())
    val state = _state.asStateFlow()
    private var loadJob: kotlinx.coroutines.Job? = null
    private var preferredId: Long? = null
    private var resolvedPreference: Long? = null

    fun select(id: Long) {
        preferredId=id
        val vehicle=_state.value.items.firstOrNull { it.id==id } ?: return
        resolvedPreference=id
        _state.update { it.copy(activeVehicle=vehicle) }
    }

    fun resume(selectedId: Long? = preferredId) {
        preferredId=selectedId
        val fresh=cache?.garage?.snapshot(Unit,Unit)?.takeIf { it.fresh }?.value
        if(fresh!=null && fresh===firstPage && resolvedPreference==selectedId) return
        load(0,fresh)
    }

    fun refresh(selectedId: Long? = preferredId) {
        preferredId=selectedId
        load(0)
    }

    fun more() { val state = _state.value; if (!state.loadingMore && !state.loading && state.nextPage < state.totalPages) load(state.nextPage) }
    private fun load(page: Int,cachedFirst: VehiclePage? = null) {
        if (_state.value.deleting != null) return
        loadJob?.cancel()
        _state.update { it.copy(loading = page == 0, loadingMore = page > 0, failure = null) }
        loadJob = viewModelScope.launch {
            try {
                val result = cachedFirst ?: if(page==0) {
                    cache?.garage?.fetch(Unit,Unit) { repository.list(0) } ?: repository.list(0)
                } else repository.list(page)
                val requested=preferredId
                val previous=_state.value.activeVehicle?.takeIf { it.id==requested }
                val keepLoadedPages=cachedFirst!=null && cachedFirst===firstPage
                val loadedSelection=_state.value.items.firstOrNull { keepLoadedPages && it.id==requested }
                val resolved=if(page==0) loadedSelection ?: resolveVehicle(result,requested,previous) else null
                if(page==0) {
                    firstPage=result
                    resolvedPreference=requested
                }
                _state.update { state ->
                    val items=when {
                        keepLoadedPages -> state.items
                        page==0 -> result.content
                        else -> (state.items+result.content).distinctBy(VehicleDto::id)
                    }
                    // A car may have been selected while an off-page preference was being checked.
                    val active=when {
                        page==0 && items.isEmpty() -> null
                        page==0 && requested==preferredId -> resolved
                        else -> items.firstOrNull { it.id==preferredId } ?: state.activeVehicle ?: items.firstOrNull()
                    }
                    state.copy(items=items,activeVehicle=active,loading=false,loadingMore=false,
                        nextPage=if(keepLoadedPages) state.nextPage else page+1,totalPages=result.totalPages)
                }
            } catch (error: Exception) { val failure = errors.map(error); _state.update { it.copy(loading = false, loadingMore = false, failure = failure) } }
        }
    }

    private suspend fun resolveVehicle(page: VehiclePage,id: Long?,previous: VehicleDto?): VehicleDto? {
        val first=page.content.firstOrNull() ?: return null
        if(id==null) return first
        page.content.firstOrNull { it.id==id }?.let { return it }
        if(page.totalPages<=1) return first
        return try { repository.get(id) }
        catch(cancelled: CancellationException) { throw cancelled }
        catch(error: HttpException) { if(error.code()==404) first else previous ?: first }
        catch(_: Exception) { previous ?: first }
    }

    fun delete(id: Long,onDeleted: (Long) -> Unit = {}) {
        if (_state.value.deleting != null) return
        loadJob?.cancel()
        _state.update { it.copy(deleting = id, loading = false, loadingMore = false, failure = null) }
        viewModelScope.launch {
            try { repository.delete(id) }
            catch (error: Exception) {
                val failure=errors.map(error)
                _state.update { it.copy(deleting=null,failure=failure) }
                return@launch
            }
            if(preferredId==id) preferredId=null
            _state.update { state ->
                val items=state.items.filterNot { it.id==id }
                state.copy(deleting=null,items=items,activeVehicle=state.activeVehicle?.takeUnless { it.id==id } ?: items.firstOrNull())
            }
            onDeleted(id)
            refresh()
        }
    }
}
