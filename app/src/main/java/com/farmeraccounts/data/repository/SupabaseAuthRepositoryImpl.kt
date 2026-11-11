package com.farmeraccounts.data.repository

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.farmeraccounts.domain.repository.AuthRepository
import com.farmeraccounts.domain.repository.AuthState
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.providers.builtin.Email
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private val Context.authDataStore by preferencesDataStore(name = "owner_auth_preferences")

class SupabaseAuthRepositoryImpl(
    private val context: Context,
    private val supabaseClient: SupabaseClient?
) : AuthRepository {

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private val keyOwnerId = stringPreferencesKey("supabase_owner_user_id")
    private val keyOwnerEmail = stringPreferencesKey("supabase_owner_email")

    private val _authState = MutableStateFlow<AuthState>(AuthState.Unauthenticated)
    override val authState: StateFlow<AuthState> = _authState.asStateFlow()

    init {
        restoreSessionOfflineFirst()
    }

    private fun restoreSessionOfflineFirst() {
        scope.launch {
            val prefs = context.authDataStore.data.first()
            val cachedUserId = prefs[keyOwnerId]
            val cachedEmail = prefs[keyOwnerEmail]

            if (!cachedUserId.isNullOrBlank() && !cachedEmail.isNullOrBlank()) {
                // Immediately allow local application to operate in offline state
                _authState.value = AuthState.Offline(cachedUserId, cachedEmail)

                // Concurrently attempt non-blocking cloud token refresh
                if (supabaseClient != null) {
                    try {
                        supabaseClient.auth.awaitInitialization()
                        val session = supabaseClient.auth.currentSessionOrNull()
                        if (session != null) {
                            _authState.value = AuthState.Authenticated(session.user?.id ?: cachedUserId, cachedEmail)
                        } else {
                            supabaseClient.auth.refreshCurrentSession()
                            val refreshed = supabaseClient.auth.currentSessionOrNull()
                            if (refreshed != null) {
                                _authState.value = AuthState.Authenticated(refreshed.user?.id ?: cachedUserId, cachedEmail)
                            }
                        }
                    } catch (e: Exception) {
                        // Network error or offline: maintain Offline state gracefully
                    }
                }
            } else {
                _authState.value = AuthState.Unauthenticated
            }
        }
    }

    override suspend fun getOwnerId(): String? = withContext(Dispatchers.IO) {
        val activeUserId = supabaseClient?.auth?.currentUserOrNull()?.id
        if (!activeUserId.isNullOrBlank()) {
            return@withContext activeUserId
        }
        val prefs = context.authDataStore.data.first()
        prefs[keyOwnerId]
    }

    override suspend fun signIn(email: String, password: String): Result<String> = withContext(Dispatchers.IO) {
        if (supabaseClient == null) {
            return@withContext Result.failure(IllegalStateException("Supabase client is not configured."))
        }

        try {
            supabaseClient.auth.signInWith(Email) {
                this.email = email.trim()
                this.password = password
            }

            val user = supabaseClient.auth.currentUserOrNull()
                ?: return@withContext Result.failure(IllegalStateException("Login succeeded but user profile was null."))

            val userId = user.id
            val userEmail = user.email ?: email.trim()

            context.authDataStore.edit { prefs ->
                prefs[keyOwnerId] = userId
                prefs[keyOwnerEmail] = userEmail
            }

            _authState.value = AuthState.Authenticated(userId, userEmail)
            Result.success(userId)
        } catch (e: Exception) {
            _authState.value = AuthState.Error(e.message ?: "Authentication failed")
            Result.failure(e)
        }
    }

    override suspend fun signUpOwner(email: String, password: String): Result<String> = withContext(Dispatchers.IO) {
        if (supabaseClient == null) {
            return@withContext Result.failure(IllegalStateException("Supabase client is not configured."))
        }

        // Single-owner check: prevent multiple owner accounts
        val prefs = context.authDataStore.data.first()
        val existingOwner = prefs[keyOwnerId]
        if (!existingOwner.isNullOrBlank()) {
            return@withContext Result.failure(IllegalStateException("સિસ્ટમમાં માલિકનું ખાતું પહેલેથી સેટ કરેલ છે (Owner already configured)."))
        }

        try {
            supabaseClient.auth.signUpWith(Email) {
                this.email = email.trim()
                this.password = password
            }

            val user = supabaseClient.auth.currentUserOrNull()
                ?: return@withContext Result.failure(IllegalStateException("Registration created, please check confirmation email or log in."))

            val userId = user.id
            val userEmail = user.email ?: email.trim()

            context.authDataStore.edit { p ->
                p[keyOwnerId] = userId
                p[keyOwnerEmail] = userEmail
            }

            _authState.value = AuthState.Authenticated(userId, userEmail)
            Result.success(userId)
        } catch (e: Exception) {
            _authState.value = AuthState.Error(e.message ?: "Registration failed")
            Result.failure(e)
        }
    }

    override suspend fun signOut(): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            supabaseClient?.auth?.signOut()
        } catch (_: Exception) {
            // Ignore network errors on signout
        }

        context.authDataStore.edit { prefs ->
            prefs.remove(keyOwnerId)
            prefs.remove(keyOwnerEmail)
        }

        _authState.value = AuthState.Unauthenticated
        Result.success(Unit)
    }

    override suspend fun refreshSession(): Result<Unit> = withContext(Dispatchers.IO) {
        if (supabaseClient == null) return@withContext Result.failure(IllegalStateException("Supabase not configured"))
        try {
            supabaseClient.auth.awaitInitialization()
            supabaseClient.auth.refreshCurrentSession()
            val user = supabaseClient.auth.currentUserOrNull()
            if (user != null) {
                _authState.value = AuthState.Authenticated(user.id, user.email ?: "")
                Result.success(Unit)
            } else {
                Result.failure(IllegalStateException("Session refresh failed"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}

