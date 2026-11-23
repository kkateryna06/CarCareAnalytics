package com.carcare.analytics.core.designsystem

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.carcare.analytics.R
import com.carcare.analytics.core.network.*
import com.carcare.analytics.feature.insights.OverviewIcon

@Composable
fun FailureCard(failure: Failure, onRetry: (() -> Unit)? = null, showingCachedData: Boolean = false) {
    if (failure.fallback==R.string.error_network) {
        NetworkFailureCard(onRetry,showingCachedData)
        return
    }
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Text(stringResource(failure.fallback), color = MaterialTheme.colorScheme.onErrorContainer)
            if (onRetry != null) TextButton(onClick = onRetry) { Text(stringResource(R.string.retry)) }
        }
    }
}

@Composable
internal fun localizedFieldError(failure: Failure?,key: String): String? {
    val message=failure?.fields?.get(key) ?: return null
    if(message.startsWith("Пробег не согласуется с соседними датами")) {
        val bounds=Regex("\\d+").findAll(message).map { it.value }.toList()
        if(bounds.size==2) return stringResource(R.string.error_mileage_bounds,bounds[0],bounds[1])
        if(bounds.size==1) return stringResource(R.string.error_mileage_min,bounds[0])
    }
    val label=when {
        message.startsWith("must not be ") -> R.string.error_required
        message.startsWith("must be a date in the past") -> R.string.expense_date_invalid
        message.startsWith("must be a well-formed email") -> R.string.error_email
        message.startsWith("Конец периода") -> R.string.error_date_range
        message.startsWith("Период не может превышать") -> R.string.error_long_period
        message.startsWith("Изменяйте пробег") -> R.string.derived_odometer
        message.startsWith("Пароль должен") -> R.string.error_password
        else -> R.string.error_invalid_field
    }
    return stringResource(label)
}

@Composable
private fun NetworkFailureCard(onRetry: (() -> Unit)?,showingCachedData: Boolean) {
    val colors=MaterialTheme.colorScheme
    val message: @Composable () -> Unit = {
        Row(horizontalArrangement=Arrangement.spacedBy(10.dp)) {
            OverviewIcon("warning",Modifier.size(23.dp),colors.onTertiaryContainer)
            Column(verticalArrangement=Arrangement.spacedBy(2.dp)) {
                Text(stringResource(R.string.network_unavailable),style=MaterialTheme.typography.titleMedium)
                Text(
                    stringResource(if(showingCachedData) R.string.network_cached_data else R.string.network_retry_hint),
                    style=MaterialTheme.typography.bodySmall
                )
            }
        }
    }
    val retry: @Composable () -> Unit = {
        if(onRetry!=null) Button(
            onClick=onRetry,shape=RoundedCornerShape(12.dp),
            contentPadding=PaddingValues(horizontal=12.dp,vertical=8.dp),
            modifier=Modifier.heightIn(min=46.dp)
        ) { Text(stringResource(R.string.retry)) }
    }
    Surface(
        Modifier.fillMaxWidth(),shape=RoundedCornerShape(16.dp),
        color=colors.tertiaryContainer,contentColor=colors.onTertiaryContainer
    ) {
        BoxWithConstraints(Modifier.padding(12.dp)) {
            if(onRetry!=null && maxWidth>=304.dp && LocalDensity.current.fontScale<=1.2f) {
                Row(horizontalArrangement=Arrangement.spacedBy(10.dp),verticalAlignment=Alignment.CenterVertically) {
                    Box(Modifier.weight(1f)) { message() }
                    retry()
                }
            } else {
                Column(Modifier.fillMaxWidth(),verticalArrangement=Arrangement.spacedBy(6.dp)) {
                    message()
                    if(onRetry!=null) Box(Modifier.align(Alignment.End)) { retry() }
                }
            }
        }
    }
}

@Composable
internal fun DeleteConfirmationDialog(title: String,description: String,onDismiss: () -> Unit,onConfirm: () -> Unit) {
    val colors=MaterialTheme.colorScheme
    Dialog(onDismissRequest=onDismiss) {
        Surface(shape=RoundedCornerShape(24.dp),color=colors.background) {
            Column(
                Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(20.dp),
                horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.spacedBy(14.dp)
            ) {
                Surface(shape=RoundedCornerShape(16.dp),color=colors.errorContainer,contentColor=colors.onErrorContainer) {
                    Box(Modifier.size(54.dp),contentAlignment=Alignment.Center) {
                        OverviewIcon("trash",Modifier.size(28.dp),LocalContentColor.current)
                    }
                }
                Text(title,style=MaterialTheme.typography.headlineSmall,fontWeight=FontWeight.Bold,textAlign=TextAlign.Center)
                Text(description,style=MaterialTheme.typography.bodyMedium,textAlign=TextAlign.Center)
                Row(Modifier.fillMaxWidth().padding(top=5.dp),horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
                        onClick=onDismiss,modifier=Modifier.weight(1f).heightIn(min=48.dp),shape=RoundedCornerShape(13.dp),
                        contentPadding=PaddingValues(horizontal=6.dp,vertical=8.dp)
                    ) { Text(stringResource(R.string.cancel)) }
                    Button(
                        onClick=onConfirm,modifier=Modifier.weight(1f).heightIn(min=48.dp),shape=RoundedCornerShape(13.dp),
                        colors=ButtonDefaults.buttonColors(containerColor=colors.error,contentColor=colors.onError),
                        contentPadding=PaddingValues(horizontal=6.dp,vertical=8.dp)
                    ) { Text(stringResource(R.string.delete)) }
                }
            }
        }
    }
}

@StringRes
fun FuelType.label(): Int = when(this) {
    FuelType.PETROL -> R.string.petrol; FuelType.DIESEL -> R.string.diesel; FuelType.LPG -> R.string.lpg
    FuelType.HYBRID -> R.string.hybrid; FuelType.ELECTRIC -> R.string.electric; FuelType.OTHER -> R.string.other
}
