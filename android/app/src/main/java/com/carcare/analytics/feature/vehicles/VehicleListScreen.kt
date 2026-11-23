package com.carcare.analytics.feature.vehicles

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.carcare.analytics.R
import com.carcare.analytics.core.designsystem.CareIcon
import com.carcare.analytics.core.designsystem.CareSymbol
import com.carcare.analytics.core.designsystem.DeleteConfirmationDialog
import com.carcare.analytics.core.designsystem.FailureCard
import com.carcare.analytics.core.designsystem.LoadingCards
import com.carcare.analytics.core.designsystem.label
import com.carcare.analytics.core.network.VehicleDto
import com.carcare.analytics.feature.insights.OverviewIcon
import com.carcare.analytics.feature.insights.overviewNumber
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@Composable
fun VehicleListScreen(
    onAdd: () -> Unit,
    onEdit: (Long) -> Unit,
    onOpen: (Long) -> Unit,
    onLogout: () -> Unit,
    preferredId: Long?,
    onVehicleDeleted: (Long) -> Unit,
    onSettings: () -> Unit,
    viewModel: VehicleListViewModel
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val refresh={ viewModel.refresh(preferredId) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { viewModel.resume(preferredId) }

    GarageContent(
        state=state,onAdd=onAdd,onEdit=onEdit,onOpen=onOpen,onLogout=onLogout,
        onRefresh=refresh,onMore=viewModel::more,onDelete={ viewModel.delete(it,onVehicleDeleted) },
        onSettings=onSettings
    )
}

@Composable
internal fun GarageContent(
    state: VehicleListState,
    onAdd: () -> Unit,
    onEdit: (Long) -> Unit,
    onOpen: (Long) -> Unit,
    onLogout: () -> Unit,
    onRefresh: () -> Unit,
    onMore: () -> Unit,
    onDelete: (Long) -> Unit,
    onSettings: (() -> Unit)? = null
) {
    val enabled=state.deleting==null
    val firstLoad=state.loading && state.nextPage==0
    val empty=!firstLoad && state.items.isEmpty() && state.failure==null
    val featured=state.activeVehicle ?: state.items.firstOrNull()
    val otherVehicles=state.items.filter { it.id!=featured?.id }
    var pendingDelete by rememberSaveable { mutableStateOf<Long?>(null) }
    val refreshLabel=stringResource(R.string.refresh)

    Scaffold(contentWindowInsets=WindowInsets.statusBars) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            Row(
                Modifier.fillMaxWidth().padding(start=20.dp,end=20.dp,top=12.dp,bottom=8.dp),
                verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(10.dp)
            ) {
                Text("CarCare",Modifier.weight(1f),style=MaterialTheme.typography.headlineSmall)
                if(onSettings!=null) {
                    val settings=stringResource(R.string.settings)
                    IconButton(onClick=onSettings,enabled=enabled,modifier=Modifier.semantics { contentDescription=settings }) {
                        OverviewIcon("language",Modifier.size(23.dp))
                    }
                }
                OutlinedButton(
                    onClick=onLogout,enabled=enabled,shape=RoundedCornerShape(14.dp),
                    border=BorderStroke(1.dp,MaterialTheme.colorScheme.outlineVariant),
                    contentPadding=PaddingValues(horizontal=12.dp,vertical=8.dp)
                ) {
                    OverviewIcon("logout",Modifier.size(20.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(stringResource(R.string.logout))
                }
            }
            if(empty) {
                Box(Modifier.fillMaxWidth().weight(1f),contentAlignment=Alignment.Center) {
                    EmptyGarage(onAdd)
                }
            } else {
                LazyColumn(
                    Modifier.fillMaxWidth().weight(1f),
                    contentPadding=PaddingValues(start=20.dp,end=20.dp,top=4.dp,bottom=12.dp),
                    verticalArrangement=Arrangement.spacedBy(14.dp)
                ) {
                    item {
                        Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) {
                            Text(stringResource(R.string.vehicles),Modifier.weight(1f),fontSize=34.sp,lineHeight=40.sp,fontWeight=FontWeight.Bold,letterSpacing=(-1).sp)
                            IconButton(
                                onClick=onRefresh,enabled=enabled && !state.loading,
                                modifier=Modifier.semantics { contentDescription=refreshLabel }
                            ) {
                                OverviewIcon("refresh",Modifier.size(22.dp),LocalContentColor.current)
                            }
                        }
                    }
                    state.failure?.let { failure -> item { FailureCard(failure,onRefresh) } }
                    if(firstLoad && featured==null) {
                        item { LoadingCards() }
                    } else if(!enabled) {
                        item { LinearProgressIndicator(Modifier.fillMaxWidth()) }
                    }
                    featured?.let { car ->
                        item("vehicle:${car.id}") {
                            GarageVehicleCard(car,true,enabled,{ onOpen(car.id) },{ onEdit(car.id) },{ pendingDelete=car.id })
                        }
                    }
                    items(otherVehicles,key={ "vehicle:${it.id}" }) { car ->
                        GarageVehicleCard(car,false,enabled,{ onOpen(car.id) },{ onEdit(car.id) },{ pendingDelete=car.id })
                    }
                    if(state.loadingMore) {
                        item { LinearProgressIndicator(Modifier.fillMaxWidth()) }
                    } else if(state.nextPage<state.totalPages) {
                        item {
                            OutlinedButton(onClick=onMore,enabled=enabled && !state.loading,modifier=Modifier.fillMaxWidth(),shape=RoundedCornerShape(14.dp)) {
                                Text(stringResource(R.string.load_more))
                            }
                        }
                    }
                }
                Box(Modifier.padding(start=20.dp,end=20.dp,top=8.dp,bottom=14.dp)) {
                    GarageAddButton(onAdd,enabled)
                }
            }
        }
    }

    val deletingCar=state.items.firstOrNull { it.id==pendingDelete }
    if(deletingCar!=null) DeleteConfirmationDialog(
        title=stringResource(R.string.delete_title),
        description=stringResource(R.string.delete_description,"${deletingCar.make} ${deletingCar.model}"),
        onDismiss={ pendingDelete=null },
        onConfirm={ pendingDelete=null; onDelete(deletingCar.id) }
    )
}

