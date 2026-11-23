package com.carcare.analytics.feature.auth

import com.carcare.analytics.core.network.*
import com.carcare.analytics.core.security.TokenStore
import com.carcare.analytics.core.security.SessionStorageException
import kotlinx.coroutines.CancellationException
import javax.inject.Inject

class AuthRepository @Inject constructor(private val api: CarCareApi, private val tokens: TokenStore) {
    suspend fun authenticate(email: String, password: String, register: Boolean) {
        val request = AuthRequest(email.trim(), password)
        val response = if (register) api.register(request) else api.login(request)
        try { tokens.save(response) }
        catch (error: Exception) { if (error is CancellationException) throw error; throw SessionStorageException(error) }
    }
}
