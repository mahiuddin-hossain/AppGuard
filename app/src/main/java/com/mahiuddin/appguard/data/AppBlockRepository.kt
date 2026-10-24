package com.mahiuddin.appguard.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import java.io.IOException

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "app_guard_preferences")

class AppBlockRepository private constructor(private val context: Context) {

    companion object {
        private val KEY_BLOCKED_PACKAGES = stringSetPreferencesKey("blocked_packages")
        private val KEY_IS_BLOCKING_ENABLED = booleanPreferencesKey("is_blocking_enabled")

        @Volatile
        private var INSTANCE: AppBlockRepository? = null

        fun getInstance(context: Context): AppBlockRepository {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: AppBlockRepository(context.applicationContext).also { INSTANCE = it }
            }
        }
    }

    val blockedPackagesFlow: Flow<Set<String>> = context.dataStore.data
        .catch { exception ->
            if (exception is IOException) {
                emit(emptyPreferences())
            } else {
                throw exception
            }
        }
        .map { preferences ->
            preferences[KEY_BLOCKED_PACKAGES] ?: emptySet()
        }

    val isBlockingEnabledFlow: Flow<Boolean> = context.dataStore.data
        .catch { exception ->
            if (exception is IOException) {
                emit(emptyPreferences())
            } else {
                throw exception
            }
        }
        .map { preferences ->
            preferences[KEY_IS_BLOCKING_ENABLED] ?: true
        }

    suspend fun addPackage(packageName: String) {
        context.dataStore.edit { preferences ->
            val current = preferences[KEY_BLOCKED_PACKAGES] ?: emptySet()
            preferences[KEY_BLOCKED_PACKAGES] = current + packageName
        }
    }

    suspend fun removePackage(packageName: String) {
        context.dataStore.edit { preferences ->
            val current = preferences[KEY_BLOCKED_PACKAGES] ?: emptySet()
            preferences[KEY_BLOCKED_PACKAGES] = current - packageName
        }
    }

    suspend fun setBlockedPackages(packages: Set<String>) {
        context.dataStore.edit { preferences ->
            preferences[KEY_BLOCKED_PACKAGES] = packages
        }
    }

    suspend fun setBlockingEnabled(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[KEY_IS_BLOCKING_ENABLED] = enabled
        }
    }
}
