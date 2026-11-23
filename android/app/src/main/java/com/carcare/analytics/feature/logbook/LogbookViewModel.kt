package com.carcare.analytics.feature.logbook

import androidx.lifecycle.*
import com.carcare.analytics.core.network.*
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import javax.inject.Inject

data class LogbookState(val vehicle: VehicleDto? = null,val documentsTab: Boolean = false,val timeline: List<TimelineDto> = emptyList(),
    val documents: List<JournalRecordDto> = emptyList(),val loading: Boolean = true,val loadingMore: Boolean = false,val deleting: Boolean = false,
    val nextPage: Int = 0,val totalPages: Int = 0,val filters: JournalFilters = JournalFilters(),val fields: Map<String,Int> = emptyMap(),val failure: Failure? = null,
    val appliedFilters: JournalFilters = JournalFilters(),val refreshing: Boolean = false,val failedPage: Int = 0)

internal val JournalFilters.hasCustomDates: Boolean get() = dateFrom.isNotBlank() || dateTo.isNotBlank()
internal val JournalFilters.hasHistoryFilters: Boolean get() = kind.isNotBlank() || category.isNotBlank() || hasCustomDates

@HiltViewModel
class LogbookViewModel @Inject constructor(private val repository: JournalRepository,private val api: CarCareApi,private val errors: ErrorMapper,private val saved: SavedStateHandle,private val cache: ScreenReadCache? = null): ViewModel() {
    val vehicleId=checkNotNull(saved.get<String>("vehicleId")).toLong()
    private fun restoredFilters()=JournalFilters(saved["filter_kind"]?:"",saved["filter_category"]?:"",saved["filter_from"]?:"",saved["filter_to"]?:"",saved["filter_document"]?:"")
    private var applied=restoredFilters()
    private fun restoredDraft()=JournalFilters(saved["draft_kind"]?:applied.kind,saved["draft_category"]?:applied.category,
        saved["draft_from"]?:applied.dateFrom,saved["draft_to"]?:applied.dateTo,saved["draft_document"]?:applied.documentType)
    private val _state=MutableStateFlow(LogbookState(
        documentsTab=saved.get<String>("tab")=="documents",filters=restoredDraft(),
        appliedFilters=applied
    ))
    val state=_state.asStateFlow()
    private var loadJob: Job?=null
    private var shownQuery: JournalFilters?=null
    private var shownRead: JournalRead?=null
    private var shownDocuments: DocumentsRead?=null

    init {
        if (_state.value.documentsTab) {
            cache?.documents?.snapshot(vehicleId,applied.documentType)?.let { showDocuments(it.value) }
        } else {
            val query=historyQuery()
            cache?.journals?.snapshot(vehicleId,query)?.let { showHistory(query,it.value) }
        }
    }

    private fun showDocuments(read: DocumentsRead) {
        val alreadyShown=shownDocuments===read
        shownDocuments=read
        _state.update {
            if(alreadyShown) it.copy(loading=false,loadingMore=false,refreshing=false,failure=null)
            else it.copy(vehicle=read.vehicle,documents=read.page.content,loading=false,loadingMore=false,refreshing=false,
                nextPage=1,totalPages=read.page.totalPages,failure=null)
        }
    }

    private fun historyQuery()=applied.copy(documentType="")

