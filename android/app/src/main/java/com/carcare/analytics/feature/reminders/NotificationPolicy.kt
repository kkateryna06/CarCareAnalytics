package com.carcare.analytics.feature.reminders

import com.carcare.analytics.feature.insights.*
import java.time.LocalDate
import java.time.temporal.ChronoUnit

data class LocalReminderState(val status: ReminderStatus,val stage: String?,val notificationKey: String?,val remainingDays: Long?,val remainingKm: Long?)

internal data class EvaluatedReminder(val reminder: ReminderDto, val timing: LocalReminderState)

internal fun sortedReminders(data: RemindersDto,today: LocalDate): List<EvaluatedReminder> = data.content
    .map { EvaluatedReminder(it,evaluateReminder(it,today,data.currentOdometerKm)) }
    .sortedWith(compareBy<EvaluatedReminder> { it.timing.status.ordinal }
        .thenBy { it.timing.remainingDays ?: Long.MAX_VALUE }
        .thenBy { it.timing.remainingKm ?: Long.MAX_VALUE }
        .thenBy { it.reminder.key })

fun evaluateReminder(reminder: ReminderDto,today: LocalDate,currentKm: Int): LocalReminderState {
    val days=reminder.dueDate?.let { ChronoUnit.DAYS.between(today,LocalDate.parse(it)) }
    val km=reminder.dueOdometerKm?.let { it.toLong()-currentKm }
    if(reminder.status==ReminderStatus.DONE) return LocalReminderState(ReminderStatus.DONE,null,null,days,km)
    val status=when {
        days?.let { it<0 }==true || km?.let { it<0 }==true -> ReminderStatus.OVERDUE
        days==0L || km==0L -> ReminderStatus.DUE
        days?.let { it<=30 }==true || km?.let { it<=500 }==true -> ReminderStatus.UPCOMING
        else -> ReminderStatus.SCHEDULED
    }
    val dateStage=days?.let { when { it<0 -> "DATE_OVERDUE"; it==0L -> "DATE_DUE"; it<=1 -> "D1"; it<=7 -> "D7"; it<=30 -> "D30"; else -> null } }
    val kmStage=km?.let { when { it<0 -> "KM_OVERDUE"; it==0L -> "KM_DUE"; it<=100 -> "K100"; it<=500 -> "K500"; else -> null } }
    val stage=listOfNotNull(dateStage,kmStage).takeIf { it.isNotEmpty() }?.joinToString("|")
    return LocalReminderState(status,stage,stage?.let { "${reminder.key}:$it" },days,km)
}
