package com.carcare.analytics.feature.vehicles

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.carcare.analytics.R
import com.carcare.analytics.core.designsystem.*
import com.carcare.analytics.core.network.FuelType
import com.carcare.analytics.feature.insights.OverviewIcon
import com.carcare.analytics.feature.insights.TerracottaVehicle
import com.carcare.analytics.feature.insights.overviewNumber
import java.time.LocalDate

private val detailsFields = setOf("generation","engineCapacityLiters","vin","registrationNumber","purchaseDate")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VehicleFormScreen(onBack: () -> Unit, viewModel: VehicleFormViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val editing = viewModel.id != null
    val enabled = state.loaded && !state.loading && !state.saving && !state.done && !state.preparingPhoto
    var details by rememberSaveable { mutableStateOf(false) }
    var saveAttempts by remember { mutableIntStateOf(0) }
    val mainScroll = rememberScrollState()
    val detailsScroll = rememberScrollState()
    val focus = LocalFocusManager.current
    val picker=rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri -> uri?.let(viewModel::choosePhoto) }
    val requesters = remember { vehicleFields.associateWith { BringIntoViewRequester() } }
    val firstError = vehicleFields.firstOrNull { it in state.fields || it in state.failure?.fields.orEmpty() }
    val backLabel = stringResource(R.string.back)
    val back: () -> Unit = {
        focus.clearFocus()
        if (details) details = false else onBack()
    }

    LaunchedEffect(state.done) { if (state.done) onBack() }
    LaunchedEffect(saveAttempts, state.failure) {
        if (saveAttempts > 0 && firstError != null) {
            val targetDetails = firstError in detailsFields
            if (targetDetails != details) {
                details = targetDetails
                withFrameNanos { }
            }
            requesters.getValue(firstError).bringIntoView()
        }
    }
    BackHandler(enabled=details || state.saving || state.done) { if (!state.saving && !state.done) back() }

    val title = when {
        details -> R.string.vehicle_details_title
        editing -> R.string.edit_vehicle
        else -> R.string.vehicle_new
    }
    val saveLabel = when {
        state.saving -> R.string.saving
        editing -> R.string.save_changes
        else -> R.string.add_vehicle
    }
    val fuel = FuelType.entries.firstOrNull { it.name == state.values["fuelType"] }

    @Composable fun errorFor(key: String): String? =
        state.fields[key]?.let { stringResource(it) } ?: localizedFieldError(state.failure,key)

    @Composable fun field(key: String, label: Int, icon: String, keyboard: KeyboardOptions = KeyboardOptions.Default) {
        val error = errorFor(key)
        OutlinedTextField(
            value=state.values[key].orEmpty(),onValueChange={ viewModel.change(key,it) },
            label={ Text(stringResource(label)) },leadingIcon={ VehicleFieldIcon(icon) },
            textStyle=MaterialTheme.typography.bodyLarge.copy(fontWeight=FontWeight.SemiBold),
            shape=RoundedCornerShape(15.dp),singleLine=true,enabled=enabled,
            isError=error!=null,supportingText=error?.let { { Text(it) } },keyboardOptions=keyboard,
            modifier=Modifier.fillMaxWidth().bringIntoViewRequester(requesters.getValue(key))
        )
    }

    Scaffold(contentWindowInsets=WindowInsets.systemBars) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).consumeWindowInsets(padding).imePadding()) {
            Row(
                Modifier.fillMaxWidth().padding(start=20.dp,end=20.dp,top=8.dp,bottom=12.dp),
                horizontalArrangement=Arrangement.spacedBy(12.dp),verticalAlignment=Alignment.CenterVertically
            ) {
                FilledTonalIconButton(
                    onClick=back,enabled=!state.saving && !state.done,shape=RoundedCornerShape(14.dp),
                    modifier=Modifier.size(48.dp).semantics { contentDescription=backLabel }
                ) { CareIcon(CareSymbol.BACK,Modifier.size(23.dp)) }
                Text(stringResource(title),fontSize=28.sp,lineHeight=32.sp,fontWeight=FontWeight.Bold,letterSpacing=(-.6).sp)
            }

            Column(
                Modifier.weight(1f).fillMaxWidth().verticalScroll(if (details) detailsScroll else mainScroll)
                    .padding(start=20.dp,end=20.dp,bottom=12.dp),
                verticalArrangement=Arrangement.spacedBy(11.dp)
            ) {
                if (state.loading) LoadingCards()
                state.failure?.takeIf { !state.loaded || it.fields.keys.none(vehicleFields::contains) }?.let {
                    FailureCard(it,if (!state.loaded) viewModel::load else null)
                }
                if (state.loaded) {
                    if (editing || details) {
                        val name = listOf("make","model").map { state.values[it].orEmpty().trim() }.filter(String::isNotEmpty).joinToString(" ")
                        val specification = listOfNotNull(
                            state.values["year"]?.takeIf(String::isNotBlank),
                            fuel?.let { stringResource(it.label()) }
                        ).joinToString(" · ")
                        if (name.isNotBlank()) TerracottaVehicle(name,specification)
                    }
                    if (details) {
                        field("generation",R.string.generation,"title")
                        field("engineCapacityLiters",R.string.engine_capacity,"drop",KeyboardOptions(keyboardType=KeyboardType.Decimal))
                        field("vin",R.string.vin,"title",KeyboardOptions(capitalization=KeyboardCapitalization.Characters,autoCorrectEnabled=false))
                        field("registrationNumber",R.string.registration_number,"title",KeyboardOptions(capitalization=KeyboardCapitalization.Characters,autoCorrectEnabled=false))
                        val error = errorFor("purchaseDate")
                        val dateLabel = stringResource(R.string.purchase_date)
                        Column(
                            Modifier.fillMaxWidth().bringIntoViewRequester(requesters.getValue("purchaseDate")),
                            verticalArrangement=Arrangement.spacedBy(5.dp)
                        ) {
                            Text(dateLabel,style=MaterialTheme.typography.bodyLarge)
                            TerracottaDateControl(
                                state.values["purchaseDate"].orEmpty(),R.string.vehicle_pick_purchase_date,enabled,error!=null,
                                onChange={ viewModel.change("purchaseDate",it) },latest=LocalDate.now(),clearLabelId=R.string.vehicle_clear_purchase_date
                            )
                            error?.let { Text(it,color=MaterialTheme.colorScheme.error,style=MaterialTheme.typography.bodySmall) }
                        }
                        VehicleFormNote(R.string.vehicle_optional_hint)
                    } else {
                        Text(stringResource(R.string.vehicle_basics),style=MaterialTheme.typography.titleLarge)
                        field("make",R.string.make,"car",KeyboardOptions(capitalization=KeyboardCapitalization.Words))
                        field("model",R.string.model,"car",KeyboardOptions(capitalization=KeyboardCapitalization.Words))
                        field("year",R.string.year,"calendar",KeyboardOptions(keyboardType=KeyboardType.Number))
                        VehicleFuelField(fuel,enabled,errorFor("fuelType"),Modifier.bringIntoViewRequester(requesters.getValue("fuelType"))) {
                            viewModel.change("fuelType",it.name)
                        }
                        if (editing) {
                            Surface(shape=RoundedCornerShape(15.dp),color=MaterialTheme.colorScheme.secondaryContainer.copy(alpha=.55f)) {
                                Row(
                                    Modifier.fillMaxWidth().bringIntoViewRequester(requesters.getValue("currentOdometerKm")).padding(12.dp),
                                    horizontalArrangement=Arrangement.spacedBy(12.dp),
                                    verticalAlignment=Alignment.CenterVertically
                                ) {
                                    VehicleFieldIcon("road")
                                    Column {
                                        Text(stringResource(R.string.vehicle_current_odometer),style=MaterialTheme.typography.bodyMedium)
                                        val km = state.values["currentOdometerKm"]?.toIntOrNull()
                                        if (km != null) Text(
                                            stringResource(R.string.vehicle_odometer_value,overviewNumber(km.toString(),0)),
                                            style=MaterialTheme.typography.titleLarge,fontWeight=FontWeight.Bold
                                        )
                                    }
                                }
                            }
                            VehicleFormNote(R.string.derived_odometer)
                            errorFor("currentOdometerKm")?.let {
                                Text(it,color=MaterialTheme.colorScheme.error,style=MaterialTheme.typography.bodySmall)
                            }
                        } else {
                            field("currentOdometerKm",R.string.vehicle_initial_odometer,"road",KeyboardOptions(keyboardType=KeyboardType.Number))
                        }
                        val version=state.photoVersion.takeUnless { state.photoRemoved }
                        val hasPhoto=version!=null || state.photoDraft!=null
                        if(hasPhoto) VehiclePhoto(viewModel.id,version,
                            Modifier.fillMaxWidth().aspectRatio(16f/9f).clip(RoundedCornerShape(15.dp)),state.photoDraft)
                        Row(horizontalArrangement=Arrangement.spacedBy(8.dp),verticalAlignment=Alignment.CenterVertically) {
                            OutlinedButton(onClick={
                                focus.clearFocus()
                                picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                            },enabled=enabled) {
                                Text(stringResource(if(hasPhoto) R.string.vehicle_photo_change else R.string.vehicle_photo_add))
                            }
                            if(hasPhoto) TextButton(onClick=viewModel::removePhoto,enabled=enabled) {
                                Text(stringResource(R.string.vehicle_photo_remove))
                            }
                        }
                        if(state.preparingPhoto) LinearProgressIndicator(Modifier.fillMaxWidth())
                        state.photoError?.let { Text(stringResource(it),color=MaterialTheme.colorScheme.error,style=MaterialTheme.typography.bodyMedium) }
                        Surface(
                            onClick={ focus.clearFocus(); details=true },enabled=enabled,
                            modifier=Modifier.fillMaxWidth().heightIn(min=54.dp),
                            shape=RoundedCornerShape(15.dp),color=MaterialTheme.colorScheme.secondaryContainer
                        ) {
                            Row(
                                Modifier.padding(horizontal=14.dp,vertical=12.dp),
                                horizontalArrangement=Arrangement.spacedBy(10.dp),verticalAlignment=Alignment.CenterVertically
                            ) {
                                OverviewIcon("title",Modifier.size(23.dp))
                                Text(stringResource(R.string.vehicle_details_link),Modifier.weight(1f),style=MaterialTheme.typography.titleMedium)
                                OverviewIcon("right",Modifier.size(18.dp))
                            }
                        }
                    }
                }
            }
            if (state.loaded) {
                Button(
                    onClick={ focus.clearFocus(); saveAttempts++; viewModel.save() },enabled=enabled,shape=RoundedCornerShape(16.dp),
                    modifier=Modifier.fillMaxWidth().padding(start=20.dp,end=20.dp,top=8.dp,bottom=16.dp).heightIn(min=54.dp)
                ) {
                    if (state.saving) CircularProgressIndicator(Modifier.size(18.dp),color=LocalContentColor.current,strokeWidth=2.dp)
                    else if (editing) OverviewIcon("check",Modifier.size(22.dp),LocalContentColor.current)
                    else CareIcon(CareSymbol.PLUS,Modifier.size(22.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(saveLabel),style=MaterialTheme.typography.titleMedium)
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun VehicleFuelField(fuel: FuelType?, enabled: Boolean, error: String?, modifier: Modifier, onSelect: (FuelType) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(expanded=expanded,onExpandedChange={ if (enabled) expanded=it },modifier=modifier) {
        OutlinedTextField(
            value=fuel?.let { stringResource(it.label()) }.orEmpty(),onValueChange={},readOnly=true,
            label={ Text(stringResource(R.string.fuel_type)) },leadingIcon={ VehicleFieldIcon("fuel") },
            trailingIcon={ ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
            textStyle=MaterialTheme.typography.bodyLarge.copy(fontWeight=FontWeight.SemiBold),
            shape=RoundedCornerShape(15.dp),isError=error!=null,supportingText=error?.let { { Text(it) } },
            enabled=enabled,modifier=Modifier.menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable).fillMaxWidth()
        )
        ExposedDropdownMenu(expanded=expanded,onDismissRequest={ expanded=false }) {
            FuelType.entries.forEach { type ->
                DropdownMenuItem(text={ Text(stringResource(type.label())) },onClick={ onSelect(type); expanded=false })
            }
        }
    }
}

@Composable
private fun VehicleFieldIcon(icon: String) {
    Surface(
        Modifier.padding(start=7.dp,end=3.dp).size(34.dp),shape=RoundedCornerShape(10.dp),
        color=MaterialTheme.colorScheme.secondaryContainer.copy(alpha=.7f)
    ) {
        Box(contentAlignment=Alignment.Center) {
            if (icon=="calendar") CompositionLocalProvider(LocalContentColor provides MaterialTheme.colorScheme.primary) {
                CareIcon(CareSymbol.CALENDAR,Modifier.size(21.dp))
            } else OverviewIcon(icon,Modifier.size(21.dp))
        }
    }
}

@Composable
private fun VehicleFormNote(label: Int) {
    Row(
        Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(9.dp),
        verticalAlignment=Alignment.CenterVertically
    ) {
        OverviewIcon("info",Modifier.size(21.dp))
        Text(stringResource(label),style=MaterialTheme.typography.bodyMedium,color=MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
