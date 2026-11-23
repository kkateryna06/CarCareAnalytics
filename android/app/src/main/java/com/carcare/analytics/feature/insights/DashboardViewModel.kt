package com.carcare.analytics.feature.insights

import androidx.lifecycle.*
import com.carcare.analytics.R
import com.carcare.analytics.core.network.*
import com.carcare.analytics.feature.logbook.*
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.time.*
import java.time.temporal.ChronoUnit
import javax.inject.Inject

data class DashboardState(val from: String,val to: String,val data: DashboardDto? = null,val loading: Boolean = true,val stale: Boolean = false,val fields: Map<String,Int> = emptyMap(),val failure: Failure? = null)

data class DashboardChartState(
    val data: MonthlyDto? = null,
    val loading: Boolean = true,
    val failure: Failure? = null,
    val selection: DashboardMonthState? = null
)

data class DashboardMonthState(
    val month: YearMonth,
    val data: DashboardDto? = null,
    val loading: Boolean = true,
    val failure: Failure? = null
)

fun validateAnalyticsPeriod(from: String,to: String): Map<String,Int> {
    val result=validateFilters(JournalFilters(dateFrom=from,dateTo=to)).toMutableMap()
    if(from.isBlank()) result["dateFrom"]=R.string.error_required; if(to.isBlank()) result["dateTo"]=R.string.error_required
    val first=runCatching { YearMonth.from(LocalDate.parse(from)) }.getOrNull(); val last=runCatching { YearMonth.from(LocalDate.parse(to)) }.getOrNull()
    if(first!=null && last!=null && ChronoUnit.MONTHS.between(first,last)+1>1200) result["dateFrom"]=R.string.error_long_period
    return result
}
@HiltViewModel
class DashboardViewModel @Inject constructor(private val source: InsightsDataSource,private val errors: ErrorMapper,private val saved: SavedStateHandle,private val cache: ScreenReadCache? = null): ViewModel() {
    val vehicleId=checkNotNull(saved.get<String>("vehicleId")).toLong()
    private var from=saved.get<String>("active_from")?:LocalDate.now().withDayOfMonth(1).minusMonths(2).toString()
    private var to=saved.get<String>("active_to")?:LocalDate.now().toString()
    private fun query() = DashboardQuery(from,to,ZoneId.systemDefault().id)
    private var shownQuery=query()
    private val initial=cache?.dashboards?.snapshot(vehicleId,shownQuery)?.value
    private val _state=MutableStateFlow(DashboardState(saved["draft_from"]?:from,saved["draft_to"]?:to,data=initial,loading=initial==null))
    val state=_state.asStateFlow()
    private var job: Job?=null
    private var chartQuery = chartRange()
    private var monthQuery: DashboardQuery? = null
    private val initialChart = cache?.dashboardCharts?.snapshot(vehicleId, chartQuery)?.value
    private val _chart = MutableStateFlow(DashboardChartState(data = initialChart, loading = initialChart == null))
    val chart = _chart.asStateFlow()
    private var chartJob: Job? = null
    private var monthJob: Job? = null

    private fun chartRange(): DashboardQuery {
        val last = YearMonth.from(LocalDate.parse(to))
        val first = minOf(YearMonth.from(LocalDate.parse(from)), last.minusMonths(11))
        return DashboardQuery(first.atDay(1).toString(), last.atEndOfMonth().toString(), ZoneId.systemDefault().id)
    }

    fun changeFrom(value: String) { saved["draft_from"]=value; _state.update { it.copy(from=value,fields=emptyMap()) } }
    fun changeTo(value: String) { saved["draft_to"]=value; _state.update { it.copy(to=value,fields=emptyMap()) } }
    fun preset(months: Int) { val today=LocalDate.now(); changeFrom((if(months==0) today.withDayOfYear(1) else today.withDayOfMonth(1).minusMonths((months-1).toLong())).toString()); changeTo(today.toString()); apply() }
    fun apply() {
        val s=_state.value; val fields=validateAnalyticsPeriod(s.from,s.to)
        if(fields.isNotEmpty()) { _state.update { it.copy(fields=fields) }; return }
        from=s.from; to=s.to; saved["active_from"]=from; saved["active_to"]=to; resume()
    }
    fun resume() = loadAll(force = false)
    fun refresh() = loadAll(force = true)

