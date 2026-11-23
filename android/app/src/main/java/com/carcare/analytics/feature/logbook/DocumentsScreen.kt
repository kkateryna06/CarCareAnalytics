package com.carcare.analytics.feature.logbook

import com.carcare.analytics.core.designsystem.appLocale
import android.icu.text.PluralRules
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.carcare.analytics.R
import com.carcare.analytics.core.designsystem.CareIcon
import com.carcare.analytics.core.designsystem.CareSymbol
import com.carcare.analytics.core.designsystem.DeleteConfirmationDialog
import com.carcare.analytics.core.designsystem.EmptyPanel
import com.carcare.analytics.core.designsystem.FailureCard
import com.carcare.analytics.core.designsystem.LoadingCards
import com.carcare.analytics.feature.insights.OverviewIcon
import com.carcare.analytics.feature.insights.TerracottaVehicle
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit

private val documentDateFormat = DateTimeFormatter.ofPattern("dd.MM.uuuu")

@Composable
internal fun DocumentsScreen(
    state: LogbookState,
    viewModel: LogbookViewModel,
    onGarage: () -> Unit,
    onBack: () -> Unit,
    onAdd: () -> Unit,
    onEdit: (Long) -> Unit
) {
    var pendingDelete by rememberSaveable { mutableStateOf<Long?>(null) }
    val groups = remember(state.documents) { state.documents.groupBy(JournalRecordDto::type) }
    val selected = state.appliedFilters.documentType
    val enabled = !state.deleting
    val back = stringResource(R.string.back)
    val today = LocalDate.now()

    Scaffold(
        contentWindowInsets=WindowInsets.statusBars,
        bottomBar={
            Surface(color=MaterialTheme.colorScheme.background) {
                Button(
                    onClick=onAdd,enabled=enabled,shape=RoundedCornerShape(16.dp),
                    modifier=Modifier.fillMaxWidth().padding(horizontal=20.dp,vertical=10.dp).heightIn(min=54.dp)
                ) {
                    CareIcon(CareSymbol.PLUS,Modifier.size(23.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.document_add),style=MaterialTheme.typography.titleMedium)
                }
            }
        }
    ) { padding ->
        LazyColumn(
            Modifier.fillMaxSize().padding(padding),
            contentPadding=PaddingValues(start=20.dp,end=20.dp,top=8.dp,bottom=12.dp),
            verticalArrangement=Arrangement.spacedBy(12.dp)
        ) {
            item {
                Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(12.dp)) {
                    FilledTonalIconButton(
                        onClick=onBack,enabled=enabled,shape=RoundedCornerShape(14.dp),
                        modifier=Modifier.size(48.dp).semantics { contentDescription=back }
                    ) { OverviewIcon("left",Modifier.size(22.dp),LocalContentColor.current) }
                    Text("CarCare",style=MaterialTheme.typography.headlineSmall)
                }
            }
            state.vehicle?.let { vehicle -> item { TerracottaVehicle(vehicle,onGarage.takeIf { enabled }) } }
            item {
                Text(stringResource(R.string.document_list_title),fontSize=34.sp,lineHeight=40.sp,fontWeight=FontWeight.Bold,letterSpacing=(-1).sp)
            }
            if(groups.isNotEmpty() || selected.isNotEmpty()) {
                item {
                    LazyRow(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                        items(listOf(null)+DocumentType.entries,key={ it?.name.orEmpty() }) { type ->
                            FilterChip(
                                selected=selected==type?.name.orEmpty(),enabled=enabled,onClick={ viewModel.documentFilter(type) },
                                label={ Text(stringResource(type?.shortLabel ?: R.string.document_all)) },
                                shape=RoundedCornerShape(12.dp)
                            )
                        }
                    }
                }
            }
            state.failure?.let { failure -> item { FailureCard(failure,viewModel::retry) } }
            if(state.loading && groups.isEmpty()) item { LoadingCards() }
            if(!state.loading && groups.isEmpty() && state.failure==null) {
                item {
                    if(selected.isEmpty()) {
                        EmptyPanel(stringResource(R.string.document_empty_title),stringResource(R.string.documents_empty))
                    } else {
                        EmptyPanel(stringResource(R.string.document_filtered_empty)) {
                            TextButton(onClick={ viewModel.documentFilter(null) }) { Text(stringResource(R.string.document_show_all)) }
                        }
                    }
                }
            }
            items(groups.entries.sortedBy { it.key?.ordinal ?: Int.MAX_VALUE },key={ it.key?.name.orEmpty() }) { (_,records) ->
                val current = records.firstOrNull { it.active } ?: records.first()
                DocumentCard(current,records.filterNot { it.id==current.id },today,enabled,onEdit,{ pendingDelete=it })
            }
            if(state.loadingMore || state.deleting || (state.loading && groups.isNotEmpty())) {
                item { LinearProgressIndicator(Modifier.fillMaxWidth()) }
            } else if(state.nextPage<state.totalPages) {
                item {
                    OutlinedButton(onClick=viewModel::more,enabled=!state.refreshing,modifier=Modifier.fillMaxWidth(),shape=RoundedCornerShape(14.dp)) {
                        Text(stringResource(R.string.load_more))
                    }
                }
            }
        }
    }
    pendingDelete?.let { id ->
        DeleteConfirmationDialog(
            title=stringResource(R.string.document_delete_title),
            description=stringResource(R.string.document_delete_description),
            onDismiss={ pendingDelete=null },
            onConfirm={ pendingDelete=null; viewModel.delete(EntryKind.DOCUMENT,id) }
        )
    }
}

