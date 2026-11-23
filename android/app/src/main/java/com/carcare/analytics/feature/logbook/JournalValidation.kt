package com.carcare.analytics.feature.logbook

import com.carcare.analytics.R
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.LocalDate

private val maxFuelLiters=BigDecimal("9999999.999")
private val maxMoney=BigDecimal("9999999999.99")

private fun validDecimal(value: String,type: FieldType,zeroAllowed: Boolean = false): BigDecimal? {
    val decimal=value.trim().replace(',','.').toBigDecimalOrNull() ?: return null
    val liters=type==FieldType.LITERS
    val maximum=if(liters) maxFuelLiters else maxMoney
    val scale=if(liters) 3 else 2
    return decimal.takeIf { it.signum()>=0 && (zeroAllowed || it.signum()>0) && it<=maximum && it.scale()<=scale }
}

internal fun refuelingPricePerLiter(liters: String,cost: String): BigDecimal? {
    val volume=validDecimal(liters,FieldType.LITERS) ?: return null
    val total=validDecimal(cost,FieldType.MONEY) ?: return null
    return total.divide(volume,2,RoundingMode.HALF_UP)
}

fun validateOperation(kind: EntryKind,values: Map<String,String>,today: LocalDate = LocalDate.now()): Map<String,Int> = buildMap {
    for(field in fieldsFor(kind)) {
        val value=values[field.key].orEmpty().trim()
        if(value.isEmpty()) { if(field.required) put(field.key,R.string.error_required); continue }
        when(field.type) {
            FieldType.INTEGER -> if(value.toIntOrNull()?.let { it>=0 } != true) put(field.key,R.string.error_odometer)
            FieldType.DATE -> {
                val date=runCatching { LocalDate.parse(value) }.getOrNull()
                if(date==null || (field.key=="date" && date.isAfter(today))) put(field.key,R.string.error_operation_date)
            }
            FieldType.MONEY,FieldType.LITERS -> {
                if(validDecimal(value,field.type,zeroAllowed=field.key=="costPln")==null) {
                    put(field.key,if(field.type==FieldType.LITERS) R.string.error_liters else R.string.error_amount)
                }
            }
            FieldType.CHOICE -> if(field.options.none { it.first==value }) put(field.key,R.string.error_required)
            else -> if(value.length>field.maxLength) put(field.key,R.string.error_length)
        }
    }
    val date=runCatching { LocalDate.parse(values["date"]) }.getOrNull()
    val next=runCatching { LocalDate.parse(values["nextDueDate"]) }.getOrNull()
    if(date!=null && next!=null && next.isBefore(date)) put("nextDueDate",R.string.error_next_date)
    val km=values["odometerKm"]?.toIntOrNull(); val nextKm=values["nextDueOdometerKm"]?.toIntOrNull()
    if(km!=null && nextKm!=null && nextKm<km) put("nextDueOdometerKm",R.string.error_next_odometer)
}

fun operationPayload(kind: EntryKind,values: Map<String,String>): Map<String,Any?> = fieldsFor(kind).associate { field ->
    val value=values[field.key].orEmpty().trim()
    field.key to when {
        field.type==FieldType.CHECKBOX -> value.toBoolean()
        value.isEmpty() -> null
        field.type==FieldType.INTEGER -> value.toInt()
        field.type==FieldType.MONEY || field.type==FieldType.LITERS -> value.replace(',','.').toBigDecimal().toPlainString()
        else -> value
    }
}

fun validateFilters(filters: JournalFilters): Map<String,Int> = buildMap {
    for((key,value) in listOf("dateFrom" to filters.dateFrom,"dateTo" to filters.dateTo)) {
        if(value.isNotBlank() && runCatching { LocalDate.parse(value) }.isFailure) put(key,R.string.error_filter_date)
    }
    val from=runCatching { LocalDate.parse(filters.dateFrom) }.getOrNull(); val to=runCatching { LocalDate.parse(filters.dateTo) }.getOrNull()
    if(from!=null && to!=null && from.isAfter(to)) put("dateTo",R.string.error_date_range)
}
