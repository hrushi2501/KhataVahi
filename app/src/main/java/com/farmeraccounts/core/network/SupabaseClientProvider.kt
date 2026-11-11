package com.farmeraccounts.core.network

import com.farmeraccounts.BuildConfig
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.Auth
import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.postgrest.Postgrest

/**
 * Provides the singleton Supabase client configured using non-privileged BuildConfig credentials.
 * Service role keys are never stored or exposed.
 */
object SupabaseClientProvider {

    fun createClient(): SupabaseClient? {
        val url = BuildConfig.SUPABASE_URL.trim()
        val key = BuildConfig.SUPABASE_PUBLISHABLE_KEY.trim()

        if (url.isBlank() || key.isBlank() || !url.startsWith("https://") || !key.startsWith("sb_publishable_")) {
            return null
        }

        return try {
            createSupabaseClient(
                supabaseUrl = url,
                supabaseKey = key
            ) {
                install(Auth) {
                    alwaysAutoRefresh = true
                }
                install(Postgrest)
            }
        } catch (e: Exception) {
            null
        }
    }
}

