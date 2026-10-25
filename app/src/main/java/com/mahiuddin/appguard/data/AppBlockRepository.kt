package com.mahiuddin.appguard.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.mahiuddin.appguard.data.db.AppDatabase
import com.mahiuddin.appguard.data.db.BlockedAppEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import java.io.IOException

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "app_guard_preferences")

class AppBlockRepository private constructor(private val context: Context) {

    private val blockedAppDao = AppDatabase.getInstance(context).blockedAppDao()

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

    val blockedAppEntitiesFlow: Flow<List<BlockedAppEntity>> = blockedAppDao.getAllBlockedAppsFlow()

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

    suspend fun addBlockedApp(packageName: String, label: String, isCustom: Boolean = false) {
        val entity = BlockedAppEntity(
            packageName = packageName,
            label = label,
            isCustom = isCustom
        )
        blockedAppDao.insertOrUpdate(entity)

        context.dataStore.edit { preferences ->
            val current = preferences[KEY_BLOCKED_PACKAGES] ?: emptySet()
            preferences[KEY_BLOCKED_PACKAGES] = current + packageName
        }
    }

    suspend fun removeBlockedApp(packageName: String) {
        blockedAppDao.deleteByPackageName(packageName)

        context.dataStore.edit { preferences ->
            val current = preferences[KEY_BLOCKED_PACKAGES] ?: emptySet()
            preferences[KEY_BLOCKED_PACKAGES] = current - packageName
        }
    }

    suspend fun setBlockedApps(apps: List<BlockedAppEntity>) {
        if (apps.isEmpty()) {
            blockedAppDao.deleteAll()
        } else {
            blockedAppDao.insertOrUpdateAll(apps)
            blockedAppDao.deleteNotInSet(apps.map { it.packageName })
        }

        context.dataStore.edit { preferences ->
            preferences[KEY_BLOCKED_PACKAGES] = apps.map { it.packageName }.toSet()
        }
    }

    suspend fun setBlockingEnabled(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[KEY_IS_BLOCKING_ENABLED] = enabled
        }
    }
}
