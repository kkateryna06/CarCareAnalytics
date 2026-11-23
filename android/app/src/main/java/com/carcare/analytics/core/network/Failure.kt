package com.carcare.analytics.core.network

import androidx.annotation.StringRes
import com.carcare.analytics.R
import com.carcare.analytics.core.security.SessionStorageException
import com.squareup.moshi.Moshi
import kotlinx.coroutines.CancellationException
import retrofit2.HttpException
import java.io.IOException
import javax.inject.Inject

data class Failure(@param:StringRes val fallback: Int, val message: String? = null, val fields: Map<String, String> = emptyMap())
class ErrorMapper @Inject constructor(private val moshi: Moshi) {
    fun map(error: Throwable): Failure {
        if (error is CancellationException) throw error
        return when (error) {
            is SessionStorageException -> Failure(R.string.error_storage)
            is HttpException -> {
                val body = runCatching { error.response()?.errorBody()?.string()?.let { moshi.adapter(ApiError::class.java).fromJson(it) } }.getOrNull()
                val fallback=when(body?.code) {
                    "INVALID_CREDENTIALS" -> R.string.error_invalid_credentials
                    "EMAIL_EXISTS" -> R.string.error_email_exists
                    "VALIDATION_ERROR" -> R.string.error_check_fields
                    "EXPORT_TOO_LARGE" -> R.string.error_export_too_large
                    "INVALID_FILTER" -> R.string.error_invalid_filter
                    "SOURCE_MANAGED" -> R.string.error_source_managed
                    "IDEMPOTENCY_RESULT_DELETED" -> R.string.error_not_found
                    else -> when {
                        error.code()==401 -> R.string.error_session
                        error.code()==404 -> R.string.error_not_found
                        error.code()>=500 -> R.string.error_server
                        else -> R.string.error_unknown
                    }
                }
                Failure(fallback, body?.message, body?.fieldErrors.orEmpty())
            }
            is IOException -> Failure(R.string.error_network)
            else -> Failure(R.string.error_unknown)
        }
    }
}
