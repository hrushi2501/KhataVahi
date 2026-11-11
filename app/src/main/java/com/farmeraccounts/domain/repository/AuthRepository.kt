package com.farmeraccounts.domain.repository

import kotlinx.coroutines.flow.StateFlow

sealed class AuthState {
    object Unauthenticated : AuthState()
    data class Authenticated(val userId: String, val email: String) : AuthState()
    data class Offline(val cachedUserId: String, val email: String) : AuthState()
    data class Error(val message: String) : AuthState()
}

/**
 * Dedicated Owner Authentication contract per project specifications:
 * - Single-owner model
 * - Persisted credentials in secure DataStore
 * - Seamless offline startup (never blocks local application)
 * - Automatic token refresh with graceful offline fallback
 */
interface AuthRepository {
    val authState: StateFlow<AuthState>
    suspend fun getOwnerId(): String?
    suspend fun signIn(email: String, password: String): Result<String>
    suspend fun signUpOwner(email: String, password: String): Result<String>
    suspend fun signOut(): Result<Unit>
    suspend fun refreshSession(): Result<Unit>
}

