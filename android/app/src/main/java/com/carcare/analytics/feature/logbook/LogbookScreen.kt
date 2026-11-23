package com.carcare.analytics.feature.logbook

import com.carcare.analytics.feature.insights.localizedMonth
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.*
import com.carcare.analytics.R
import com.carcare.analytics.core.designsystem.*
import com.carcare.analytics.feature.insights.OverviewIcon
import com.carcare.analytics.feature.insights.TerracottaVehicle
import com.carcare.analytics.feature.insights.expenseCategoryColor
import com.carcare.analytics.feature.insights.overviewNumber
import java.time.LocalDate
import java.time.format.DateTimeFormatter

private val shortDateFormat=DateTimeFormatter.ofPattern("dd.MM.uuuu")
private val quickKinds=setOf("",EntryKind.REFUELING.name,EntryKind.SERVICE.name)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LogbookScreen(
    onGarage: () -> Unit,
    onBack: () -> Unit,
    onDocuments: () -> Unit,
    onAdd: (EntryKind) -> Unit,
    onEdit: (EntryKind, Long) -> Unit,
    viewModel: LogbookViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { viewModel.resume() }
    if(state.documentsTab) {
        DocumentsScreen(state,viewModel,onGarage,onBack,{ onAdd(EntryKind.DOCUMENT) },{ onEdit(EntryKind.DOCUMENT,it) })
        return
    }

    var filtersOpen by rememberSaveable { mutableStateOf(false) }
    var addOpen by rememberSaveable { mutableStateOf(false) }
    val openFilters = { viewModel.beginFilters(); filtersOpen=true }
    val add = { addOpen=true }
    val months=remember(state.timeline) { state.timeline.groupBy { it.date.take(7) } }

    Scaffold(contentWindowInsets=WindowInsets.statusBars) { padding ->
        LazyColumn(
            Modifier.fillMaxSize().padding(padding),
            contentPadding=PaddingValues(start=20.dp,end=20.dp,top=12.dp,bottom=20.dp),
            verticalArrangement=Arrangement.spacedBy(12.dp)
        ) {
            item { Text("CarCare",style=MaterialTheme.typography.headlineSmall,modifier=Modifier.padding(bottom=6.dp)) }
            state.vehicle?.let { vehicle ->
                item { TerracottaVehicle(vehicle,onGarage) }
            }
            item { JournalHeading(!state.deleting,add) }
            item { JournalQuickFilters(state.appliedFilters,!state.deleting,viewModel::quickFilter,openFilters) }
            if(state.appliedFilters.hasCustomDates || state.appliedFilters.category.isNotBlank() || state.appliedFilters.kind !in quickKinds) {
                item { AppliedFilter(state.appliedFilters,viewModel::resetFilters) }
            }
            item {
                Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween) {
                    TextButton(onClick=onDocuments,enabled=!state.deleting,modifier=Modifier.weight(1f,fill=false)) { Text(stringResource(R.string.documents)) }
                    TextButton(onClick=viewModel::refresh,enabled=!state.deleting && !state.refreshing) { Text(stringResource(R.string.refresh)) }
                }
            }
            state.failure?.takeIf { state.failedPage==0 }?.let { failure -> item { FailureCard(failure,viewModel::retry) } }
            if(state.loading) item { LoadingCards() }
            else {
                if(state.timeline.isEmpty() && state.failure==null) {
                    item {
                        if(state.appliedFilters.hasHistoryFilters) {
                            EmptyPanel(stringResource(R.string.journal_no_matches),stringResource(R.string.journal_no_matches_hint)) {
                                OutlinedButton(onClick=viewModel::resetFilters,shape=RoundedCornerShape(14.dp)) { Text(stringResource(R.string.journal_reset_filter)) }
                            }
                        } else {
                            EmptyPanel(stringResource(R.string.journal_empty)) {
                                OutlinedButton(onClick=add,shape=RoundedCornerShape(14.dp)) { Text(stringResource(R.string.add_operation)) }
                            }
                        }
                    }
                }
                months.forEach { (month,rows) ->
                    item(key="journal-month:$month",contentType="month-heading") {
                        Text(localizedMonth(month),Modifier.padding(top=6.dp,bottom=2.dp).semantics { heading() },
                            style=MaterialTheme.typography.titleLarge,fontWeight=FontWeight.Bold)
                    }
                    items(rows,key={ "${it.kind}:${it.id}" },contentType={ "history-row" }) { row ->
                        TimelineRow(row,!state.deleting) { onEdit(row.kind,row.id) }
                    }
                }
            }
            state.failure?.takeIf { state.failedPage>0 }?.let { failure -> item { FailureCard(failure,viewModel::retry) } }
            if(state.loadingMore || state.deleting) item { LinearProgressIndicator(Modifier.fillMaxWidth()) }
            else if(!state.loading && state.failure==null && state.nextPage<state.totalPages) {
                item {
                    OutlinedButton(onClick=viewModel::more,enabled=!state.refreshing,shape=RoundedCornerShape(14.dp),
                        modifier=Modifier.fillMaxWidth().heightIn(min=48.dp)) {
                        Text(stringResource(R.string.journal_show_more),style=MaterialTheme.typography.titleSmall)
                        Spacer(Modifier.width(8.dp))
                        OverviewIcon("down",Modifier.size(16.dp))
                    }
                }
            }
        }
    }
    if(addOpen) AddOperationSheet(onDismiss={ addOpen=false },onAdd={ kind -> addOpen=false; onAdd(kind) })
    if(filtersOpen) {
        ModalBottomSheet(
            onDismissRequest={ filtersOpen=false },
            sheetState=rememberModalBottomSheetState(skipPartiallyExpanded=true),
            containerColor=MaterialTheme.colorScheme.background
        ) {
            FilterPanel(state,viewModel,onDone={ filtersOpen=false })
        }
    }
}