@Composable
private fun GarageVehicleCard(car: VehicleDto, featured: Boolean, enabled: Boolean, onOpen: () -> Unit, onEdit: () -> Unit, onDelete: () -> Unit) {
    val openLabel=stringResource(R.string.garage_open_overview)
    OutlinedCard(
        Modifier.fillMaxWidth(),shape=RoundedCornerShape(18.dp),
        colors=CardDefaults.outlinedCardColors(containerColor=Color.Transparent),
        border=BorderStroke(1.dp,MaterialTheme.colorScheme.outlineVariant)
    ) {
        Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) {
            Row(
                Modifier.weight(1f).clickable(enabled=enabled,onClickLabel=openLabel,role=Role.Button,onClick=onOpen)
                    .padding(start=13.dp,end=if(featured) 13.dp else 4.dp,top=13.dp,bottom=13.dp),
                verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(12.dp)
            ) {
                Surface(shape=RoundedCornerShape(15.dp),color=MaterialTheme.colorScheme.secondaryContainer) {
                    Box(Modifier.size(if(featured) 62.dp else 54.dp),contentAlignment=Alignment.Center) {
                        VehiclePhoto(car.id,car.photoVersion,Modifier.fillMaxSize()) {
                            OverviewIcon("car",Modifier.size(if(featured) 42.dp else 36.dp))
                        }
                    }
                }
                Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(3.dp)) {
                    Text("${car.make} ${car.model}",style=MaterialTheme.typography.titleMedium,fontWeight=FontWeight.Bold,maxLines=2,overflow=TextOverflow.Ellipsis)
                    Text("${car.year} · ${stringResource(car.fuelType.label())}",style=MaterialTheme.typography.bodyMedium,color=MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(stringResource(R.string.vehicle_odometer_value,overviewNumber(car.currentOdometerKm.toString(),0)),style=MaterialTheme.typography.bodyMedium,color=MaterialTheme.colorScheme.onSurfaceVariant)
                    car.registrationNumber?.takeIf { it.isNotBlank() }?.let { plate ->
                        Surface(color=MaterialTheme.colorScheme.secondaryContainer.copy(alpha=.65f),shape=RoundedCornerShape(8.dp)) {
                            Text(plate,Modifier.padding(horizontal=7.dp,vertical=2.dp),style=MaterialTheme.typography.labelMedium,maxLines=1,overflow=TextOverflow.Ellipsis)
                        }
                    }
                }
                if(!featured) OverviewIcon("right",Modifier.size(17.dp))
            }
            if(!featured) {
                Box(Modifier.padding(end=5.dp)) {
                    VehicleActions(car,outlined=false,enabled=enabled,onEdit=onEdit,onDelete=onDelete)
                }
            }
        }
        if(featured) {
            Row(
                Modifier.fillMaxWidth().padding(start=13.dp,end=13.dp,bottom=13.dp),
                verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(8.dp)
            ) {
                Button(onClick=onOpen,enabled=enabled,modifier=Modifier.weight(1f).heightIn(min=48.dp),shape=RoundedCornerShape(14.dp),contentPadding=PaddingValues(horizontal=8.dp,vertical=10.dp)) {
                    Text(openLabel,textAlign=TextAlign.Center)
                }
                val editLabel=stringResource(R.string.edit)
                OutlinedIconButton(
                    onClick=onEdit,enabled=enabled,modifier=Modifier.size(48.dp).semantics { contentDescription=editLabel },
                    shape=RoundedCornerShape(14.dp),border=BorderStroke(1.dp,MaterialTheme.colorScheme.outlineVariant)
                ) {
                    OverviewIcon("note",Modifier.size(21.dp))
                }
                VehicleActions(car,outlined=true,enabled=enabled,onEdit=onEdit,onDelete=onDelete)
            }
        }
    }
}

