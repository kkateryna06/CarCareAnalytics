package com.carcare.analytics.core.security

class SessionStorageException(cause: Throwable) : Exception("Session storage unavailable", cause)