    private fun showHistory(query: JournalFilters,read: JournalRead) {
        val alreadyShown=shownQuery==query && shownRead===read
        shownQuery=query
        shownRead=read
        _state.update {
            if(alreadyShown) it.copy(loading=false,loadingMore=false,refreshing=false,failure=null)
            else it.copy(vehicle=read.vehicle,timeline=read.page.content,
                loading=false,loadingMore=false,refreshing=false,nextPage=1,totalPages=read.page.totalPages,failure=null)
        }
    }
    fun changeFilters(value: JournalFilters) {
        saved["draft_kind"]=value.kind; saved["draft_category"]=value.category; saved["draft_from"]=value.dateFrom; saved["draft_to"]=value.dateTo; saved["draft_document"]=value.documentType
        _state.update { it.copy(filters=value,fields=emptyMap()) }
    }
    fun beginFilters() { changeFilters(applied) }
    fun applyFilters(): Boolean {
        if (_state.value.deleting) return false
        val fields=validateFilters(_state.value.filters)
        if(!_state.value.documentsTab && fields.isNotEmpty()) { _state.update { it.copy(fields=fields) }; return false }
        val draft=_state.value.filters
        // Applying document filters must not accidentally apply an invalid, unfinished history range.
        val previous=applied
        applied=if(_state.value.documentsTab) applied.copy(documentType=draft.documentType) else draft.copy(documentType=applied.documentType)
        if(previous!=applied) clearRows()
        saveApplied()
        resume()
        return true
    }
    fun resetFilters() { changeFilters(if (_state.value.documentsTab) applied.copy(documentType="") else JournalFilters(documentType=applied.documentType)); applyFilters() }
    fun quickFilter(kind: String) {
        changeFilters(applied.copy(kind=kind, category=""))
        applyFilters()
    }
    fun documentFilter(type: DocumentType?) {
        if(_state.value.deleting || applied.documentType==type?.name.orEmpty()) return
        changeFilters(applied.copy(documentType=type?.name.orEmpty()))
        applyFilters()
    }
    private fun saveApplied() {
        saved["filter_kind"]=applied.kind; saved["filter_category"]=applied.category; saved["filter_from"]=applied.dateFrom; saved["filter_to"]=applied.dateTo; saved["filter_document"]=applied.documentType
        _state.update { it.copy(fields=emptyMap(),appliedFilters=applied) }
    }
    private fun clearRows() {
        shownRead=null
        shownQuery=null
        shownDocuments=null
        _state.update { it.copy(timeline=emptyList(),documents=emptyList(),nextPage=0,totalPages=0) }
    }
    fun resume() { if(!_state.value.deleting) load(0) }
    fun refresh() { if(!_state.value.deleting) load(0,force=true) }
    fun retry() { if(_state.value.failedPage>0) more() else refresh() }
    fun more() { val s=_state.value; if(!s.refreshing && !s.loadingMore && !s.deleting && s.nextPage<s.totalPages) load(s.nextPage) }
    private fun load(page: Int,force: Boolean = false) {
        loadJob?.cancel()
        val docs=_state.value.documentsTab
        val filters=applied
        val query=historyQuery()
        if(page==0) {
            if(docs) {
                val hit=cache?.documents?.snapshot(vehicleId,filters.documentType)
                hit?.let { showDocuments(it.value) }
                if(!force && hit?.fresh==true) return
            } else {
                val hit=cache?.journals?.snapshot(vehicleId,query)
                hit?.let { showHistory(query,it.value) }
                if(!force && hit?.fresh==true) return
            }
        }
        _state.update { it.copy(loading=page==0 && (if(docs) shownDocuments==null else shownQuery!=query),
            loadingMore=page>0,refreshing=page==0,failure=null,failedPage=0) }
        loadJob=viewModelScope.launch {
            try {
                if(docs) {
                    if(page==0) {
                        val fetch: suspend () -> DocumentsRead = { DocumentsRead(api.vehicle(vehicleId),repository.documents(vehicleId,0,filters)) }
                        val read=cache?.documents?.fetch(vehicleId,filters.documentType,load=fetch) ?: fetch()
                        showDocuments(read)
                    } else {
                        val result=repository.documents(vehicleId,page,filters)
                        _state.update { it.copy(documents=(it.documents+result.content).distinctBy(JournalRecordDto::id),
                            loadingMore=false,nextPage=page+1,totalPages=result.totalPages) }
                    }
                } else if(page==0) {
                    val fetch: suspend () -> JournalRead = { JournalRead(api.vehicle(vehicleId),repository.timeline(vehicleId,0,query)) }
                    val read=cache?.journals?.fetch(vehicleId,query,load=fetch) ?: fetch()
                    showHistory(query,read)
                } else {
                    val result=repository.timeline(vehicleId,page,query)
                    _state.update { it.copy(timeline=(it.timeline+result.content).distinctBy { row -> row.kind to row.id },loadingMore=false,nextPage=page+1,totalPages=result.totalPages) }
                }
            } catch(cancelled: CancellationException) { throw cancelled }
            catch(error: Exception) { val failure=errors.map(error); _state.update { it.copy(loading=false,loadingMore=false,refreshing=false,failure=failure,failedPage=page) } }
        }
    }

    fun delete(kind: EntryKind,id: Long) {
        if(_state.value.deleting) return
        loadJob?.cancel(); _state.update { it.copy(deleting=true,loading=false,loadingMore=false,refreshing=false,failure=null,failedPage=0) }
        viewModelScope.launch {
            try { repository.delete(vehicleId,kind,id); _state.update { it.copy(deleting=false) }; refresh() }
            catch(error: Exception) { val failure=errors.map(error); _state.update { it.copy(deleting=false,failure=failure) } }
        }
    }
}