@Composable
private fun DocumentCard(
    record: JournalRecordDto,
    history: List<JournalRecordDto>,
    today: LocalDate,
    enabled: Boolean,
    onEdit: (Long) -> Unit,
    onDelete: (Long) -> Unit
) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    var menu by remember { mutableStateOf(false) }
    val edit = stringResource(R.string.document_edit)
    val actions = stringResource(R.string.document_actions)
    val title = stringResource(record.type?.label ?: R.string.entry_document)
    OutlinedCard(
        modifier=Modifier.fillMaxWidth(),shape=RoundedCornerShape(18.dp),
        colors=CardDefaults.outlinedCardColors(containerColor=Color.Transparent),
        border=BorderStroke(1.dp,MaterialTheme.colorScheme.outlineVariant)
    ) {
        Column(Modifier.padding(12.dp),verticalArrangement=Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment=Alignment.Top,modifier=Modifier.fillMaxWidth()) {
                Surface(shape=RoundedCornerShape(13.dp),color=MaterialTheme.colorScheme.secondaryContainer) {
                    Box(Modifier.size(45.dp),contentAlignment=Alignment.Center) {
                        OverviewIcon(record.type.documentIcon,Modifier.size(25.dp))
                    }
                }
                Column(Modifier.weight(1f).padding(start=10.dp,top=2.dp),verticalArrangement=Arrangement.spacedBy(3.dp)) {
                    Text(title,style=MaterialTheme.typography.titleMedium,fontWeight=FontWeight.Bold)
                    DocumentExpiry(record.validUntil)
                }
                IconButton(onClick={ onEdit(record.id) },enabled=enabled,modifier=Modifier.semantics { contentDescription=edit }) {
                    OverviewIcon("note",Modifier.size(21.dp),LocalContentColor.current)
                }
                Box {
                    IconButton(onClick={ menu=true },enabled=enabled,modifier=Modifier.semantics { contentDescription=actions }) {
                        OverviewIcon("more",Modifier.size(21.dp),LocalContentColor.current)
                    }
                    DropdownMenu(expanded=menu,onDismissRequest={ menu=false }) {
                        DropdownMenuItem(
                            text={ Text(stringResource(R.string.document_delete),color=MaterialTheme.colorScheme.error) },
                            onClick={ menu=false; onDelete(record.id) }
                        )
                    }
                }
            }
            DocumentStatus(record,today)
            record.note?.takeIf(String::isNotBlank)?.let {
                Text(it,style=MaterialTheme.typography.bodyMedium,color=MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if(history.isNotEmpty()) {
                HorizontalDivider()
                TextButton(onClick={ expanded=!expanded },enabled=enabled,contentPadding=PaddingValues(horizontal=6.dp)) {
                    OverviewIcon(if(expanded) "up" else "down",Modifier.size(18.dp),LocalContentColor.current)
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.document_history))
                }
                if(expanded) history.forEach { older ->
                    OutlinedCard(onClick={ onEdit(older.id) },enabled=enabled,modifier=Modifier.fillMaxWidth(),shape=RoundedCornerShape(12.dp)) {
                        Row(
                            Modifier.fillMaxWidth().padding(horizontal=12.dp,vertical=9.dp),verticalAlignment=Alignment.CenterVertically,
                            horizontalArrangement=Arrangement.spacedBy(8.dp)
                        ) {
                            Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(3.dp)) {
                                DocumentExpiry(older.validUntil)
                                older.note?.takeIf(String::isNotBlank)?.let {
                                    Text(it,style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                            OverviewIcon("right",Modifier.size(16.dp))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DocumentExpiry(value: String?) {
    value?.takeIf(String::isNotBlank)?.let {
        val date = parseDocumentDate(it)?.format(documentDateFormat) ?: it
        Text(stringResource(R.string.document_until_date,date),style=MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun DocumentStatus(record: JournalRecordDto,today: LocalDate) {
    val days = parseDocumentDate(record.validUntil)?.let { ChronoUnit.DAYS.between(today,it) }
    val label = when(record.status) {
        "OVERDUE" -> stringResource(R.string.status_overdue)
        "DUE" -> stringResource(R.string.status_due)
        "UPCOMING","VALID" -> when {
            days!=null && days>0 -> stringResource(
                when(PluralRules.forLocale(appLocale()).select(days.toDouble())) {
                    "one" -> R.string.document_days_one
                    "few" -> R.string.document_days_few
                    else -> R.string.document_days_many
                },days
            )
            record.status=="UPCOMING" -> stringResource(R.string.status_upcoming)
            else -> stringResource(R.string.status_valid)
        }
        else -> return
    }
    val colors = MaterialTheme.colorScheme
    val urgent = record.status=="DUE" || record.status=="OVERDUE"
    val soon = record.status=="UPCOMING"
    Surface(
        shape=RoundedCornerShape(20.dp),
        color=when { urgent -> colors.errorContainer; soon -> colors.primaryContainer; else -> colors.secondaryContainer },
        contentColor=when { urgent -> colors.onErrorContainer; soon -> colors.onPrimaryContainer; else -> colors.onSecondaryContainer }
    ) {
        Text(label,Modifier.padding(horizontal=10.dp,vertical=5.dp),style=MaterialTheme.typography.labelMedium,fontWeight=FontWeight.Bold)
    }
}

@Composable
internal fun DocumentTypeSelector(value: String,enabled: Boolean,invalid: Boolean,onChange: (String) -> Unit) {
    val colors = MaterialTheme.colorScheme
    Surface(
        modifier=Modifier.fillMaxWidth().selectableGroup(),shape=RoundedCornerShape(14.dp),color=Color.Transparent,
        border=BorderStroke(1.dp,if(invalid) colors.error else colors.outlineVariant)
    ) {
        Row(Modifier.padding(3.dp),horizontalArrangement=Arrangement.spacedBy(3.dp)) {
            DocumentType.entries.forEach { type ->
                val selected = type.name==value
                Surface(
                    modifier=Modifier.weight(1f),shape=RoundedCornerShape(11.dp),
                    color=if(selected) colors.secondaryContainer else Color.Transparent
                ) {
                    Row(
                        Modifier.heightIn(min=48.dp).selectable(selected=selected,enabled=enabled,role=Role.RadioButton,onClick={ onChange(type.name) })
                            .padding(horizontal=6.dp,vertical=8.dp),
                        horizontalArrangement=Arrangement.spacedBy(6.dp,Alignment.CenterHorizontally),
                        verticalAlignment=Alignment.CenterVertically
                    ) {
                        OverviewIcon(type.documentIcon,Modifier.size(21.dp))
                        Text(stringResource(type.shortLabel),style=MaterialTheme.typography.bodyMedium,fontWeight=if(selected) FontWeight.Bold else FontWeight.Normal)
                    }
                }
            }
        }
    }
}

private val DocumentType.shortLabel: Int
    get() = if(this==DocumentType.INSURANCE) R.string.document_insurance_short else label

private val DocumentType?.documentIcon: String
    get() = if(this==DocumentType.INSURANCE) "shield" else "title"

private fun parseDocumentDate(value: String?): LocalDate? =
    value?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
