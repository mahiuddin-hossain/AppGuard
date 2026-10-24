package com.mahiuddin.appguard.service

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.provider.Settings
import android.text.TextUtils
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityManager
import com.mahiuddin.appguard.data.AppBlockRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking

class AppBlockAccessibilityService : AccessibilityService() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    @Volatile
    private var blockedPackages: Set<String> = emptySet()

    @Volatile
    private var isBlockingEnabled: Boolean = true

    @Volatile
    private var lastBlockedPackage: String? = null

    @Volatile
    private var lastBlockedTime: Long = 0L

    private var homeLauncherPackages: Set<String> = emptySet()

    override fun onCreate() {
        super.onCreate()
        Log.i(TAG, "onCreate: Initializing AppBlockAccessibilityService")
        updateHomeLauncherPackages()

        val repository = AppBlockRepository.getInstance(applicationContext)

        // Load initial state synchronously so blockedPackages & isBlockingEnabled
        // are immediately available before the first accessibility event is delivered.
        loadInitialStateSynchronously(repository)

        // Subscribe to live DataStore updates reactively
        serviceScope.launch {
            try {
                repository.blockedPackagesFlow.collect { packages ->
                    Log.d(TAG, "blockedPackagesFlow emitted ${packages.size} packages")
                    blockedPackages = packages
                    resetGuardState()
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error in blockedPackagesFlow collector", e)
            }
        }

        serviceScope.launch {
            try {
                repository.isBlockingEnabledFlow.collect { enabled ->
                    Log.d(TAG, "isBlockingEnabledFlow emitted enabled=$enabled")
                    isBlockingEnabled = enabled
                    resetGuardState()
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error in isBlockingEnabledFlow collector", e)
            }
        }
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        Log.i(TAG, "onServiceConnected: Service bound to Accessibility framework")
        updateHomeLauncherPackages()
        resetGuardState()

        val repository = AppBlockRepository.getInstance(applicationContext)
        loadInitialStateSynchronously(repository)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        try {
            if (event == null || event.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
                return
            }

            if (!isBlockingEnabled) {
                resetGuardState()
                return
            }

            val eventPackageName = event.packageName?.toString() ?: return

            // Ignore empty package, AppGuard itself, system UI, Android framework, and Home Launchers
            if (eventPackageName.isEmpty() ||
                eventPackageName == packageName ||
                eventPackageName == "com.android.systemui" ||
                eventPackageName == "android" ||
                homeLauncherPackages.contains(eventPackageName)
            ) {
                resetGuardState()
                return
            }

            // If foreground package is not in the active blocklist, reset guard state
            if (!blockedPackages.contains(eventPackageName)) {
                resetGuardState()
                return
            }

            val currentTime = System.currentTimeMillis()

            // Debounce duplicate events within 1500ms window for the exact same package
            if (eventPackageName == lastBlockedPackage && (currentTime - lastBlockedTime) < 1500) {
                return
            }

            lastBlockedPackage = eventPackageName
            lastBlockedTime = currentTime

            Log.i(TAG, "Blocked app detected ($eventPackageName). Performing Home action.")
            val success = performGlobalAction(GLOBAL_ACTION_HOME)
            Log.d(TAG, "performGlobalAction(GLOBAL_ACTION_HOME) returned $success")

        } catch (e: Exception) {
            Log.e(TAG, "Error processing accessibility event", e)
        }
    }

    override fun onInterrupt() {
        Log.w(TAG, "onInterrupt: AccessibilityService interrupted")
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.i(TAG, "onDestroy: AppBlockAccessibilityService destroying")
        serviceScope.cancel()
    }

    private fun loadInitialStateSynchronously(repository: AppBlockRepository) {
        try {
            runBlocking(Dispatchers.IO) {
                blockedPackages = repository.blockedPackagesFlow.first()
                isBlockingEnabled = repository.isBlockingEnabledFlow.first()
            }
            Log.d(TAG, "Loaded initial state: isBlockingEnabled=$isBlockingEnabled, blockedCount=${blockedPackages.size}")
        } catch (e: Exception) {
            Log.e(TAG, "Error loading initial DataStore state", e)
        }
    }

    private fun resetGuardState() {
        lastBlockedPackage = null
        lastBlockedTime = 0L
    }

    private fun updateHomeLauncherPackages() {
        try {
            val intent = Intent(Intent.ACTION_MAIN).apply {
                addCategory(Intent.CATEGORY_HOME)
            }
            val resolveInfos = packageManager.queryIntentActivities(
                intent,
                PackageManager.MATCH_DEFAULT_ONLY
            )
            val packages = mutableSetOf<String>(
                "com.google.android.apps.nexuslauncher",
                "com.sec.android.app.launcher",
                "com.miui.home",
                "com.huawei.android.launcher",
                "com.oppo.launcher",
                "com.vivo.launcher",
                "com.oneplus.launcher",
                "com.android.launcher",
                "com.android.launcher3",
                "com.nothing.launcher"
            )
            for (info in resolveInfos) {
                info.activityInfo?.packageName?.let { packages.add(it) }
            }
            homeLauncherPackages = packages
        } catch (e: Exception) {
            Log.e(TAG, "Error querying launcher packages", e)
            homeLauncherPackages = emptySet()
        }
    }

    companion object {
        private const val TAG = "AppBlockService"

        fun isAccessibilityServiceEnabled(context: Context): Boolean {
            val expectedComponentName = ComponentName(context, AppBlockAccessibilityService::class.java)

            val enabledServicesSetting = Settings.Secure.getString(
                context.contentResolver,
                Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
            )

            if (!enabledServicesSetting.isNull_or_Empty()) {
                val colonSplitter = TextUtils.SimpleStringSplitter(':')
                colonSplitter.setString(enabledServicesSetting)

                while (colonSplitter.hasNext()) {
                    val componentNameString = colonSplitter.next()
                    val enabledComponent = ComponentName.unflattenFromString(componentNameString)
                    if (enabledComponent != null && enabledComponent == expectedComponentName) {
                        return true
                    }
                }
            }

            try {
                val accessibilityManager = context.getSystemService(ACCESSIBILITY_SERVICE) as? AccessibilityManager
                val enabledServices = accessibilityManager?.getEnabledAccessibilityServiceList(
                    AccessibilityServiceInfo.FEEDBACK_ALL_MASK
                ) ?: emptyList()

                for (service in enabledServices) {
                    val serviceInfo = service.resolveInfo.serviceInfo
                    if (serviceInfo.packageName == expectedComponentName.packageName &&
                        serviceInfo.name == expectedComponentName.className
                    ) {
                        return true
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error checking accessibility service status via AccessibilityManager", e)
            }

            return false
        }

        private fun String?.isNull_or_Empty(): Boolean = this == null || this.isEmpty()
    }
}