    private fun loadAll(force: Boolean) {
        load(force)
        loadChart(force)
        val selected = saved.get<String>("overview_month")?.let(YearMonth::parse)
        if (selected != null && includesMonth(selected)) {
            loadMonth(selected, force)
        } else {
            closeMonth()
        }
    }

    fun closeMonth() {
        monthJob?.cancel()
        monthQuery = null
        saved.remove<String>("overview_month")
        _chart.update { it.copy(selection = null) }
    }

    private fun includesMonth(month: YearMonth): Boolean =
        month in YearMonth.from(LocalDate.parse(chartQuery.from))..YearMonth.from(LocalDate.parse(chartQuery.to))

    fun selectMonth(value: String) {
        val month = YearMonth.parse(value)
        if (!includesMonth(month)) return
        if (_chart.value.selection?.let { it.month == month && it.loading } == true) return
        saved["overview_month"] = value
        loadMonth(month, force = false)
    }

    fun retryMonth() {
        _chart.value.selection?.let { loadMonth(it.month, force = true) }
    }

    private fun loadChart(force: Boolean) {
        val query = chartRange()
        chartJob?.cancel()
        val snapshot = cache?.dashboardCharts?.snapshot(vehicleId, query)
        val displayed = snapshot?.value ?: _chart.value.data.takeIf { chartQuery == query }
        chartQuery = query
        val needsRefresh = force || snapshot?.fresh != true
        _chart.update { it.copy(data = displayed, loading = needsRefresh, failure = null) }
        if (!needsRefresh) return
        chartJob = viewModelScope.launch {
            try {
                val fetch = suspend { source.monthly(vehicleId, query.from, query.to, query.timeZone) }
                val data = cache?.dashboardCharts?.fetch(vehicleId, query, load = fetch) ?: fetch()
                _chart.update { it.copy(data = data, loading = false) }
            } catch (ex: Exception) {
                val failure = errors.map(ex)
                _chart.update { it.copy(loading = false, failure = failure) }
            }
        }
    }

    private fun loadMonth(month: YearMonth, force: Boolean) {
        val query = DashboardQuery(month.atDay(1).toString(), month.atEndOfMonth().toString(), chartQuery.timeZone)
        monthJob?.cancel()
        val snapshot = cache?.dashboards?.snapshot(vehicleId, query)
        val displayed = snapshot?.value ?: _chart.value.selection?.data.takeIf { monthQuery == query }
        monthQuery = query
        val needsRefresh = force || snapshot?.fresh != true
        _chart.update { it.copy(selection = DashboardMonthState(month, displayed, needsRefresh)) }
        if (!needsRefresh) return
        monthJob = viewModelScope.launch {
            try {
                val fetch = suspend { source.dashboard(vehicleId, query.from, query.to) }
                val data = cache?.dashboards?.fetch(vehicleId, query, load = fetch) ?: fetch()
                _chart.update { it.copy(selection = DashboardMonthState(month, data, loading = false)) }
            } catch (ex: Exception) {
                val failure = errors.map(ex)
                _chart.update { it.copy(selection = DashboardMonthState(month, displayed, loading = false, failure = failure)) }
            }
        }
    }

    private fun load(force: Boolean) {
        val query=query()
        job?.cancel()
        val snapshot=cache?.dashboards?.snapshot(vehicleId,query)
        val displayed=snapshot?.value ?: _state.value.data.takeIf { shownQuery==query }
        shownQuery=query
        val needsRefresh=force || snapshot?.fresh!=true
        _state.update { it.copy(data=displayed,loading=needsRefresh,failure=null,stale=false) }
        if (!needsRefresh) return
        job=viewModelScope.launch {
            try {
                val fetch: suspend () -> DashboardDto = { source.dashboard(vehicleId,query.from,query.to) }
                val data=cache?.dashboards?.fetch(vehicleId,query,load=fetch) ?: fetch()
                _state.update { it.copy(data=data,loading=false,stale=false) }
            }
            catch(ex: Exception) { val failure=errors.map(ex); _state.update { it.copy(loading=false,stale=it.data!=null,failure=failure) } }
        }
    }
}
