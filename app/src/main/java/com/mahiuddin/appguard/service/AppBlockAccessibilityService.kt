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
import android.view.accessibility.AccessibilityNodeInfo
import com.mahiuddin.appguard.data.AppBlockRepository
import com.mahiuddin.appguard.data.DnsBlockRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking

class AppBlockAccessibilityService : AccessibilityService() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val dnsFilterEngine = DnsFilterEngine()

    @Volatile
    private var blockedPackages: Set<String> = emptySet()

    @Volatile
    private var isBlockingEnabled: Boolean = true

    @Volatile
    private var lastBlockedPackage: String? = null

    @Volatile
    private var lastBlockedTime: Long = 0L

    private var homeLauncherPackages: Set<String> = emptySet()

    // Domain Blocking Preferences
    @Volatile
    private var isDnsBlockingEnabled: Boolean = false

    @Volatile
    private var blockAdult: Boolean = false

    @Volatile
    private var blockFacebook: Boolean = false

    @Volatile
    private var blockInstagram: Boolean = false

    @Volatile
    private var blockTiktok: Boolean = false

    @Volatile
    private var blockSnapchat: Boolean = false

    @Volatile
    private var customBlockedDomains: Set<String> = emptySet()

    @Volatile
    private var customAllowedDomains: Set<String> = emptySet()

    companion object {
        private const val TAG = "AppBlockService"

        private val BROWSER_PACKAGES = setOf(
            "com.android.chrome", "org.mozilla.firefox", "com.sec.android.app.sbrowser",
            "com.opera.browser", "com.microsoft.emmx", "com.brave.browser",
            "com.ucmobile.intl", "com.opera.mini.native", "com.duckduckgo.mobile.android"
        )

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

    override fun onCreate() {
        super.onCreate()
        Log.i(TAG, "onCreate: Initializing AppBlockAccessibilityService")
        updateHomeLauncherPackages()

        val repository = AppBlockRepository.getInstance(applicationContext)
        val dnsRepository = DnsBlockRepository.getInstance(applicationContext)

        // Load initial state synchronously
        loadInitialStateSynchronously(repository, dnsRepository)

        // Subscribe to App Blocking preferences
        serviceScope.launch {
            try {
                repository.blockedPackagesFlow.collect { packages ->
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
                    isBlockingEnabled = enabled
                    resetGuardState()
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error in isBlockingEnabledFlow collector", e)
            }
        }

        // Subscribe to Domain Shield preferences
        serviceScope.launch { dnsRepository.isDnsBlockingEnabledFlow.collect { isDnsBlockingEnabled = it } }
        serviceScope.launch { dnsRepository.blockAdultSitesFlow.collect { blockAdult = it } }
        serviceScope.launch { dnsRepository.blockFacebookFlow.collect { blockFacebook = it } }
        serviceScope.launch { dnsRepository.blockInstagramFlow.collect { blockInstagram = it } }
        serviceScope.launch { dnsRepository.blockTiktokFlow.collect { blockTiktok = it } }
        serviceScope.launch { dnsRepository.blockSnapchatFlow.collect { blockSnapchat = it } }
        serviceScope.launch { dnsRepository.customBlockedDomainsFlow.collect { customBlockedDomains = it } }
        serviceScope.launch { dnsRepository.customAllowedDomainsFlow.collect { customAllowedDomains = it } }
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        Log.i(TAG, "onServiceConnected: Service bound to Accessibility framework")
        updateHomeLauncherPackages()
        resetGuardState()

        val repository = AppBlockRepository.getInstance(applicationContext)
        val dnsRepository = DnsBlockRepository.getInstance(applicationContext)
        loadInitialStateSynchronously(repository, dnsRepository)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        try {
            if (event == null) return

            val eventPackageName = event.packageName?.toString() ?: return

            // 1. Process App Package Blocking on TYPE_WINDOW_STATE_CHANGED
            if (event.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED && isBlockingEnabled) {
                checkAndPerformPackageBlocking(eventPackageName)
            }

            // 2. Process Domain & Web Blocking across browsers and web views
            if (isDnsBlockingEnabled) {
                checkAndPerformDomainBlocking(eventPackageName)
            }

        } catch (e: Exception) {
            Log.e(TAG, "Error processing accessibility event", e)
        }
    }

    private fun checkAndPerformPackageBlocking(eventPackageName: String) {
        if (eventPackageName.isEmpty() ||
            eventPackageName == packageName ||
            eventPackageName == "com.android.systemui" ||
            eventPackageName == "android" ||
            homeLauncherPackages.contains(eventPackageName)
        ) {
            resetGuardState()
            return
        }

        if (!blockedPackages.contains(eventPackageName)) {
            resetGuardState()
            return
        }

        val currentTime = System.currentTimeMillis()
        if (eventPackageName == lastBlockedPackage && (currentTime - lastBlockedTime) < 1500) {
            return
        }

        lastBlockedPackage = eventPackageName
        lastBlockedTime = currentTime

        Log.i(TAG, "Blocked app detected ($eventPackageName). Performing Home action.")
        performGlobalAction(GLOBAL_ACTION_HOME)
    }

    private fun checkAndPerformDomainBlocking(eventPackageName: String) {
        val rootNode = rootInActiveWindow ?: return

        val extractedUrl = findUrlInNodeTree(rootNode)
        if (!extractedUrl.isNull_or_Empty()) {
            val domain = dnsFilterEngine.normalizeDomain(extractedUrl!!)
            if (domain.isNotEmpty()) {
                val isBlocked = dnsFilterEngine.isDomainBlocked(
                    domain = domain,
                    blockAdult = blockAdult,
                    blockFacebook = blockFacebook,
                    blockInstagram = blockInstagram,
                    blockTiktok = blockTiktok,
                    blockSnapchat = blockSnapchat,
                    customAllowedDomains = customAllowedDomains,
                    customBlockedDomains = customBlockedDomains
                )

                if (isBlocked) {
                    val currentTime = System.currentTimeMillis()
                    if (domain == lastBlockedPackage && (currentTime - lastBlockedTime) < 1500) {
                        return
                    }

                    lastBlockedPackage = domain
                    lastBlockedTime = currentTime

                    Log.i(TAG, "Blocked Domain detected in browser ($domain). Performing Home action.")
                    performGlobalAction(GLOBAL_ACTION_HOME)
                }
            }
        }
    }

    private fun findUrlInNodeTree(node: AccessibilityNodeInfo?): String? {
        if (node == null) return null

        val text = node.text?.toString() ?: node.contentDescription?.toString()
        if (!text.isNull_or_Blank() && containsDomainPattern(text!!)) {
            return text
        }

        val childCount = node.childCount
        for (i in 0 until childCount) {
            val child = node.getChild(i) ?: continue
            val result = findUrlInNodeTree(child)
            if (result != null) {
                return result
            }
        }

        return null
    }

    private fun containsDomainPattern(text: String): Boolean {
        val trimmed = text.trim().lowercase()
        return (trimmed.contains(".com") || trimmed.contains(".org") ||
                trimmed.contains(".net") || trimmed.contains(".info") ||
                trimmed.contains(".porn") || trimmed.contains(".sex") ||
                trimmed.contains(".xxx") || trimmed.contains("http://") ||
                trimmed.contains("https://"))
    }

    override fun onInterrupt() {
        Log.w(TAG, "onInterrupt: AccessibilityService interrupted")
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.i(TAG, "onDestroy: AppBlockAccessibilityService destroying")
        serviceScope.cancel()
    }

    private fun loadInitialStateSynchronously(
        repository: AppBlockRepository,
        dnsRepository: DnsBlockRepository
    ) {
        try {
            runBlocking(Dispatchers.IO) {
                blockedPackages = repository.blockedPackagesFlow.first()
                isBlockingEnabled = repository.isBlockingEnabledFlow.first()

                isDnsBlockingEnabled = dnsRepository.isDnsBlockingEnabledFlow.first()
                blockAdult = dnsRepository.blockAdultSitesFlow.first()
                blockFacebook = dnsRepository.blockFacebookFlow.first()
                blockInstagram = dnsRepository.blockInstagramFlow.first()
                blockTiktok = dnsRepository.blockTiktokFlow.first()
                blockSnapchat = dnsRepository.blockSnapchatFlow.first()
                customBlockedDomains = dnsRepository.customBlockedDomainsFlow.first()
                customAllowedDomains = dnsRepository.customAllowedDomainsFlow.first()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error loading initial DataStore state for AccessibilityService", e)
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

    private fun String?.isNull_or_Blank(): Boolean = this == null || this.isBlank()
}
