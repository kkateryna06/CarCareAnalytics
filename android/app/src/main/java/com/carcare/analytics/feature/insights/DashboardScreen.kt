package com.carcare.analytics.feature.insights

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.*
import com.carcare.analytics.R
import com.carcare.analytics.core.designsystem.*
import com.carcare.analytics.feature.logbook.EntryKind

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(onBack: ()->Unit,onJournal: ()->Unit,onReminders: ()->Unit,onForecast: ()->Unit,onAdd: (EntryKind)->Unit,viewModel: DashboardViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val chart by viewModel.chart.collectAsStateWithLifecycle()
    val scroll = rememberLazyListState()
    var periodOpen by rememberSaveable { mutableStateOf(false) }
    var detailsOpen by rememberSaveable { mutableStateOf(false) }
    val monthOpen = chart.selection != null && state.data != null
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { viewModel.resume() }
    BackHandler(enabled = monthOpen && !periodOpen && !detailsOpen, onBack = viewModel::closeMonth)
    LaunchedEffect(monthOpen) {
        if (monthOpen) scroll.animateScrollToItem(1)
        else if (scroll.canScrollBackward) scroll.scrollToItem(0)
    }

    Scaffold(contentWindowInsets=WindowInsets.statusBars) { padding ->
        BoxWithConstraints(Modifier.fillMaxSize().padding(padding)) {
            val compact = maxHeight < 640.dp
            val spacing = if (compact) 8.dp else 12.dp
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                state = scroll,
                contentPadding = PaddingValues(horizontal = 20.dp, vertical = spacing),
                verticalArrangement = Arrangement.spacedBy(spacing),
                userScrollEnabled = monthOpen || scroll.canScrollForward || scroll.canScrollBackward
            ) {
                item(key = "overview-summary") {
                    Column(verticalArrangement = Arrangement.spacedBy(spacing)) {
                        OverviewHeading(state.data?.summary?.period) { periodOpen = true }
                        state.failure?.let { FailureCard(it, viewModel::refresh, showingCachedData = state.stale) }
                        if (state.stale && state.failure?.fallback != R.string.error_network) {
                            Text(stringResource(R.string.stale_analytics), color = MaterialTheme.colorScheme.error)
                        }
                        if (state.loading && state.data == null) LoadingCards()
                        state.data?.let { data ->
                            TerracottaVehicle(data.vehicle, onBack, compact = true)
                            TerracottaTotal(stringResource(R.string.overview_period_total), data.summary)
                            OverviewMetrics(data)
                        }
                    }
                }
                state.data?.let { data ->
                    item(key = "monthly-chart") {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            chart.data?.let { OverviewMonthChart(it.content, chart.selection?.month, compact, viewModel::selectMonth) }
                            if (chart.loading && chart.data == null) LinearProgressIndicator(Modifier.fillMaxWidth())
                            chart.failure?.let { FailureCard(it, viewModel::refresh, showingCachedData = chart.data != null) }
                        }
                    }
                    chart.selection?.let { month ->
                        item(key = "monthly-report") { OverviewMonthReport(month, viewModel::retryMonth, viewModel::closeMonth) }
                    }
                    if (!compact) item(key = "overview-deadline") { OverviewDeadline(data, onReminders) }
                    item(key = "overview-actions") {
                        Column {
                            AddOperationButton(onAdd, largeLabel = !compact)
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                TextButton(onClick = { detailsOpen = true }) { Text(stringResource(R.string.more_analytics)) }
                                TextButton(onClick = onForecast) { Text(stringResource(R.string.forecast_link)) }
                            }
                        }
                    }
                }
            }
        }
    }

    if (detailsOpen) state.data?.let { data ->
        ModalBottomSheet(
            onDismissRequest = { detailsOpen = false },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ) {
            OverviewDetails(data)
        }
    }
    if(periodOpen) ModalBottomSheet(onDismissRequest={periodOpen=false},sheetState=rememberModalBottomSheetState(skipPartiallyExpanded=true)) {
        Column(Modifier.navigationBarsPadding().padding(20.dp).imePadding().verticalScroll(rememberScrollState())) {
            PeriodSelector(state,viewModel) { periodOpen=false }
            TextButton(onClick={periodOpen=false},modifier=Modifier.fillMaxWidth()) { Text(stringResource(R.string.done)) }
        }
    }
}

