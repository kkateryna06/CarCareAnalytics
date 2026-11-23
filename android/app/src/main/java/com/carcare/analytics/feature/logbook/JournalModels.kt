package com.carcare.analytics.feature.logbook

import com.carcare.analytics.R

enum class EntryKind(val resource: String, val label: Int) {
    ODOMETER("odometer",R.string.entry_odometer), REFUELING("refuelings",R.string.entry_refueling),
    EXPENSE("expenses",R.string.entry_expense), SERVICE("services",R.string.entry_service), DOCUMENT("documents",R.string.entry_document)
}
enum class ExpenseCategory(val label: Int) {
    FUEL(R.string.category_fuel), SERVICE(R.string.entry_service), INSURANCE(R.string.document_insurance), TAXES(R.string.category_taxes),
    PARKING(R.string.category_parking), WASH(R.string.category_wash), OTHER(R.string.other)
}
enum class ServiceType(val label: Int) { REPAIR(R.string.service_repair), OIL_CHANGE(R.string.service_oil), TIRES(R.string.service_tires), BRAKES(R.string.service_brakes), INSPECTION(R.string.service_inspection), OTHER(R.string.other) }
enum class DocumentType(val label: Int) { INSURANCE(R.string.document_insurance), INSPECTION(R.string.document_inspection) }

// Flat network projection shared by the five resource forms. Decimal values remain strings.
data class JournalRecordDto(
    val id: Long, val vehicleId: Long, val date: String? = null, val odometerKm: Int? = null,
    val note: String? = null, val sourceType: String? = null, val liters: String? = null,
    val totalCostPln: String? = null, val pricePerLiterPln: String? = null, val isFullTank: Boolean? = null,
    val station: String? = null, val category: ExpenseCategory? = null, val amountPln: String? = null,
    val title: String? = null, val description: String? = null, val sourceId: Long? = null,
    val serviceType: ServiceType? = null, val workshop: String? = null, val costPln: String? = null,
    val nextDueDate: String? = null, val nextDueOdometerKm: Int? = null,
    val type: DocumentType? = null, val validUntil: String? = null, val active: Boolean = false, val status: String? = null
) {
    fun fields(): Map<String,String> = mapOf("date" to date.orEmpty(),"odometerKm" to (odometerKm?.toString().orEmpty()),"note" to note.orEmpty(),
        "liters" to liters.orEmpty(),"totalCostPln" to totalCostPln.orEmpty(),"isFullTank" to (isFullTank?:false).toString(),"station" to station.orEmpty(),
        "category" to category?.name.orEmpty(),"amountPln" to amountPln.orEmpty(),"title" to title.orEmpty(),"description" to description.orEmpty(),
        "serviceType" to serviceType?.name.orEmpty(),"workshop" to workshop.orEmpty(),"costPln" to costPln.orEmpty(),"nextDueDate" to nextDueDate.orEmpty(),
        "nextDueOdometerKm" to nextDueOdometerKm?.toString().orEmpty(),"type" to type?.name.orEmpty(),"validUntil" to validUntil.orEmpty())
}
data class JournalPage<T>(val content: List<T>, val page: Int, val size: Int, val totalElements: Long, val totalPages: Int)
data class TimelineDto(val id: Long,val kind: EntryKind,val date: String,val title: String,val amountPln: String?,val odometerKm: Int?,val category: ExpenseCategory?)

enum class FieldType { TEXT, MULTILINE, DATE, INTEGER, MONEY, LITERS, CHOICE, CHECKBOX }
data class JournalField(val key: String,val label: Int,val type: FieldType = FieldType.TEXT,val required: Boolean = false,val maxLength: Int = 1000,val options: List<Pair<String,Int>> = emptyList())
fun fieldsFor(kind: EntryKind): List<JournalField> {
    val date=JournalField("date",R.string.operation_date,FieldType.DATE,true)
    val note=JournalField("note",R.string.operation_note,FieldType.MULTILINE)
    val km=JournalField("odometerKm",R.string.operation_odometer,FieldType.INTEGER,kind!=EntryKind.SERVICE)
    return when(kind) {
        EntryKind.ODOMETER -> listOf(date,km,note)
        EntryKind.REFUELING -> listOf(date,km,JournalField("liters",R.string.fuel_liters,FieldType.LITERS,true),JournalField("totalCostPln",R.string.total_cost,FieldType.MONEY,true),JournalField("isFullTank",R.string.full_tank,FieldType.CHECKBOX),JournalField("station",R.string.station,maxLength=120),note)
        EntryKind.EXPENSE -> listOf(date,JournalField("category",R.string.expense_category,FieldType.CHOICE,true,options=ExpenseCategory.entries.filterNot { it==ExpenseCategory.FUEL }.map { it.name to it.label }),JournalField("amountPln",R.string.total_cost,FieldType.MONEY,true),JournalField("title",R.string.expense_title,required=true,maxLength=200),JournalField("description",R.string.operation_description,FieldType.MULTILINE,maxLength=2000))
        EntryKind.SERVICE -> listOf(date,JournalField("serviceType",R.string.service_type,FieldType.CHOICE,true,options=ServiceType.entries.map { it.name to it.label }),km,JournalField("description",R.string.operation_description,FieldType.MULTILINE,true,2000),JournalField("workshop",R.string.workshop,maxLength=120),JournalField("costPln",R.string.service_cost,FieldType.MONEY),JournalField("nextDueDate",R.string.next_due_date,FieldType.DATE),JournalField("nextDueOdometerKm",R.string.next_due_odometer,FieldType.INTEGER))
        EntryKind.DOCUMENT -> listOf(JournalField("type",R.string.document_type,FieldType.CHOICE,true,options=DocumentType.entries.map { it.name to it.label }),JournalField("validUntil",R.string.valid_until,FieldType.DATE,true),note)
    }
}