private val garageDateFormat=DateTimeFormatter.ofPattern("dd.MM.uuuu")

@Composable
private fun VehicleActions(car: VehicleDto, outlined: Boolean, enabled: Boolean, onEdit: () -> Unit, onDelete: () -> Unit) {
    var open by remember { mutableStateOf(false) }
    val label=stringResource(R.string.garage_vehicle_actions,"${car.make} ${car.model}")
    val modifier=Modifier.size(48.dp).semantics { contentDescription=label }
    val updated=remember(car.updatedAt) {
        runCatching { Instant.parse(car.updatedAt).atZone(ZoneId.systemDefault()).toLocalDate().format(garageDateFormat) }.getOrNull()
    }
    Box {
        if(outlined) {
            OutlinedIconButton(
                onClick={ open=true },enabled=enabled,modifier=modifier,
                shape=RoundedCornerShape(14.dp),border=BorderStroke(1.dp,MaterialTheme.colorScheme.outlineVariant)
            ) { OverviewIcon("more",Modifier.size(22.dp),LocalContentColor.current) }
        } else {
            IconButton(onClick={ open=true },enabled=enabled,modifier=modifier) {
                OverviewIcon("more",Modifier.size(22.dp),LocalContentColor.current)
            }
        }
        DropdownMenu(expanded=open,onDismissRequest={ open=false }) {
            updated?.let {
                Text(stringResource(R.string.updated_at,it),Modifier.padding(horizontal=16.dp,vertical=8.dp),style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
                HorizontalDivider()
            }
            if(!outlined) DropdownMenuItem(text={ Text(stringResource(R.string.edit)) },onClick={ open=false;onEdit() })
            DropdownMenuItem(text={ Text(stringResource(R.string.delete),color=MaterialTheme.colorScheme.error) },onClick={ open=false;onDelete() })
        }
    }
}

@Composable
private fun EmptyGarage(onAdd: () -> Unit) {
    Column(
        Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(start=20.dp,end=20.dp,top=12.dp,bottom=34.dp),
        horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.spacedBy(20.dp)
    ) {
        Text(stringResource(R.string.empty_title),style=MaterialTheme.typography.headlineMedium,textAlign=TextAlign.Center)
        Box(Modifier.size(width=200.dp,height=112.dp),contentAlignment=Alignment.Center) {
            Box(Modifier.align(Alignment.BottomCenter).padding(bottom=10.dp).size(width=175.dp,height=10.dp)
                .background(MaterialTheme.colorScheme.secondaryContainer,RoundedCornerShape(10.dp)))
            OverviewIcon("car",Modifier.size(128.dp))
        }
        Text(stringResource(R.string.garage_empty_prompt),style=MaterialTheme.typography.bodyLarge,color=MaterialTheme.colorScheme.onSurfaceVariant,textAlign=TextAlign.Center)
        GarageAddButton(onAdd,enabled=true)
    }
}

@Composable
private fun GarageAddButton(onAdd: () -> Unit, enabled: Boolean) {
    Button(onClick=onAdd,enabled=enabled,modifier=Modifier.fillMaxWidth().heightIn(min=54.dp),shape=RoundedCornerShape(16.dp)) {
        CareIcon(CareSymbol.PLUS,Modifier.size(22.dp))
        Spacer(Modifier.width(8.dp))
        Text(stringResource(R.string.add_vehicle),style=MaterialTheme.typography.titleMedium,textAlign=TextAlign.Center)
    }
}