@Composable
private fun OverviewDetails(data: DashboardDto) {
    LazyColumn(
        Modifier.navigationBarsPadding(),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item { Text(stringResource(R.string.more_analytics), style = MaterialTheme.typography.titleLarge) }
        item { MetricRow(R.string.all_spending,stringResource(R.string.money_pln,decimalDisplay(data.lifetimeExpensesPln)),R.string.month_spending,stringResource(R.string.money_pln,decimalDisplay(data.currentMonthExpensesPln))) }
        item { MetricRow(R.string.period_spending,stringResource(R.string.money_pln,decimalDisplay(data.summary.totalExpensesPln)),R.string.month_average,stringResource(R.string.money_pln,decimalDisplay(data.summary.monthAveragePln))) }
        item { MetricRow(R.string.distance,data.summary.distanceKm?.let { stringResource(R.string.km_long,it) }?:stringResource(R.string.not_available),R.string.cost_per_km,data.summary.costPerKmPln?.let { stringResource(R.string.pln_per_km,decimalDisplay(it,4)) }?:stringResource(R.string.not_available)) }
        item { MetricRow(R.string.fuel_consumption,data.fuel.averageLitersPer100Km?.let { stringResource(R.string.liters_per_100,decimalDisplay(it)) }?:stringResource(R.string.not_available),R.string.average_fuel_price,data.fuel.averagePricePerLiterPln?.let { stringResource(R.string.pln_per_liter,decimalDisplay(it)) }?:stringResource(R.string.not_available)) }
        item {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) { CategoryChart(data.categories.content) }
        }
    }
}

@Composable private fun MetricRow(leftLabel: Int,leftValue: String,rightLabel: Int,rightValue: String) {
    Row(horizontalArrangement=Arrangement.spacedBy(12.dp)) {
        Metric(leftLabel,leftValue,Modifier.weight(1f)); Metric(rightLabel,rightValue,Modifier.weight(1f))
    }
}
@Composable private fun Metric(label: Int,value: String,modifier: Modifier) {
    ElevatedCard(modifier) { Column(Modifier.padding(16.dp).heightIn(min=80.dp),verticalArrangement=Arrangement.spacedBy(10.dp)) { Text(stringResource(label),style=MaterialTheme.typography.labelLarge); Text(value,style=MaterialTheme.typography.titleLarge,color=MaterialTheme.colorScheme.primary) } }
}
@Composable private fun PeriodSelector(state: DashboardState,viewModel: DashboardViewModel,onDone:()->Unit) {
    Card(Modifier.fillMaxWidth()) { Column(Modifier.padding(16.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {
        Text(stringResource(R.string.analytics_period),style=MaterialTheme.typography.titleMedium)
        Row { TextButton(onClick={ viewModel.preset(1);onDone() }) { Text(stringResource(R.string.this_month)) }; TextButton(onClick={ viewModel.preset(3);onDone() }) { Text(stringResource(R.string.three_months)) } }
        TextButton(onClick={ viewModel.preset(0);onDone() }) { Text(stringResource(R.string.this_year)) }
        val fromError=state.fields["dateFrom"]?.let { stringResource(it) }?:localizedFieldError(state.failure,"dateFrom")
        DateField(state.from,viewModel::changeFrom,stringResource(R.string.date_from),!state.loading,fromError)
        val toError=state.fields["dateTo"]?.let { stringResource(it) }?:localizedFieldError(state.failure,"dateTo")
        DateField(state.to,viewModel::changeTo,stringResource(R.string.date_to),!state.loading,toError)
        Button(shape=androidx.compose.foundation.shape.RoundedCornerShape(16.dp), onClick={ viewModel.apply(); if(validateAnalyticsPeriod(state.from,state.to).isEmpty()) onDone() },enabled=!state.loading) { Text(stringResource(R.string.apply_filters)) }
    } }
}
