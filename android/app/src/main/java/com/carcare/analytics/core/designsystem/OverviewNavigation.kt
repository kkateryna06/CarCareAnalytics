package com.carcare.analytics.core.designsystem

import androidx.compose.ui.res.stringResource
import com.carcare.analytics.R
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable fun OverviewNavigation(active: Int = 1, vehicleSelected: Boolean = true, onNavigate:(Int)->Unit) {
    Surface(color=MaterialTheme.colorScheme.surface) {
        Column(Modifier.navigationBarsPadding()) {
            HorizontalDivider(color=MaterialTheme.colorScheme.outlineVariant.copy(alpha=.5f))
            Row(Modifier.fillMaxWidth().selectableGroup().padding(horizontal=16.dp,vertical=6.dp),horizontalArrangement=Arrangement.SpaceBetween) {
                val labels=listOf(R.string.nav_garage,R.string.nav_overview,R.string.logbook,R.string.reminders_title).map { stringResource(it) }
                val symbols=listOf(CareSymbol.CAR,CareSymbol.CHART,CareSymbol.JOURNAL,CareSymbol.CALENDAR)
                labels.forEachIndexed { i,label ->
                    val selected=i==active
                    val enabled=i==0 || vehicleSelected
                    val color=when {
                        !enabled -> MaterialTheme.colorScheme.onSurface.copy(alpha=.38f)
                        selected -> MaterialTheme.colorScheme.primary
                        else -> MaterialTheme.colorScheme.onSurface
                    }
                    Column(Modifier.weight(1f).padding(horizontal=3.dp)
                        .background(if(selected) MaterialTheme.colorScheme.secondaryContainer else androidx.compose.ui.graphics.Color.Transparent,RoundedCornerShape(16.dp))
                        .selectable(selected=selected,enabled=enabled,role=Role.Tab,onClick={onNavigate(i)})
                        .padding(vertical=7.dp),horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.spacedBy(4.dp)) {
                        androidx.compose.runtime.CompositionLocalProvider(LocalContentColor provides color) { CareIcon(symbols[i]) }
                        Text(label,color=color,fontSize=12.sp,lineHeight=16.sp)
                    }
                }
            }
        }
    }
}
