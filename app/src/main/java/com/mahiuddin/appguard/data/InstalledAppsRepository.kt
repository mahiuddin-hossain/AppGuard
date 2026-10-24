package com.mahiuddin.appguard.data

import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.ConcurrentHashMap

data class AppInfo(
    val packageName: String,
    val label: String,
    val icon: Drawable?,
    val isSystemApp: Boolean
)

class InstalledAppsRepository(private val context: Context) {

    private val packageManager: PackageManager = context.packageManager
    private val selfPackageName: String = context.packageName

    private val appInfoCache = ConcurrentHashMap<String, AppInfo>()

    @Volatile
    private var cachedInstalledApps: List<AppInfo>? = null

    suspend fun getInstalledApps(forceRefresh: Boolean = false): List<AppInfo> = withContext(Dispatchers.IO) {
        if (!forceRefresh && cachedInstalledApps != null) {
            return@withContext cachedInstalledApps!!
        }

        val launcherIntent = Intent(Intent.ACTION_MAIN, null).apply {
            addCategory(Intent.CATEGORY_LAUNCHER)
        }

        val resolveInfoList = try {
            packageManager.queryIntentActivities(launcherIntent, 0)
        } catch (e: Exception) {
            emptyList()
        }

        val appsList = mutableListOf<AppInfo>()
        val seenPackages = mutableSetOf<String>()

        for (resolveInfo in resolveInfoList) {
            val packageName = resolveInfo.activityInfo.packageName
            if (packageName == selfPackageName || seenPackages.contains(packageName)) {
                continue
            }

            if (isExcludedPackage(packageName)) {
                continue
            }

            seenPackages.add(packageName)

            val cached = appInfoCache[packageName]
            if (cached != null) {
                appsList.add(cached)
                continue
            }

            val appLabel = try {
                resolveInfo.loadLabel(packageManager).toString()
            } catch (e: Exception) {
                packageName
            }

            val appIcon = try {
                resolveInfo.loadIcon(packageManager)
            } catch (e: Exception) {
                null
            }

            val isSystemApp = try {
                val appInfo = packageManager.getApplicationInfo(packageName, 0)
                (appInfo.flags and ApplicationInfo.FLAG_SYSTEM) != 0 &&
                        (appInfo.flags and ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) == 0
            } catch (e: Exception) {
                false
            }

            val app = AppInfo(
                packageName = packageName,
                label = appLabel,
                icon = appIcon,
                isSystemApp = isSystemApp
            )

            appInfoCache[packageName] = app
            appsList.add(app)
        }

        val sortedList = appsList.sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.label })
        cachedInstalledApps = sortedList
        sortedList
    }

    fun getCachedOrFetchAppInfo(packageName: String): AppInfo {
        val cached = appInfoCache[packageName]
        if (cached != null) {
            return cached
        }

        return try {
            val appInfo = packageManager.getApplicationInfo(packageName, 0)
            val label = packageManager.getApplicationLabel(appInfo).toString()
            val icon = packageManager.getApplicationIcon(appInfo)
            val isSystem = (appInfo.flags and ApplicationInfo.FLAG_SYSTEM) != 0 &&
                    (appInfo.flags and ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) == 0

            val result = AppInfo(
                packageName = packageName,
                label = label,
                icon = icon,
                isSystemApp = isSystem
            )
            appInfoCache[packageName] = result
            result
        } catch (e: Exception) {
            AppInfo(
                packageName = packageName,
                label = packageName,
                icon = null,
                isSystemApp = false
            )
        }
    }

    private fun isExcludedPackage(packageName: String): Boolean {
        return packageName == "com.android.systemui" ||
                packageName == "com.android.settings" ||
                packageName == "com.google.android.packageinstaller" ||
                packageName == "com.android.packageinstaller" ||
                packageName == "com.google.android.permissioncontroller"
    }
}
