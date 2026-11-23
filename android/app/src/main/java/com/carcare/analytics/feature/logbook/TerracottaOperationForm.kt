package com.carcare.analytics.feature.logbook

import com.carcare.analytics.core.designsystem.localizedFieldError
import com.carcare.analytics.core.designsystem.appLocale
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.carcare.analytics.R
import com.carcare.analytics.core.designsystem.CareIcon
import com.carcare.analytics.core.designsystem.CareSymbol
import com.carcare.analytics.core.designsystem.FailureCard
import com.carcare.analytics.core.designsystem.TerracottaDateControl
import com.carcare.analytics.core.designsystem.label
import com.carcare.analytics.feature.insights.OverviewIcon
import com.carcare.analytics.feature.insights.TerracottaVehicle
import com.carcare.analytics.feature.insights.overviewNumber
import java.text.DecimalFormatSymbols
import java.time.LocalDate
import java.time.format.DateTimeFormatter

private val formDateFormat=DateTimeFormatter.ofPattern("dd.MM.uuuu")

@OptIn(ExperimentalMaterial3Api::class,ExperimentalLayoutApi::class)
@Composable
internal fun TerracottaOperationForm(
    viewModel: OperationFormViewModel,
    state: OperationFormState,
    onBack: () -> Unit,
    onDelete: () -> Unit
) {
    val vehicleState by viewModel.formVehicle.collectAsStateWithLifecycle()
    val editing=viewModel.id!=null
    val refueling=viewModel.kind==EntryKind.REFUELING
    val service=viewModel.kind==EntryKind.SERVICE
    val document=viewModel.kind==EntryKind.DOCUMENT
    val odometer=viewModel.kind==EntryKind.ODOMETER
    val latestDate=latestOperationDate(viewModel.kind)
    val fields=remember(viewModel.kind) { fieldsFor(viewModel.kind) }
    val enabled=state.loaded && !state.loading && !state.saving && !state.done
    val focusManager=LocalFocusManager.current
    val fieldRequesters=remember(fields) { fields.associate { it.key to BringIntoViewRequester() } }
    val invalidField=fields.firstOrNull { it.key in state.fields || state.failure?.fields?.containsKey(it.key)==true }
    val scheduled=state.values["nextDueDate"].orEmpty().isNotBlank() || state.values["nextDueOdometerKm"].orEmpty().isNotBlank()
    val deadlineInvalid=listOf("nextDueDate","nextDueOdometerKm").any { it in state.fields || state.failure?.fields?.containsKey(it)==true }
    var deadlineExpanded by rememberSaveable { mutableStateOf<Boolean?>(null) }
    val showDeadline=deadlineExpanded ?: (scheduled || deadlineInvalid)
    val backLabel=stringResource(R.string.back)
    val title=when {
        odometer && editing -> R.string.odometer_edit
        odometer -> R.string.odometer_new
        document && editing -> R.string.document_edit
        document -> R.string.document_new
        service && editing -> R.string.service_edit
        service -> R.string.service_form_title
        refueling && editing -> R.string.refueling_edit
        refueling -> R.string.refueling_new
        editing -> R.string.expense_edit
        else -> R.string.expense_new
    }
    val saveLabel=when {
        state.saving -> R.string.saving
        editing -> R.string.save_changes
        odometer -> R.string.odometer_save
        document -> R.string.document_save
        service -> R.string.service_save
        refueling -> R.string.refueling_save
        else -> R.string.expense_save
    }

    LaunchedEffect(viewModel) { viewModel.loadFormVehicle() }
    LaunchedEffect(invalidField?.key,deadlineInvalid,showDeadline) {
        if(deadlineInvalid && !showDeadline) deadlineExpanded=true
        else invalidField?.let { fieldRequesters.getValue(it.key).bringIntoView() }
    }
    BackHandler(enabled=state.saving || state.done) {}

    Scaffold(contentWindowInsets=WindowInsets.systemBars) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).consumeWindowInsets(padding).imePadding()) {
            Row(
                Modifier.fillMaxWidth().padding(start=20.dp,end=20.dp,top=8.dp,bottom=12.dp),
                horizontalArrangement=Arrangement.spacedBy(12.dp),
                verticalAlignment=Alignment.CenterVertically
            ) {
                FilledTonalIconButton(
                    onClick=onBack,
                    enabled=!state.saving && !state.done,
                    shape=RoundedCornerShape(14.dp),
                    modifier=Modifier.size(48.dp).semantics { contentDescription=backLabel }
                ) {
                    OverviewIcon("left",Modifier.size(23.dp))
                }
                Text(
                    stringResource(title),
                    fontSize=28.sp, lineHeight=34.sp, fontWeight=FontWeight.Bold, letterSpacing=(-.6).sp
                )
            }

            Column(
                Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState())
                    .padding(start=20.dp,end=20.dp,bottom=12.dp),
                verticalArrangement=Arrangement.spacedBy(if(refueling || service) 10.dp else 13.dp)
            ) {
                val vehicle=vehicleState.vehicle
                val vehicleError=vehicleState.failure
                when {
                    vehicle!=null && odometer && !editing -> TerracottaVehicle(
                        "${vehicle.make} ${vehicle.model}","${vehicle.year} · ${stringResource(vehicle.fuelType.label())}"
                    )
                    vehicle!=null -> TerracottaVehicle(vehicle)
                    vehicleError!=null -> FailureCard(vehicleError,viewModel::loadFormVehicle)
                    else -> Surface(
                        Modifier.fillMaxWidth().height(72.dp),
                        color=MaterialTheme.colorScheme.surfaceContainerLow,shape=RoundedCornerShape(18.dp)
                    ) {
                        Box(contentAlignment=Alignment.Center) { LinearProgressIndicator(Modifier.fillMaxWidth(.45f)) }
                    }
                }

                if(odometer) {
                    if(editing && state.loaded) {
                        Surface(shape=RoundedCornerShape(14.dp),color=MaterialTheme.colorScheme.secondaryContainer) {
                            Row(
                                Modifier.padding(horizontal=12.dp,vertical=8.dp),horizontalArrangement=Arrangement.spacedBy(8.dp),
                                verticalAlignment=Alignment.CenterVertically
                            ) {
                                OverviewIcon("note",Modifier.size(18.dp))
                                Text(stringResource(R.string.odometer_manual),style=MaterialTheme.typography.labelLarge)
                            }
                        }
                    } else if(!editing && vehicle!=null) CurrentMileage(vehicle.currentOdometerKm)
                }

                state.failure?.takeIf { !state.loaded || it.fields.isEmpty() }?.let {
                    FailureCard(it,if(!state.loaded) viewModel::load else null)
                }
                if(state.loading) LinearProgressIndicator(Modifier.fillMaxWidth())

                val renderField: @Composable (JournalField, Modifier) -> Unit = { field, modifier ->
                    val value=state.values[field.key].orEmpty()
                    val label=stringResource(when(field.key) {
                        "date" -> R.string.expense_date
                        "title" -> R.string.expense_name
                        "liters" -> R.string.refueling_volume
                        "note" -> when {
                            document -> R.string.document_note
                            odometer -> R.string.odometer_note
                            else -> R.string.refueling_note
                        }
                        "description" -> if(service) R.string.service_work_done else field.label
                        "costPln" -> R.string.service_price
                        "nextDueDate" -> R.string.service_due_date
                        "nextDueOdometerKm" -> R.string.service_due_mileage
                        else -> field.label
                    })
                    val localError=state.fields[field.key]
                    val error=localError?.let {
                        stringResource(if(field.key=="date" && it==R.string.error_operation_date) R.string.expense_date_invalid else it)
                    } ?: localizedFieldError(state.failure,field.key)
                    val change: (String)->Unit = { viewModel.change(field.key,it) }
                    Column(
                        modifier.fillMaxWidth().bringIntoViewRequester(fieldRequesters.getValue(field.key)),
                        verticalArrangement=Arrangement.spacedBy(5.dp)
                    ) {
                        FlowRow(horizontalArrangement=Arrangement.spacedBy(6.dp),itemVerticalAlignment=Alignment.CenterVertically) {
                            Text(label,style=MaterialTheme.typography.bodyLarge)
                            if(!field.required) Text(
                                stringResource(R.string.expense_optional),
                                color=MaterialTheme.colorScheme.onSurfaceVariant,style=MaterialTheme.typography.bodyMedium
                            )
                            if(service && field.key=="description") Text("*",color=MaterialTheme.colorScheme.error)
                        }
                        when(field.type) {
                            FieldType.DATE -> {
                                val nextDue=field.key=="nextDueDate"
                                val pickerLabel=when {
                                    document -> R.string.document_pick_date
                                    odometer -> R.string.odometer_pick_date
                                    nextDue -> R.string.service_pick_due_date
                                    service -> R.string.service_pick_date
                                    refueling -> R.string.refueling_pick_date
                                    else -> R.string.expense_date_picker
                                }
                                TerracottaDateControl(
                                    value,pickerLabel,enabled,error!=null,change,
                                    earliest=if(nextDue) parseOperationDate(state.values["date"]) else null,
                                    latest=if(nextDue || document) null else latestDate,
                                    clearLabelId=if(nextDue) R.string.service_clear_due_date else null
                                )
                            }
                            FieldType.CHOICE -> if(document) DocumentTypeSelector(value,enabled,error!=null,change)
                                else OperationChoiceControl(field,value,enabled,error!=null,change)
                            else -> OperationTextControl(field,label,value,enabled,error!=null,change,viewModel.kind)
                        }
                        error?.let { Text(it,color=MaterialTheme.colorScheme.error,style=MaterialTheme.typography.bodySmall) }
                        if(odometer && field.key=="date" && latestDate<LocalDate.now()) Text(
                            stringResource(R.string.odometer_server_date_hint,latestDate.format(formDateFormat)),
                            color=MaterialTheme.colorScheme.onSurfaceVariant,style=MaterialTheme.typography.bodySmall
                        )
                    }
                }

                if(refueling) {
                    val fuelFields=fields.associateBy { it.key }
                    renderField(fuelFields.getValue("date"),Modifier)
                    renderField(fuelFields.getValue("odometerKm"),Modifier)
                    val liters=fuelFields.getValue("liters")
                    val cost=fuelFields.getValue("totalCostPln")
                    BoxWithConstraints(Modifier.fillMaxWidth()) {
                        if(maxWidth>=320.dp && LocalDensity.current.fontScale<=1.25f) {
                            Row(horizontalArrangement=Arrangement.spacedBy(12.dp)) {
                                renderField(liters,Modifier.weight(1f))
                                renderField(cost,Modifier.weight(1f))
                            }
                        } else {
                            Column(verticalArrangement=Arrangement.spacedBy(10.dp)) {
                                renderField(liters,Modifier)
                                renderField(cost,Modifier)
                            }
                        }
                    }
                    FullTankControl(state.values["isFullTank"].toBoolean(),enabled) { viewModel.change("isFullTank",it.toString()) }
                    val price=remember(state.values["liters"],state.values["totalCostPln"]) {
                        refuelingPricePerLiter(state.values["liters"].orEmpty(),state.values["totalCostPln"].orEmpty())
                    }
                    price?.let { RefuelingPrice(overviewNumber(it.toPlainString())) }
                    renderField(fuelFields.getValue("station"),Modifier)
                    renderField(fuelFields.getValue("note"),Modifier)
                } else if(service) {
                    fields.filterNot { it.key.startsWith("nextDue") }.forEach { renderField(it,Modifier) }
                    val cost=state.values["costPln"].orEmpty()
                    if(cost.isBlank() || cost.replace(',','.').toBigDecimalOrNull()?.signum()==0) {
                        Text(stringResource(R.string.service_no_cost),style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    ServiceDeadlineHeader(state.values,showDeadline,enabled) { deadlineExpanded=!showDeadline }
                    if(showDeadline) {
                        fields.filter { it.key.startsWith("nextDue") }.forEach { renderField(it,Modifier) }
                        Text(stringResource(R.string.service_deadline_hint),style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                } else fields.forEach { renderField(it,Modifier) }

                if((document || odometer) && !editing) Row(
                    Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(10.dp),
                    verticalAlignment=Alignment.CenterVertically
                ) {
                    OverviewIcon("info",Modifier.size(23.dp))
                    Text(
                        stringResource(if(odometer) R.string.odometer_history_hint else R.string.document_reminder_hint),
                        style=MaterialTheme.typography.bodyMedium,color=MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            Column(Modifier.fillMaxWidth().padding(start=20.dp,end=20.dp,top=8.dp,bottom=16.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick={ focusManager.clearFocus(); viewModel.save() },
                    enabled=enabled,shape=RoundedCornerShape(16.dp),modifier=Modifier.fillMaxWidth().heightIn(min=54.dp)
                ) {
                    if(state.saving) CircularProgressIndicator(Modifier.size(18.dp),color=LocalContentColor.current,strokeWidth=2.dp)
                    else if(editing) OverviewIcon("check",Modifier.size(22.dp),LocalContentColor.current)
                    else if(!document) {
                        if(refueling) OverviewIcon("fuel",Modifier.size(23.dp),LocalContentColor.current)
                        else CareIcon(CareSymbol.PLUS,Modifier.size(22.dp))
                    }
                    if(state.saving || editing || !document) Spacer(Modifier.width(8.dp))
                    Text(stringResource(saveLabel),style=MaterialTheme.typography.titleMedium)
                }
                if(editing) OutlinedButton(
                    onClick=onDelete,enabled=enabled,shape=RoundedCornerShape(16.dp),
                    modifier=Modifier.fillMaxWidth().heightIn(min=48.dp),
                    colors=ButtonDefaults.outlinedButtonColors(contentColor=MaterialTheme.colorScheme.error)
                ) {
                    OverviewIcon("trash",Modifier.size(21.dp),LocalContentColor.current)
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(if(document) R.string.document_delete else R.string.delete_record))
                }
            }
        }
    }
}

@Composable
private fun CurrentMileage(km: Int) {
    Surface(
        modifier=Modifier.fillMaxWidth(),shape=RoundedCornerShape(16.dp),
        color=MaterialTheme.colorScheme.secondaryContainer
    ) {
        Row(
            Modifier.fillMaxWidth().heightIn(min=58.dp).padding(horizontal=15.dp,vertical=8.dp),
            horizontalArrangement=Arrangement.spacedBy(10.dp),verticalAlignment=Alignment.CenterVertically
        ) {
            Text(stringResource(R.string.odometer_now),style=MaterialTheme.typography.titleMedium)
            Text(
                stringResource(R.string.vehicle_odometer_value,overviewNumber(km.toString(),0)),
                modifier=Modifier.weight(1f),textAlign=TextAlign.End,
                style=MaterialTheme.typography.headlineSmall,fontWeight=FontWeight.Bold
            )
        }
    }
}

private fun parseOperationDate(value: String?): LocalDate? = value?.let { runCatching { LocalDate.parse(it) }.getOrNull() }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun OperationChoiceControl(field: JournalField, value: String, enabled: Boolean, invalid: Boolean, onChange: (String)->Unit) {
    var expanded by remember { mutableStateOf(false) }
    val selected=field.options.firstOrNull { it.first==value }
    val label=stringResource(field.label)
    ExposedDropdownMenuBox(expanded=expanded,onExpandedChange={ if(enabled) expanded=it }) {
        OutlinedTextField(
            value=selected?.let { stringResource(it.second) }.orEmpty(),onValueChange={},readOnly=true,singleLine=true,
            enabled=enabled,isError=invalid,shape=RoundedCornerShape(14.dp),
            textStyle=MaterialTheme.typography.bodyLarge.copy(fontWeight=FontWeight.SemiBold),
            leadingIcon={ OverviewIcon(if(field.key=="serviceType") "service" else "expense",Modifier.size(24.dp)) },
            trailingIcon={ ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
            modifier=Modifier.menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable).fillMaxWidth().semantics { contentDescription=label }
        )
        ExposedDropdownMenu(expanded=expanded,onDismissRequest={ expanded=false }) {
            field.options.forEach { (key,labelId) ->
                DropdownMenuItem(text={ Text(stringResource(labelId)) },onClick={ onChange(key); expanded=false })
            }
        }
    }
}

@Composable
private fun OperationTextControl(field: JournalField,label: String,value: String,enabled: Boolean,invalid: Boolean,onChange: (String)->Unit,kind: EntryKind) {
    val multiline=field.type==FieldType.MULTILINE
    val decimal=field.type==FieldType.MONEY || field.type==FieldType.LITERS
    val separator=DecimalFormatSymbols.getInstance(appLocale()).decimalSeparator
    val amountDisplay=VisualTransformation { input ->
        TransformedText(AnnotatedString(input.text.replace('.',separator).replace(',',separator)),OffsetMapping.Identity)
    }
    val tallerNote=field.key=="note" && (kind==EntryKind.DOCUMENT || kind==EntryKind.ODOMETER)
    val hint=stringResource(when(field.key) {
        "amountPln","totalCostPln","costPln" -> R.string.expense_amount_placeholder
        "liters" -> R.string.refueling_volume_placeholder
        "odometerKm" -> R.string.refueling_mileage_placeholder
        "station" -> R.string.refueling_station_placeholder
        "note" -> when(kind) {
            EntryKind.DOCUMENT -> R.string.document_note_placeholder
            EntryKind.ODOMETER -> R.string.odometer_note_placeholder
            else -> R.string.refueling_note_placeholder
        }
        "description" -> if(field.required) R.string.service_work_placeholder else R.string.expense_description_placeholder
        "workshop" -> R.string.service_workshop_placeholder
        "nextDueOdometerKm" -> R.string.service_due_mileage_placeholder
        else -> R.string.expense_name_placeholder
    })
    val icon=when(field.key) {
        "amountPln","totalCostPln","costPln" -> "money"
        "liters" -> "drop"
        "odometerKm","nextDueOdometerKm" -> "road"
        "station" -> "fuel"
        "note","description" -> "note"
        "workshop" -> "service"
        else -> "title"
    }
    OutlinedTextField(
        value=value,onValueChange=onChange,enabled=enabled,isError=invalid,
        shape=RoundedCornerShape(14.dp),singleLine=!multiline,minLines=if(field.key=="description" || tallerNote) 2 else 1,
        textStyle=MaterialTheme.typography.bodyLarge.copy(fontWeight=if(multiline) FontWeight.Normal else FontWeight.SemiBold),
        placeholder={ Text(hint,style=MaterialTheme.typography.bodyMedium,maxLines=if(multiline) 2 else 1) },
        leadingIcon={ OverviewIcon(icon,Modifier.size(24.dp)) },
        keyboardOptions=KeyboardOptions(keyboardType=when {
            decimal -> KeyboardType.Decimal
            field.type==FieldType.INTEGER -> KeyboardType.Number
            else -> KeyboardType.Text
        },imeAction=if(multiline) ImeAction.Default else ImeAction.Next),
        visualTransformation=if(decimal) amountDisplay else VisualTransformation.None,
        modifier=Modifier.fillMaxWidth().semantics { contentDescription=label }
    )
}

@Composable
private fun ServiceDeadlineHeader(values: Map<String,String>, expanded: Boolean, enabled: Boolean, onClick: () -> Unit) {
    val summary=listOfNotNull(
        parseOperationDate(values["nextDueDate"])?.format(formDateFormat),
        values["nextDueOdometerKm"]?.takeIf { it.isNotBlank() }?.let { stringResource(R.string.vehicle_odometer_value,overviewNumber(it,0)) }
    ).joinToString(" · ")
    Surface(onClick=onClick,enabled=enabled,modifier=Modifier.fillMaxWidth(),shape=RoundedCornerShape(14.dp),color=MaterialTheme.colorScheme.secondaryContainer) {
        Row(Modifier.padding(horizontal=14.dp,vertical=12.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(10.dp)) {
            CompositionLocalProvider(LocalContentColor provides MaterialTheme.colorScheme.primary) { CareIcon(CareSymbol.CALENDAR) }
            Column(Modifier.weight(1f)) {
                Text(stringResource(R.string.service_next),style=MaterialTheme.typography.titleMedium)
                if(!expanded && summary.isNotBlank()) Text(summary,style=MaterialTheme.typography.bodySmall)
            }
            OverviewIcon(if(expanded) "up" else "down",Modifier.size(20.dp))
        }
    }
}

@Composable
private fun FullTankControl(checked: Boolean, enabled: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Surface(color=MaterialTheme.colorScheme.surfaceContainerLow,shape=RoundedCornerShape(14.dp)) {
        Row(
            Modifier.fillMaxWidth().toggleable(checked,enabled=enabled,role=Role.Checkbox,onValueChange=onCheckedChange)
                .padding(horizontal=14.dp,vertical=9.dp),
            verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(12.dp)
        ) {
            Checkbox(checked,onCheckedChange=null,enabled=enabled)
            Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(2.dp)) {
                Text(stringResource(R.string.full_tank),style=MaterialTheme.typography.titleMedium)
                Text(stringResource(R.string.refueling_full_tank_hint),style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun RefuelingPrice(value: String) {
    Surface(color=MaterialTheme.colorScheme.secondaryContainer,shape=RoundedCornerShape(14.dp)) {
        Row(Modifier.fillMaxWidth().padding(horizontal=14.dp,vertical=10.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(10.dp)) {
            OverviewIcon("info",Modifier.size(21.dp))
            Text(stringResource(R.string.fuel_price,value),style=MaterialTheme.typography.titleMedium)
        }
    }
}
