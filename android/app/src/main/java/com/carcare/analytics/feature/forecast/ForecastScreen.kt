package com.carcare.analytics.feature.forecast

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.carcare.analytics.R
import com.carcare.analytics.core.designsystem.*
import com.carcare.analytics.feature.insights.*
import kotlin.math.ceil

private const val SimpleMethod="SIMPLE_MOVING_AVERAGE"
private const val WeightedMethod="WEIGHTED_MOVING_AVERAGE"

private fun methodLabel(method: String) = when(method) {
    SimpleMethod -> R.string.forecast_simple
    WeightedMethod -> R.string.forecast_weighted
    else -> R.string.forecast_tie
}

private fun warningLabel(code: String): Int? = when(code) {
    "CURRENT_MONTH_EXCLUDED" -> R.string.forecast_horizon
    "HISTORY_COMPLETENESS_NOT_VERIFIED" -> R.string.forecast_incomplete_history
    "RARE_REPAIRS_NOT_PREDICTABLE" -> R.string.forecast_rare_repairs
    "NO_BACKTEST_TARGETS" -> R.string.forecast_no_backtest
    "SMALL_EVALUATION_SAMPLE" -> R.string.forecast_small_sample
    "ZERO_MONTHS_ASSUMED_NO_EXPENSES" -> R.string.forecast_zero_months
    else -> null
}