@Composable
private fun JournalHeading(enabled: Boolean, onAdd: () -> Unit) {
    Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(8.dp)) {
        Text(
            stringResource(R.string.logbook),
            Modifier.weight(1f),fontSize=34.sp,lineHeight=40.sp,fontWeight=FontWeight.Bold,letterSpacing=(-1).sp
        )
        FilledTonalButton(onClick=onAdd,enabled=enabled,shape=RoundedCornerShape(14.dp),contentPadding=PaddingValues(horizontal=12.dp,vertical=8.dp)) {
            CareIcon(CareSymbol.PLUS,Modifier.size(20.dp))
            Spacer(Modifier.width(6.dp))
            Text(stringResource(R.string.add_short))
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun JournalQuickFilters(filters: JournalFilters, enabled: Boolean, onKind: (String) -> Unit, onFilters: () -> Unit) {
    val options=listOf("" to R.string.document_all,EntryKind.REFUELING.name to R.string.refuelings_plural,EntryKind.SERVICE.name to R.string.entry_service)
    val filtersLabel=stringResource(R.string.filters)
    Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.Top,horizontalArrangement=Arrangement.spacedBy(8.dp)) {
        FlowRow(Modifier.weight(1f),horizontalArrangement=Arrangement.spacedBy(5.dp)) {
            options.forEach { (kind,label) ->
                val icon=when(kind) { EntryKind.REFUELING.name -> "fuel"; EntryKind.SERVICE.name -> "service"; else -> null }
                JournalChip(stringResource(label),icon,kind==filters.kind && filters.category.isBlank(),enabled,compact=true) { onKind(kind) }
            }
        }
        FilledTonalIconButton(onClick=onFilters,enabled=enabled,shape=RoundedCornerShape(14.dp),modifier=Modifier.size(48.dp).semantics { contentDescription=filtersLabel }) {
            OverviewIcon("filter",Modifier.size(22.dp))
        }
    }
}

@Composable
private fun JournalChip(label: String, icon: String?, selected: Boolean, enabled: Boolean, compact: Boolean = false, onClick: () -> Unit) {
    FilterChip(
        selected=selected,onClick=onClick,enabled=enabled,label={
            val style=LocalTextStyle.current
            Text(label,maxLines=1,overflow=TextOverflow.Ellipsis,style=if(compact) style.copy(fontSize=13.sp) else style)
        },shape=CircleShape,
        leadingIcon=if(icon==null || selected) null else { { OverviewIcon(icon,Modifier.size(if(compact) 15.dp else 17.dp)) } },
        colors=FilterChipDefaults.filterChipColors(
            selectedContainerColor=MaterialTheme.colorScheme.primary,
            selectedLabelColor=MaterialTheme.colorScheme.onPrimary
        ),
        border=FilterChipDefaults.filterChipBorder(enabled,selected,borderColor=MaterialTheme.colorScheme.outlineVariant)
    )
}

@Composable
private fun AppliedFilter(filters: JournalFilters, onClear: () -> Unit) {
    val text=buildList {
        EntryKind.entries.firstOrNull { it.name==filters.kind }?.let { add(stringResource(it.label)) }
        ExpenseCategory.entries.firstOrNull { it.name==filters.category }?.let { add(stringResource(it.label)) }
        when {
            filters.dateFrom.isNotBlank() && filters.dateTo.isNotBlank() -> add("${journalDate(filters.dateFrom,shortDateFormat)}–${journalDate(filters.dateTo,shortDateFormat)}")
            filters.dateFrom.isNotBlank() -> add(stringResource(R.string.range_start,journalDate(filters.dateFrom,shortDateFormat)))
            filters.dateTo.isNotBlank() -> add(stringResource(R.string.document_until_date,journalDate(filters.dateTo,shortDateFormat)))
        }
    }.joinToString(" · ")
    FilledTonalButton(onClick=onClear,shape=CircleShape) { Text("$text  ×") }
}

private fun journalDate(value: String, format: DateTimeFormatter): String =
    runCatching { LocalDate.parse(value).format(format) }.getOrDefault(value)

@Composable
internal fun timelineTitle(record: TimelineDto): String {
    val label=when {
        record.kind==EntryKind.ODOMETER && record.title=="Начальный пробег" -> R.string.initial_mileage_entry
        record.kind==EntryKind.ODOMETER && record.title=="Пробег" -> R.string.entry_odometer
        record.kind==EntryKind.REFUELING && record.title=="Заправка" -> R.string.entry_refueling
        record.title.isBlank() -> record.kind.label
        else -> return record.title
    }
    return stringResource(label)
}

@Composable
private fun TimelineRow(record: TimelineDto, enabled: Boolean, onClick: () -> Unit) {
    val category=record.category
    val icon=when {
        record.kind==EntryKind.ODOMETER -> "road"
        record.kind==EntryKind.REFUELING -> "fuel"
        record.kind==EntryKind.SERVICE -> "service"
        category==ExpenseCategory.INSURANCE -> "shield"
        category==ExpenseCategory.PARKING -> "car"
        category==ExpenseCategory.WASH -> "drop"
        category==ExpenseCategory.TAXES -> "title"
        else -> "expense"
    }
    val tint=category?.let { expenseCategoryColor(it).copy(alpha=.17f) } ?: MaterialTheme.colorScheme.secondaryContainer
    val title=timelineTitle(record)
    val locale=appLocale()
    val date=remember(record.date,locale) { journalDate(record.date,DateTimeFormatter.ofPattern("d MMM",locale)) }
    val label=stringResource(category?.label ?: record.kind.label)
    val subtitle=when {
        record.kind==EntryKind.ODOMETER && record.odometerKm!=null ->
            "$date · ${stringResource(R.string.vehicle_odometer_value,overviewNumber(record.odometerKm.toString(),0))}"
        title==label -> date
        else -> "$label · $date"
    }
    Column {
        Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable(enabled=enabled,role=Role.Button,onClick=onClick)
            .padding(vertical=4.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(10.dp)) {
            Surface(shape=CircleShape,color=tint) {
                Box(Modifier.size(44.dp),contentAlignment=Alignment.Center) { OverviewIcon(icon,Modifier.size(23.dp)) }
            }
            Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(3.dp)) {
                Text(title,style=MaterialTheme.typography.titleSmall,fontWeight=FontWeight.SemiBold,maxLines=2,overflow=TextOverflow.Ellipsis)
                Text(subtitle,style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant,maxLines=2,overflow=TextOverflow.Ellipsis)
            }
            record.amountPln?.let {
                Text(stringResource(R.string.money_pln,overviewNumber(it)),Modifier.widthIn(max=126.dp),style=MaterialTheme.typography.titleMedium,
                    textAlign=TextAlign.End,color=MaterialTheme.colorScheme.primary,fontWeight=FontWeight.Bold)
            }
            OverviewIcon("right",Modifier.size(13.dp),MaterialTheme.colorScheme.onSurfaceVariant)
        }
        HorizontalDivider(Modifier.padding(start=54.dp,top=6.dp),color=MaterialTheme.colorScheme.outlineVariant.copy(alpha=.68f))
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun FilterPanel(state: LogbookState, viewModel: LogbookViewModel, onDone: () -> Unit) {
    val filters=state.filters
    val enabled=!state.deleting
    Column(Modifier.fillMaxWidth().imePadding().verticalScroll(rememberScrollState()).padding(start=20.dp,end=20.dp,bottom=24.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
        Text(stringResource(R.string.filters),style=MaterialTheme.typography.headlineMedium)
        Text(stringResource(R.string.filter_kind),style=MaterialTheme.typography.titleMedium)
        FlowRow(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
            JournalChip(stringResource(R.string.document_all),null,filters.kind.isBlank(),enabled) { viewModel.changeFilters(filters.copy(kind="")) }
            JournalChip(stringResource(R.string.refuelings_plural),"fuel",filters.kind==EntryKind.REFUELING.name,enabled) { viewModel.changeFilters(filters.copy(kind=EntryKind.REFUELING.name)) }
            JournalChip(stringResource(R.string.expenses_plural),"expense",filters.kind==EntryKind.EXPENSE.name,enabled) { viewModel.changeFilters(filters.copy(kind=EntryKind.EXPENSE.name)) }
            JournalChip(stringResource(R.string.entry_service),"service",filters.kind==EntryKind.SERVICE.name,enabled) { viewModel.changeFilters(filters.copy(kind=EntryKind.SERVICE.name)) }
            JournalChip(stringResource(R.string.entry_odometer),"road",filters.kind==EntryKind.ODOMETER.name,enabled) { viewModel.changeFilters(filters.copy(kind=EntryKind.ODOMETER.name)) }
        }
        JournalChoice(R.string.expense_category,filters.category,listOf("" to R.string.all_categories)+ExpenseCategory.entries.map { it.name to it.label },enabled) {
            viewModel.changeFilters(filters.copy(category=it))
        }
        Text(stringResource(R.string.journal_default_period),style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
        DateField(filters.dateFrom,{ viewModel.changeFilters(filters.copy(dateFrom=it)) },stringResource(R.string.date_from),enabled,state.fields["dateFrom"]?.let { stringResource(it) })
        DateField(filters.dateTo,{ viewModel.changeFilters(filters.copy(dateTo=it)) },stringResource(R.string.date_to),enabled,state.fields["dateTo"]?.let { stringResource(it) })
        Button(onClick={ if(viewModel.applyFilters()) onDone() },enabled=enabled,shape=RoundedCornerShape(16.dp),modifier=Modifier.fillMaxWidth().heightIn(min=52.dp)) {
            Text(stringResource(R.string.show_operations))
        }
        OutlinedButton(onClick={ viewModel.resetFilters(); onDone() },enabled=enabled,shape=RoundedCornerShape(16.dp),modifier=Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.reset_filters))
        }
    }
}
