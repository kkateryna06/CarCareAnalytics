package com.carcare.analytics.navigation

import androidx.lifecycle.*
import com.carcare.analytics.R
import com.carcare.analytics.core.network.Failure
import com.carcare.analytics.core.security.TokenStore
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.CancellationException
import javax.inject.Inject
import com.carcare.analytics.feature.reminders.*
import com.carcare.analytics.feature.forecast.CsvExportFiles

@HiltViewModel
class SessionViewModel @Inject constructor(private val tokens: TokenStore,private val notifications: NotificationStore,private val scheduler: ReminderScheduler,private val exports: CsvExportFiles) : ViewModel() {
    val ready = tokens.ready
    val session = tokens.session
    private val _failure = MutableStateFlow<Failure?>(null)
    val failure = _failure.asStateFlow()
    fun logout() { viewModelScope.launch { try { notifications.logout(); scheduler.stopNow(); tokens.clear(); exports.clear() } catch (error: Exception) { if (error is CancellationException) throw error; _failure.value = Failure(R.string.error_logout) } } }
    fun dismissError() { _failure.value = null }
}