@Composable private fun money(value: String?): String =
    if(value==null) "—" else stringResource(R.string.money_pln,overviewNumber(value))

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ForecastScreen(onBack: () -> Unit,onAddExpense: () -> Unit,viewModel: ForecastViewModel=hiltViewModel()) {
    var exportOpen by rememberSaveable { mutableStateOf(false) }
    var comparing by rememberSaveable { mutableStateOf(false) }
    var methodOpen by rememberSaveable { mutableStateOf<String?>(null) }
    var methodologyOpen by rememberSaveable { mutableStateOf(false) }
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context=LocalContext.current
    val share=rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { }
    val overviewScroll=rememberLazyListState()
    val comparisonScroll=rememberLazyListState()
    val data=state.data
    val close={ if(comparing) comparing=false else onBack() }
    val periodPending=data!=null && data.requestedHistoryMonths!=state.historyMonths
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { viewModel.resume() }
    BackHandler(enabled=comparing && !exportOpen && methodOpen==null) { comparing=false }

    LaunchedEffect(state.share) {
        state.share?.let {
            try {
                share.launch(csvShareIntent(context,it))
                viewModel.shared()
                exportOpen=false
            } catch(_: android.content.ActivityNotFoundException) { viewModel.shareFailed() }
            catch(_: SecurityException) { viewModel.shareFailed() }
            catch(_: IllegalArgumentException) { viewModel.shareFailed() }
        }
    }

    Scaffold(
        contentWindowInsets=WindowInsets.statusBars,
        topBar={
            ForecastHeader(
                stringResource(if(comparing) R.string.forecast_comparison_title else R.string.forecast_screen_title),
                close,if(comparing) null else viewModel::refresh,!state.loading
            )
        }
    ) { padding ->
        LazyColumn(
            Modifier.fillMaxSize().padding(padding),
            state=if(comparing) comparisonScroll else overviewScroll,
            contentPadding=PaddingValues(start=20.dp,end=20.dp,top=8.dp,bottom=22.dp),
            verticalArrangement=Arrangement.spacedBy(12.dp)
        ) {
            state.vehicle?.let { vehicle -> item { TerracottaVehicle(vehicle) } }
            state.failure?.let { failure -> item { FailureCard(failure,viewModel::refresh) } }
            if(state.stale) item { Text(stringResource(R.string.stale_analytics),style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.error) }
            if(state.loading && data==null) item { LoadingCards() }

            if(data!=null) {
                if(comparing) {
                    item {
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(stringResource(R.string.forecast_history_selector), style = MaterialTheme.typography.titleMedium)
                            HistoryChips(state.historyMonths, viewModel::history)
                            if (periodPending) {
                                Text(stringResource(R.string.forecast_showing_history, data.requestedHistoryMonths), style = MaterialTheme.typography.bodySmall)
                            } else if (data.availableMonths < data.requestedHistoryMonths) {
                                Text(stringResource(R.string.forecast_available_history, data.availableMonths), style = MaterialTheme.typography.bodySmall)
                            }
                            Text(stringResource(R.string.forecast_sample_count, data.evaluationCount), style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                    items(data.methods,key={ "method:${it.method}" }) { method ->
                        ForecastMethodCard(method,data.bestMethod==method.method && method.maePln!=null) { methodOpen=method.method }
                    }
                    if (data.evaluations.isEmpty()) {
                        item { Text(stringResource(R.string.forecast_no_backtest), style = MaterialTheme.typography.bodySmall) }
                    } else {
                        item { ForecastNotice("info", stringResource(R.string.forecast_backtest_description)) }
                        item { ForecastComparison(data.evaluations) }
                    }
                    items(data.evaluations,key={ "evaluation:${it.targetMonth}" }) { row -> EvaluationCard(row) }
                    data.warnings.filter { it=="SMALL_EVALUATION_SAMPLE" || it=="ZERO_MONTHS_ASSUMED_NO_EXPENSES" }.mapNotNull(::warningLabel).forEach { label ->
                        item { Text(stringResource(label),style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant) }
                    }
                } else if(data.sufficientData) {
                    item { ForecastEstimate(data) }
                    item {
                        TextButton(onClick={ comparing=true },contentPadding=PaddingValues(horizontal=0.dp)) {
                            Text(stringResource(R.string.forecast_compare_button),style=MaterialTheme.typography.titleMedium)
                            Spacer(Modifier.width(5.dp)); OverviewIcon("right",Modifier.size(16.dp))
                        }
                    }
                } else {
                    item { InsufficientForecast(data,onAddExpense) }
                }
            }

            if(!comparing) {
                item {
                    OutlinedButton(
                        onClick={ exportOpen=true },modifier=Modifier.fillMaxWidth().heightIn(min=52.dp),
                        shape=RoundedCornerShape(16.dp),border=BorderStroke(1.dp,MaterialTheme.colorScheme.outlineVariant)
                    ) {
                        OverviewIcon("download",Modifier.size(23.dp))
                        Spacer(Modifier.width(8.dp)); Text(stringResource(R.string.forecast_export_button),style=MaterialTheme.typography.titleMedium)
                    }
                }
                if(data!=null) {
                    item {
                        TextButton(onClick={ methodologyOpen=!methodologyOpen },contentPadding=PaddingValues(horizontal=0.dp)) {
                            Text(stringResource(if(methodologyOpen) R.string.forecast_hide_methodology else R.string.forecast_methodology))
                            Spacer(Modifier.width(6.dp)); OverviewIcon(if(methodologyOpen) "up" else "down",Modifier.size(15.dp))
                        }
                    }
                    if(methodologyOpen) {
                        data.warnings.filter { it != "SMALL_EVALUATION_SAMPLE" && it != "NO_BACKTEST_TARGETS" }
                            .mapNotNull(::warningLabel).distinct().forEach { label ->
                            item { Text(stringResource(label),style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant) }
                        }
                        if(data.history.isNotEmpty()) item {
                            Column(verticalArrangement=Arrangement.spacedBy(6.dp)) {
                                Text(stringResource(R.string.monthly_chart),style=MaterialTheme.typography.titleMedium)
                                OverviewChart(data.history.takeLast(data.windowMonths).map { MonthTotalDto(it.month,it.amountPln) })
                            }
                        }
                    }
                }
            }
        }
    }

    data?.methods?.firstOrNull { it.method==methodOpen }?.let { method ->
        AlertDialog(
            onDismissRequest={ methodOpen=null },title={ Text(stringResource(methodLabel(method.method))) },
            text={
                Column(verticalArrangement=Arrangement.spacedBy(10.dp)) {
                    Text(stringResource(if(method.method==SimpleMethod) R.string.forecast_simple_formula else R.string.forecast_weighted_formula))
                    Text(stringResource(R.string.forecast_backtest_description))
                    data.history.lastOrNull()?.let { last ->
                        val forecast=method.forecastPln?.toBigDecimalOrNull()
                        val lastAmount=last.amountPln.toBigDecimalOrNull()
                        if(forecast!=null && lastAmount!=null) Text(
                            stringResource(R.string.forecast_last_month,localizedMonth(last.month),overviewNumber(last.amountPln),overviewNumber((forecast-lastAmount).toPlainString()))
                        )
                    }
                }
            },
            confirmButton={ TextButton(onClick={ methodOpen=null }) { Text(stringResource(R.string.forecast_close)) } }
        )
    }
    if(exportOpen) {
        ModalBottomSheet(
            onDismissRequest={ if(!state.exporting) exportOpen=false },
            sheetState=rememberModalBottomSheetState(skipPartiallyExpanded=true),
            containerColor=MaterialTheme.colorScheme.background
        ) {
            Column(Modifier.imePadding().verticalScroll(rememberScrollState()).padding(horizontal=20.dp).padding(bottom=24.dp)) {
                CsvExportPanel(state,viewModel)
            }
        }
    }
}

@Composable
private fun ForecastEstimate(data: ForecastDto) {
    val estimate = data.methods.firstOrNull { it.method == SimpleMethod }?.forecastPln
    Surface(
        Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.secondaryContainer,
        shape = RoundedCornerShape(18.dp)
    ) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(localizedMonth(data.targetMonth), style = MaterialTheme.typography.titleMedium)
            Text(money(estimate), style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold)
            Text(
                stringResource(R.string.forecast_simple_formula),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun ForecastHeader(title: String,onBack: () -> Unit,onRefresh: (() -> Unit)?,enabled: Boolean) {
    val back=stringResource(R.string.back)
    val refresh=stringResource(R.string.refresh)
    Row(
        Modifier.fillMaxWidth().statusBarsPadding().padding(start=20.dp,end=14.dp,top=8.dp,bottom=8.dp),
        verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(10.dp)
    ) {
        FilledTonalIconButton(onClick=onBack,shape=RoundedCornerShape(14.dp),modifier=Modifier.size(48.dp).semantics { contentDescription=back }) {
            OverviewIcon("left",Modifier.size(22.dp))
        }
        Text(title,Modifier.weight(1f),fontSize=25.sp,lineHeight=30.sp,fontWeight=FontWeight.Bold,letterSpacing=(-.45).sp)
        if(onRefresh!=null) IconButton(onClick=onRefresh,enabled=enabled,modifier=Modifier.semantics { contentDescription=refresh }) {
            OverviewIcon("refresh",Modifier.size(22.dp),LocalContentColor.current)
        }
    }
}

@Composable
private fun HistoryChips(selected: Int,onSelect: (Int) -> Unit) {
    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),horizontalArrangement=Arrangement.spacedBy(8.dp)) {
        forecastHistoryOptions.forEach { months ->
            FilterChip(
                selected=selected==months,onClick={ onSelect(months) },shape=CircleShape,
                label={ Text(stringResource(R.string.forecast_months,months)) },
                colors=FilterChipDefaults.filterChipColors(selectedContainerColor=MaterialTheme.colorScheme.primaryContainer),
                border=FilterChipDefaults.filterChipBorder(true,selected==months,borderColor=MaterialTheme.colorScheme.outlineVariant)
            )
        }
    }
}

@Composable
private fun ForecastMethodCard(method: ForecastMethod,lowerError: Boolean,onClick: () -> Unit) {
    OutlinedCard(
        onClick=onClick,modifier=Modifier.fillMaxWidth(),shape=RoundedCornerShape(18.dp),
        colors=CardDefaults.outlinedCardColors(containerColor=Color.Transparent),
        border=BorderStroke(1.dp,MaterialTheme.colorScheme.outlineVariant)
    ) {
        Row(Modifier.fillMaxWidth().padding(13.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(11.dp)) {
            ForecastIcon(if(method.method==WeightedMethod) "scale" else "bars",44)
            Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(3.dp)) {
                Text(stringResource(methodLabel(method.method)),style=MaterialTheme.typography.titleMedium,fontWeight=FontWeight.Bold)
                Text(money(method.forecastPln),fontSize=27.sp,lineHeight=32.sp,fontWeight=FontWeight.Bold,letterSpacing=(-.6).sp)
                Text(stringResource(R.string.forecast_mae_short,money(method.maePln)),style=MaterialTheme.typography.bodyMedium,color=MaterialTheme.colorScheme.onSurfaceVariant)
                if(lowerError) {
                    Surface(color=MaterialTheme.colorScheme.primaryContainer,shape=CircleShape) {
                        Text(stringResource(R.string.forecast_lower_error),Modifier.padding(horizontal=8.dp,vertical=4.dp),style=MaterialTheme.typography.labelMedium,color=MaterialTheme.colorScheme.onPrimaryContainer)
                    }
                }
            }
            OverviewIcon("right",Modifier.size(17.dp))
        }
    }
}

@Composable
private fun ForecastIcon(symbol: String,size: Int) {
    Surface(shape=RoundedCornerShape(if(size>60) 23.dp else 13.dp),color=MaterialTheme.colorScheme.secondaryContainer) {
        Box(Modifier.size(size.dp),contentAlignment=Alignment.Center) {
            OverviewIcon(symbol,Modifier.size((size*.58).dp))
        }
    }
}

@Composable
private fun InsufficientForecast(data: ForecastDto,onAddExpense: () -> Unit) {
    Column(Modifier.fillMaxWidth(),horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.spacedBy(15.dp)) {
        Spacer(Modifier.height(20.dp))
        ForecastIcon("bars",88)
        Text(stringResource(R.string.forecast_not_enough_title),style=MaterialTheme.typography.headlineSmall,textAlign=TextAlign.Center)
        Text(stringResource(R.string.forecast_history_missing,data.windowMonths,data.availableMonths),style=MaterialTheme.typography.bodyLarge,textAlign=TextAlign.Center,color=MaterialTheme.colorScheme.onSurfaceVariant)
        Surface(Modifier.fillMaxWidth(),color=MaterialTheme.colorScheme.surfaceContainerLow,shape=RoundedCornerShape(18.dp)) {
            Column(Modifier.fillMaxWidth().padding(18.dp),horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.spacedBy(5.dp)) {
                Text(localizedMonth(data.targetMonth),style=MaterialTheme.typography.titleMedium,textAlign=TextAlign.Center)
                Text("—",style=MaterialTheme.typography.headlineLarge)
                Text(stringResource(R.string.forecast_no_value),style=MaterialTheme.typography.bodyMedium,color=MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Button(onClick=onAddExpense,modifier=Modifier.fillMaxWidth().heightIn(min=54.dp),shape=RoundedCornerShape(16.dp)) {
            CareIcon(CareSymbol.PLUS,Modifier.size(22.dp))
            Spacer(Modifier.width(8.dp)); Text(stringResource(R.string.forecast_add_past),style=MaterialTheme.typography.titleMedium,textAlign=TextAlign.Center)
        }
        Text(stringResource(R.string.forecast_past_hint),style=MaterialTheme.typography.bodySmall,textAlign=TextAlign.Center,color=MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun ForecastNotice(icon: String,text: String) {
    Surface(Modifier.fillMaxWidth(),color=MaterialTheme.colorScheme.surfaceContainerLow,shape=RoundedCornerShape(16.dp)) {
        Row(Modifier.padding(horizontal=13.dp,vertical=11.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(9.dp)) {
            OverviewIcon(icon,Modifier.size(22.dp))
            Text(text,Modifier.weight(1f),style=MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
private fun CsvExportPanel(state: ForecastState,viewModel: ForecastViewModel) {
    Column(Modifier.fillMaxWidth(),verticalArrangement=Arrangement.spacedBy(12.dp)) {
        Text(stringResource(R.string.forecast_csv_title),style=MaterialTheme.typography.headlineMedium)
        state.vehicle?.let { TerracottaVehicle(it) }
        val fromError=state.fields["dateFrom"]?.let { stringResource(it) } ?: localizedFieldError(state.exportFailure,"dateFrom")
        val toError=state.fields["dateTo"]?.let { stringResource(it) } ?: localizedFieldError(state.exportFailure,"dateTo")
        DateField(state.from,viewModel::from,stringResource(R.string.date_from),!state.exporting,fromError)
        DateField(state.to,viewModel::to,stringResource(R.string.date_to),!state.exporting,toError)
        ForecastNotice("download",stringResource(R.string.export_csv_description))
        state.exportFailure?.let { FailureCard(it,viewModel::export) }
        if(state.exporting) LinearProgressIndicator(Modifier.fillMaxWidth())
        Button(
            onClick=viewModel::export,enabled=!state.exporting && state.share==null,
            modifier=Modifier.fillMaxWidth().heightIn(min=52.dp),shape=RoundedCornerShape(16.dp)
        ) {
            Text(stringResource(R.string.share_csv),style=MaterialTheme.typography.titleMedium)
        }
    }
}

@Composable
private fun ForecastComparison(rows: List<ForecastEvaluation>) {
    val colors=listOf(MaterialTheme.colorScheme.onSurface,Color(0xFFAB5036),Color(0xFF8B642E))
    val grid=MaterialTheme.colorScheme.outlineVariant
    val series=listOf(rows.map { it.actualPln },rows.map { it.simplePln },rows.map { it.weightedPln }).map { line ->
        line.map { it.toFloatOrNull()?.takeIf { value -> value.isFinite() && value>=0f } ?: return }
    }
    val maximum=series.maxOf { values -> values.maxOrNull() ?: 0f }.coerceAtLeast(1f)
    val step=ceil(maximum/4f)
    val axisMax=step*4
    val chartLabel=stringResource(R.string.forecast_chart_description)
    OutlinedCard(Modifier.fillMaxWidth(),shape=RoundedCornerShape(18.dp),colors=CardDefaults.outlinedCardColors(containerColor=Color.Transparent)) {
        Column(Modifier.padding(14.dp),verticalArrangement=Arrangement.spacedBy(9.dp)) {
            Text(stringResource(R.string.forecast_fact_chart),style=MaterialTheme.typography.titleLarge)
            Row(Modifier.fillMaxWidth().height(158.dp),horizontalArrangement=Arrangement.spacedBy(7.dp)) {
                Column(Modifier.height(158.dp),verticalArrangement=Arrangement.SpaceBetween) {
                    for(tick in 4 downTo 0) Text(overviewNumber((tick*step).toString(),0),style=MaterialTheme.typography.labelSmall)
                }
                Canvas(Modifier.weight(1f).fillMaxHeight().semantics { contentDescription=chartLabel }) {
                    val inset=7.dp.toPx()
                    val width=(size.width-2*inset).coerceAtLeast(0f)
                    val height=(size.height-2*inset).coerceAtLeast(0f)
                    for(tick in 0..4) {
                        val y=inset+height*tick/4
                        drawLine(grid,Offset(0f,y),Offset(size.width,y),1.dp.toPx())
                    }
                    series.forEachIndexed { index,values ->
                        val points=values.mapIndexed { i,value ->
                            Offset(inset+if(values.size==1) width/2 else width*i/(values.size-1),inset+height*(1-value/axisMax))
                        }
                        val line=Path()
                        points.forEachIndexed { i,p -> if(i==0) line.moveTo(p.x,p.y) else line.lineTo(p.x,p.y) }
                        val pattern=when(index) {
                            1 -> PathEffect.dashPathEffect(floatArrayOf(7.dp.toPx(),4.dp.toPx()))
                            2 -> PathEffect.dashPathEffect(floatArrayOf(2.dp.toPx(),4.dp.toPx()))
                            else -> null
                        }
                        drawPath(line,colors[index],style=Stroke(2.dp.toPx(),pathEffect=pattern))
                        points.forEach { drawCircle(colors[index],2.7.dp.toPx(),it) }
                    }
                }
            }
            Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween) {
                Text(localizedMonth(rows.first().targetMonth,"LLL yy"),style=MaterialTheme.typography.labelSmall)
                if(rows.size>1) Text(localizedMonth(rows.last().targetMonth,"LLL yy"),style=MaterialTheme.typography.labelSmall)
            }
            listOf(R.string.forecast_fact,R.string.forecast_simple,R.string.forecast_weighted).forEachIndexed { index,label ->
                Text("●  ${stringResource(label)}",color=colors[index],style=MaterialTheme.typography.labelMedium)
            }
        }
    }
}

@Composable
private fun EvaluationCard(row: ForecastEvaluation) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    OutlinedCard(onClick={ expanded=!expanded },modifier=Modifier.fillMaxWidth(),shape=RoundedCornerShape(16.dp),colors=CardDefaults.outlinedCardColors(containerColor=Color.Transparent)) {
        Column(Modifier.padding(13.dp),verticalArrangement=Arrangement.spacedBy(5.dp)) {
            Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(9.dp)) {
                ForecastIcon("title",38)
                Column(Modifier.weight(1f)) {
                    Text(localizedMonth(row.targetMonth),style=MaterialTheme.typography.titleMedium)
                    Text(stringResource(R.string.forecast_actual,overviewNumber(row.actualPln)),style=MaterialTheme.typography.bodyMedium,fontWeight=FontWeight.SemiBold)
                }
                OverviewIcon(if(expanded) "up" else "down",Modifier.size(18.dp))
            }
            Text(stringResource(R.string.forecast_error_row,stringResource(R.string.forecast_simple),overviewNumber(row.simplePln),overviewNumber(row.simpleAbsoluteErrorPln)),style=MaterialTheme.typography.bodySmall)
            Text(stringResource(R.string.forecast_error_row,stringResource(R.string.forecast_weighted),overviewNumber(row.weightedPln),overviewNumber(row.weightedAbsoluteErrorPln)),style=MaterialTheme.typography.bodySmall)
            if(expanded) Text(
                stringResource(R.string.forecast_training,localizedMonth(row.trainingFrom),localizedMonth(row.trainingTo),localizedMonth(row.excludedMonth)),
                style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
