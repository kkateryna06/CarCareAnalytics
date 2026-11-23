package com.carcare.analytics.feature.auth

import androidx.lifecycle.*
import com.carcare.analytics.R
import com.carcare.analytics.core.network.*
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

data class AuthState(val email: String = "", val password: String = "", val confirmation: String = "", val busy: Boolean = false,
    val fields: Map<String, Int> = emptyMap(), val failure: Failure? = null)

fun validateCredentials(email: String, password: String, confirmation: String, register: Boolean): Map<String, Int> = buildMap {
    if (!email.trim().matches(Regex("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$")) || email.trim().length > 254) put("email", R.string.error_email)
    if (password.length < 8 || password.toByteArray(Charsets.UTF_8).size > 72 || password.isBlank()) put("password", R.string.error_password)
    if (register && password != confirmation) put("confirmation", R.string.error_confirm)
}

@HiltViewModel
class AuthViewModel @Inject constructor(private val repository: AuthRepository, private val errors: ErrorMapper, private val saved: SavedStateHandle) : ViewModel() {
    private val _state = MutableStateFlow(AuthState(email = saved["email"] ?: ""))
    val state = _state.asStateFlow()
    fun change(field: String, value: String) {
        if (field == "email") saved["email"] = value
        _state.update { when (field) {
            "email" -> it.copy(email = value, fields = it.fields - field, failure = null)
            "password" -> it.copy(password = value, fields = it.fields - field, failure = null)
            else -> it.copy(confirmation = value, fields = it.fields - field, failure = null)
        } }
    }
    fun submit(register: Boolean) {
        val input = _state.value
        if (input.busy) return
        val fields = validateCredentials(input.email, input.password, input.confirmation, register)
        if (fields.isNotEmpty()) { _state.update { it.copy(fields = fields) }; return }
        _state.update { it.copy(busy = true, failure = null) }
        viewModelScope.launch {
            try {
                repository.authenticate(input.email, input.password, register)
                _state.update { it.copy(password = "", confirmation = "", busy = false) }
            } catch (error: Exception) { _state.update { it.copy(busy = false, failure = errors.map(error)) } }
        }
    }
}
