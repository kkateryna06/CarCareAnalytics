package com.carcare.analytics.feature.reminders

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.*
import androidx.lifecycle.compose.*
import com.carcare.analytics.R
import com.carcare.analytics.feature.insights.OverviewIcon
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class NotificationSettingsViewModel @Inject constructor(private val store: NotificationStore,private val notifier: ReminderNotifier): ViewModel() {
    val preference=store.state.stateIn(viewModelScope,SharingStarted.WhileSubscribed(5000),NotificationPreference(null,false))
    private val _error=MutableStateFlow(false); val error=_error.asStateFlow()
    private val _permissionAllowed=MutableStateFlow(notifier.allowed())
    val permissionAllowed=_permissionAllowed.asStateFlow()
    fun refreshPermission() { _permissionAllowed.value=notifier.allowed() }
    fun enable(value: Boolean) { viewModelScope.launch { try { store.enable(value); _error.value=false } catch(ex: Exception) { if(ex is CancellationException) throw ex; _error.value=true } } }
}
@Composable
fun NotificationSettings(viewModel: NotificationSettingsViewModel = hiltViewModel()) {
    val preference by viewModel.preference.collectAsStateWithLifecycle(); val error by viewModel.error.collectAsStateWithLifecycle()
    val context=LocalContext.current
    val allowed by viewModel.permissionAllowed.collectAsStateWithLifecycle()
    val launcher=rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        viewModel.refreshPermission()
        if(granted && viewModel.permissionAllowed.value) viewModel.enable(true)
    }
    fun settings() { context.startActivity(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE,context.packageName)) }
    Column(Modifier.fillMaxWidth(),verticalArrangement=Arrangement.spacedBy(14.dp)) {
        Text(stringResource(R.string.reminders_notifications_title),style=MaterialTheme.typography.headlineMedium)
        OutlinedCard(Modifier.fillMaxWidth(),shape=RoundedCornerShape(16.dp)) {
            Row(Modifier.padding(horizontal=16.dp,vertical=9.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(10.dp)) {
                val label=stringResource(R.string.reminders_notification_toggle)
                Text(label,Modifier.weight(1f),style=MaterialTheme.typography.titleMedium)
                Switch(checked=preference.enabled && allowed,modifier=Modifier.semantics { contentDescription=label },onCheckedChange={ enable ->
                    if(!enable) viewModel.enable(false)
                    else if(Build.VERSION.SDK_INT>=33 && context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)!=PackageManager.PERMISSION_GRANTED) launcher.launch(Manifest.permission.POST_NOTIFICATIONS)
                    else if(!allowed) settings() else viewModel.enable(true)
                })
            }
        }
        Surface(color=MaterialTheme.colorScheme.secondaryContainer,shape=RoundedCornerShape(16.dp)) {
            Row(Modifier.padding(14.dp),verticalAlignment=Alignment.Top,horizontalArrangement=Arrangement.spacedBy(12.dp)) {
                OverviewIcon("info",Modifier.size(23.dp))
                Text(stringResource(R.string.notifications_explanation),Modifier.weight(1f),style=MaterialTheme.typography.bodyMedium)
            }
        }
        if(!allowed) Text(stringResource(R.string.notifications_blocked),color=MaterialTheme.colorScheme.error)
        if(error) Text(stringResource(R.string.notification_storage_error),color=MaterialTheme.colorScheme.error)
        Button(onClick={ settings() },modifier=Modifier.fillMaxWidth().heightIn(min=52.dp),shape=RoundedCornerShape(16.dp)) {
            Text(stringResource(R.string.notification_settings))
        }
    }
}
