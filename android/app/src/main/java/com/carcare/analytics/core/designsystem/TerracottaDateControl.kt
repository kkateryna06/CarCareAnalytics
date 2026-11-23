package com.carcare.analytics.core.designsystem

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.carcare.analytics.R
import com.carcare.analytics.feature.insights.OverviewIcon
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

private val formDateFormat = DateTimeFormatter.ofPattern("dd.MM.uuuu")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun TerracottaDateControl(
    value: String, pickerLabelId: Int, enabled: Boolean, invalid: Boolean, onChange: (String)->Unit,
    earliest: LocalDate? = null, latest: LocalDate? = null, clearLabelId: Int? = null
) {
    var pickerOpen by rememberSaveable { mutableStateOf(false) }
    val date=runCatching { LocalDate.parse(value) }.getOrNull()
    val pickerLabel=stringResource(pickerLabelId)
    Surface(
        onClick={ pickerOpen=true },enabled=enabled,
        shape=RoundedCornerShape(14.dp),
        color=Color.Transparent,
        border=BorderStroke(1.dp,if(invalid) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.outline),
        modifier=Modifier.fillMaxWidth().heightIn(min=52.dp).semantics { contentDescription=pickerLabel }
    ) {
        Row(Modifier.padding(horizontal=14.dp,vertical=10.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(12.dp)) {
            CompositionLocalProvider(LocalContentColor provides MaterialTheme.colorScheme.primary) { CareIcon(CareSymbol.CALENDAR) }
            Text(date?.format(formDateFormat) ?: pickerLabel,modifier=Modifier.weight(1f),fontWeight=FontWeight.SemiBold,fontSize=17.sp)
            if(clearLabelId!=null && value.isNotBlank()) {
                val clearLabel=stringResource(clearLabelId)
                IconButton(onClick={ onChange("") },enabled=enabled,modifier=Modifier.size(48.dp).semantics { contentDescription=clearLabel }) {
                    OverviewIcon("close",Modifier.size(18.dp))
                }
            } else OverviewIcon("down",Modifier.size(18.dp))
        }
    }
    if(pickerOpen) {
        val minMillis=earliest?.utcMillis() ?: Long.MIN_VALUE
        val maxMillis=latest?.utcMillis() ?: Long.MAX_VALUE
        val selectableDates=remember(minMillis,maxMillis) {
            object: SelectableDates {
                override fun isSelectableDate(utcTimeMillis: Long)=utcTimeMillis in minMillis..maxMillis
                override fun isSelectableYear(year: Int)=year in (earliest?.year ?: Int.MIN_VALUE)..(latest?.year ?: Int.MAX_VALUE)
            }
        }
        val selected=date?.utcMillis()?.takeIf { it in minMillis..maxMillis }
        val displayedMonth=selected ?: earliest?.utcMillis() ?: LocalDate.now().utcMillis()
        val years=minOf(1900,date?.year ?: 1900,earliest?.year ?: 1900)..maxOf(2100,date?.year ?: 2100,earliest?.year ?: 2100)
        val picker=rememberDatePickerState(
            initialSelectedDateMillis=selected,initialDisplayedMonthMillis=displayedMonth,yearRange=years,selectableDates=selectableDates
        )
        DatePickerDialog(
            onDismissRequest={ pickerOpen=false },
            confirmButton={
                TextButton(onClick={
                    picker.selectedDateMillis?.let { onChange(Instant.ofEpochMilli(it).atOffset(ZoneOffset.UTC).toLocalDate().toString()) }
                    pickerOpen=false
                },enabled=picker.selectedDateMillis!=null) { Text(stringResource(R.string.expense_date_confirm)) }
            },
            dismissButton={ TextButton(onClick={ pickerOpen=false }) { Text(stringResource(R.string.cancel)) } }
        ) { DatePicker(picker) }
    }
}

private fun LocalDate.utcMillis(): Long = atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
