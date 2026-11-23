package com.carcare.analytics.feature.reminders

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.carcare.analytics.R
import com.carcare.analytics.core.designsystem.EmptyPanel
import com.carcare.analytics.core.designsystem.FailureCard
import com.carcare.analytics.core.designsystem.LoadingCards
import com.carcare.analytics.feature.insights.OverviewIcon
import com.carcare.analytics.feature.insights.ReminderStatus
import com.carcare.analytics.feature.insights.TerracottaVehicle
import com.carcare.analytics.feature.insights.overviewNumber
import com.carcare.analytics.feature.insights.reminderLabel
import com.carcare.analytics.feature.logbook.EntryKind
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import android.icu.text.PluralRules
import com.carcare.analytics.core.designsystem.appLocale
import kotlin.math.abs

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RemindersScreen(
    onGarage: () -> Unit,
    onEdit: (EntryKind, Long) -> Unit,
    onDocuments: () -> Unit,
    viewModel: RemindersViewModel = hiltViewModel(),
    settingsViewModel: NotificationSettingsViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val preference by settingsViewModel.preference.collectAsStateWithLifecycle()
    val allowed by settingsViewModel.permissionAllowed.collectAsStateWithLifecycle()
    var notificationsOpen by rememberSaveable { mutableStateOf(false) }
    val notificationLabel = when {
        !preference.enabled -> R.string.reminders_notifications_off
        !allowed -> R.string.reminders_notifications_blocked
        else -> R.string.reminders_notifications_on
    }
    val data = state.data
    val today = LocalDate.now()
    val ordered = remember(data?.reminders, today) {
        data?.reminders?.let { sortedReminders(it,today) }.orEmpty()
    }
    val active = ordered.filter { it.timing.status!=ReminderStatus.DONE }
    val completed = ordered.filter { it.timing.status==ReminderStatus.DONE }
    val canOpen = !state.busy && !state.stale
    val refreshLabel=stringResource(R.string.refresh)
    val openSource: (EvaluatedReminder) -> Unit = {
        val source=it.reminder
        onEdit(if(source.sourceKind=="DOCUMENT") EntryKind.DOCUMENT else EntryKind.SERVICE,source.sourceId)
    }

    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        settingsViewModel.refreshPermission()
        viewModel.resume()
    }
    Scaffold(contentWindowInsets=WindowInsets.statusBars) { padding ->
        LazyColumn(
            Modifier.fillMaxSize().padding(padding),
            contentPadding=PaddingValues(start=20.dp,end=20.dp,top=12.dp,bottom=20.dp),
            verticalArrangement=Arrangement.spacedBy(12.dp)
        ) {
            item { Text("CarCare",style=MaterialTheme.typography.headlineSmall,modifier=Modifier.padding(bottom=6.dp)) }
            data?.let {
                item {
                    TerracottaVehicle(it.vehicleLabel,stringResource(R.string.vehicle_odometer_value,overviewNumber(it.reminders.currentOdometerKm.toString(),0)),onGarage)
                }
            }
            item {
                Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) {
                    Text(stringResource(R.string.reminders_title),Modifier.weight(1f),fontSize=34.sp,lineHeight=40.sp,fontWeight=FontWeight.Bold,letterSpacing=(-1).sp)
                    IconButton(
                        onClick=viewModel::refresh,enabled=!state.loading && !state.busy,
                        modifier=Modifier.semantics { contentDescription=refreshLabel }
                    ) {
                        OverviewIcon("refresh",Modifier.size(22.dp),LocalContentColor.current)
                    }
                }
            }
            item {
                ReminderShortcut(stringResource(notificationLabel),"bell",highlighted=true) { notificationsOpen=true }
            }
            item { ReminderShortcut(stringResource(R.string.documents),"title",onClick=onDocuments) }
            if(state.loading && data==null) {
                item { LoadingCards() }
            } else if(state.busy) {
                item { LinearProgressIndicator(Modifier.fillMaxWidth()) }
            }
            state.failure?.let { failure -> item { FailureCard(failure,viewModel::refresh) } }
            if(state.stale && data!=null) {
                item {
                    Text(
                        stringResource(R.string.reminder_cache_caption,data.syncedAt),
                        style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            if(data!=null) {
                if(active.isEmpty()) {
                    item {
                        EmptyPanel(stringResource(R.string.reminders_empty_title),stringResource(R.string.no_reminders))
                    }
                }
                items(active,key={ it.reminder.key }) { row ->
                    ReminderCard(row,today,canOpen,{ openSource(row) },{ viewModel.complete(row.reminder.sourceId,true) })
                }
                item {
                    CompletedSwitch(state.includeDone,!state.busy,viewModel::showDone)
                }
                if(state.includeDone) {
                    item {
                        Text(stringResource(R.string.reminders_completed_section),style=MaterialTheme.typography.titleMedium,modifier=Modifier.padding(top=8.dp))
                    }
                    if(completed.isEmpty()) {
                        item { Text(stringResource(R.string.reminders_completed_empty),color=MaterialTheme.colorScheme.onSurfaceVariant) }
                    }
                    items(completed,key={ it.reminder.key }) { row ->
                        ReminderCard(row,today,canOpen,{ openSource(row) },{ viewModel.complete(row.reminder.sourceId,false) })
                    }
                }
            }
        }
    }

    if(notificationsOpen) {
        ModalBottomSheet(
            onDismissRequest={ notificationsOpen=false },
            sheetState=rememberModalBottomSheetState(skipPartiallyExpanded=true),
            containerColor=MaterialTheme.colorScheme.background
        ) {
            Column(Modifier.verticalScroll(rememberScrollState()).padding(start=20.dp,end=20.dp,bottom=24.dp)) {
                NotificationSettings(settingsViewModel)
            }
        }
    }
}

@Composable
private fun ReminderShortcut(label: String, icon: String, highlighted: Boolean = false, onClick: () -> Unit) {
    Surface(
        onClick=onClick,modifier=Modifier.fillMaxWidth(),shape=RoundedCornerShape(16.dp),
        color=if(highlighted) MaterialTheme.colorScheme.secondaryContainer else Color.Transparent,
        border=if(highlighted) null else BorderStroke(1.dp,MaterialTheme.colorScheme.outlineVariant)
    ) {
        Row(Modifier.padding(horizontal=16.dp,vertical=14.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(12.dp)) {
            OverviewIcon(icon,Modifier.size(23.dp))
            Text(label,Modifier.weight(1f),style=MaterialTheme.typography.bodyLarge,fontWeight=FontWeight.Medium)
            OverviewIcon("right",Modifier.size(18.dp))
        }
    }
}

@Composable
private fun CompletedSwitch(checked: Boolean, enabled: Boolean, onCheck: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth().toggleable(checked,enabled=enabled,role=Role.Switch,onValueChange=onCheck).padding(vertical=6.dp),
        verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(12.dp)
    ) {
        Text(stringResource(R.string.show_completed),Modifier.weight(1f),style=MaterialTheme.typography.bodyLarge)
        Switch(checked,onCheckedChange=null,enabled=enabled)
    }
}

@Composable
private fun ReminderCard(
    row: EvaluatedReminder,
    today: LocalDate,
    enabled: Boolean,
    onOpen: () -> Unit,
    onToggleDone: () -> Unit
) {
    val reminder=row.reminder
    val timing=row.timing
    val done=timing.status==ReminderStatus.DONE
    val service=reminder.sourceKind=="SERVICE"
    val title=stringResource(reminderLabel(reminder))
    val icon=when {
        service -> "service"
        reminder.type=="INSURANCE" -> "shield"
        else -> "title"
    }
    val locale=appLocale()
    val dateLabel=reminder.dueDate?.let {
        val date=LocalDate.parse(it)
        date.format(DateTimeFormatter.ofPattern(if(date.year==today.year) "d MMMM" else "d MMMM uuuu",locale))
    }
    val mileageTarget=reminder.dueOdometerKm?.let { stringResource(R.string.vehicle_odometer_value,overviewNumber(it.toString(),0)) }
    OutlinedCard(
        Modifier.fillMaxWidth(),shape=RoundedCornerShape(18.dp),
        colors=CardDefaults.outlinedCardColors(containerColor=Color.Transparent),
        border=BorderStroke(1.dp,MaterialTheme.colorScheme.outlineVariant.copy(alpha=.8f))
    ) {
        Row(
            Modifier.fillMaxWidth().clickable(enabled=enabled,role=Role.Button,onClick=onOpen).padding(13.dp),
            verticalAlignment=Alignment.Top,horizontalArrangement=Arrangement.spacedBy(12.dp)
        ) {
            Surface(shape=RoundedCornerShape(14.dp),color=MaterialTheme.colorScheme.secondaryContainer) {
                Box(Modifier.size(46.dp),contentAlignment=Alignment.Center) { OverviewIcon(icon,Modifier.size(27.dp)) }
            }
            Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(4.dp)) {
                Text(title,style=MaterialTheme.typography.titleMedium,fontWeight=FontWeight.Bold)
                if(service && reminder.title.isNotBlank() && !reminder.title.equals(title,ignoreCase=true)) {
                    Text(reminder.title,style=MaterialTheme.typography.bodyMedium,maxLines=2,overflow=TextOverflow.Ellipsis)
                }
                if(done) {
                    Text(stringResource(R.string.reminder_done),style=MaterialTheme.typography.labelLarge,color=MaterialTheme.colorScheme.onSurfaceVariant)
                    listOfNotNull(dateLabel,mileageTarget).joinToString(" · ").takeIf { it.isNotBlank() }?.let {
                        Text(it,style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                } else {
                    val urgent=timing.status!=ReminderStatus.SCHEDULED
                    timing.remainingDays?.let { days ->
                        ReminderTiming(reminderDaysLabel(days),dateLabel,days<0,urgent)
                    }
                    timing.remainingKm?.let { km ->
                        ReminderTiming(mileageLabel(km),mileageTarget,km<0,urgent)
                    }
                }
            }
            Box(Modifier.align(Alignment.CenterVertically)) { OverviewIcon("right",Modifier.size(18.dp)) }
        }
        if(service) {
            FilledTonalButton(
                onClick=onToggleDone,enabled=enabled,shape=RoundedCornerShape(14.dp),
                modifier=Modifier.padding(start=14.dp,end=14.dp,bottom=9.dp)
            ) {
                Text(stringResource(if(done) R.string.reopen_reminder else R.string.reminders_mark_done))
            }
        }
    }
}

@Composable
private fun ReminderTiming(text: String, target: String?, overdue: Boolean, urgent: Boolean) {
    val colors=MaterialTheme.colorScheme
    val color=when {
        overdue -> colors.error
        urgent -> colors.primary
        else -> colors.onSurfaceVariant
    }
    Text(text,style=MaterialTheme.typography.labelLarge,color=color)
    target?.let { Text(it,style=MaterialTheme.typography.bodySmall,color=colors.onSurfaceVariant) }
}

@Composable
internal fun reminderDaysLabel(days: Long): String {
    if(days==0L) return stringResource(R.string.reminders_today)
    val count=abs(days)
    val suffix=when(PluralRules.forLocale(appLocale()).select(count.toDouble())) {
        "one" -> R.string.reminders_days_one
        "few" -> R.string.reminders_days_few
        else -> R.string.reminders_days_many
    }
    val duration=stringResource(suffix,overviewNumber(count.toString(),0))
    return stringResource(if(days<0) R.string.reminders_late else R.string.reminders_in,duration)
}

@Composable
private fun mileageLabel(km: Long): String {
    if(km==0L) return stringResource(R.string.reminders_mileage_due)
    val distance=stringResource(R.string.vehicle_odometer_value,overviewNumber(abs(km).toString(),0))
    return stringResource(if(km<0) R.string.reminders_late else R.string.reminders_in,distance)
}
