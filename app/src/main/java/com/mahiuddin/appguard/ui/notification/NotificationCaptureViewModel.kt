package com.mahiuddin.appguard.ui.notification

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.mahiuddin.appguard.data.AppInfo
import com.mahiuddin.appguard.data.InstalledAppsRepository
import com.mahiuddin.appguard.data.NotificationRepository
import com.mahiuddin.appguard.data.db.CapturedNotificationEntity
import com.mahiuddin.appguard.service.AppNotificationListenerService
import com.mahiuddin.appguard.sync.FirebaseSyncManager
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class NotificationCaptureViewModel(application: Application) : AndroidViewModel(application) {

    private val notificationRepository = NotificationRepository.getInstance(application)
    private val installedAppsRepository = InstalledAppsRepository(application)
    private val firebaseSyncManager = FirebaseSyncManager.getInstance()

    val isCaptureEnabled: StateFlow<Boolean> = notificationRepository.isCaptureEnabledFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    val isCloudSyncEnabled: StateFlow<Boolean> = notificationRepository.isCloudSyncEnabledFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)

    val selectedPackages: StateFlow<Set<String>> = notificationRepository.selectedPackagesFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptySet())

    val pendingSyncCount: StateFlow<Int> = notificationRepository.pendingSyncCountFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    val lastSyncedTime: StateFlow<Long?> = notificationRepository.lastSyncedTimeFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val totalNotificationCount: StateFlow<Int> = notificationRepository.totalCountFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    private val _isListenerPermissionGranted = MutableStateFlow(false)
    val isListenerPermissionGranted: StateFlow<Boolean> = _isListenerPermissionGranted.asStateFlow()

    private val _installedApps = MutableStateFlow<List<AppInfo>>(emptyList())
    val installedApps: StateFlow<List<AppInfo>> = _installedApps.asStateFlow()

    private val _appSearchQuery = MutableStateFlow("")
    val appSearchQuery: StateFlow<String> = _appSearchQuery.asStateFlow()

    private val _historySearchQuery = MutableStateFlow("")
    val historySearchQuery: StateFlow<String> = _historySearchQuery.asStateFlow()

    private val _isSelectAppsDialogOpen = MutableStateFlow(false)
    val isSelectAppsDialogOpen: StateFlow<Boolean> = _isSelectAppsDialogOpen.asStateFlow()

    private val _isDeleteLocalDialogOpen = MutableStateFlow(false)
    val isDeleteLocalDialogOpen: StateFlow<Boolean> = _isDeleteLocalDialogOpen.asStateFlow()

    private val _isDeleteCloudDialogOpen = MutableStateFlow(false)
    val isDeleteCloudDialogOpen: StateFlow<Boolean> = _isDeleteCloudDialogOpen.asStateFlow()

    private val _userMessage = MutableStateFlow<String?>(null)
    val userMessage: StateFlow<String?> = _userMessage.asStateFlow()

    // Filtered installed apps for notification selection
    val filteredInstalledApps: StateFlow<List<AppInfo>> = combine(
        _installedApps,
        _appSearchQuery
    ) { apps, query ->
        if (query.isBlank()) {
            apps
        } else {
            val lower = query.lowercase().trim()
            apps.filter { it.label.lowercase().contains(lower) || it.packageName.lowercase().contains(lower) }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // History notifications list reactive query
    @OptIn(ExperimentalCoroutinesApi::class)
    val historyNotifications: StateFlow<List<CapturedNotificationEntity>> = _historySearchQuery
        .flatMapLatest { query ->
            notificationRepository.getNotificationsPaged(limit = 100, offset = 0, query = query)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    init {
        refreshAllData()
    }

    fun refreshAllData() {
        refreshListenerPermissionStatus()
        refreshInstalledApps()
    }

    fun refreshListenerPermissionStatus() {
        _isListenerPermissionGranted.value =
            AppNotificationListenerService.isNotificationListenerPermissionGranted(getApplication())
    }

    fun refreshInstalledApps() {
        viewModelScope.launch {
            _installedApps.value = installedAppsRepository.getInstalledApps(forceRefresh = false)
        }
    }

    fun setCaptureEnabled(enabled: Boolean) {
        viewModelScope.launch {
            notificationRepository.setCaptureEnabled(enabled)
            if (enabled && !_isListenerPermissionGranted.value) {
                _userMessage.value = "Please grant Notification Access permission in Android settings."
            }
        }
    }

    fun setCloudSyncEnabled(enabled: Boolean) {
        viewModelScope.launch {
            notificationRepository.setCloudSyncEnabled(enabled)
        }
    }

    fun togglePackageSelection(packageName: String) {
        viewModelScope.launch {
            notificationRepository.togglePackageSelection(packageName)
        }
    }

    fun openSelectAppsDialog() {
        _appSearchQuery.value = ""
        _isSelectAppsDialogOpen.value = true
        refreshInstalledApps()
    }

    fun closeSelectAppsDialog() {
        _isSelectAppsDialogOpen.value = false
    }

    fun updateAppSearchQuery(query: String) {
        _appSearchQuery.value = query
    }

    fun updateHistorySearchQuery(query: String) {
        _historySearchQuery.value = query
    }

    fun openDeleteLocalDialog() {
        _isDeleteLocalDialogOpen.value = true
    }

    fun closeDeleteLocalDialog() {
        _isDeleteLocalDialogOpen.value = false
    }

    fun openDeleteCloudDialog() {
        _isDeleteCloudDialogOpen.value = true
    }

    fun closeDeleteCloudDialog() {
        _isDeleteCloudDialogOpen.value = false
    }

    fun confirmDeleteLocalHistory() {
        viewModelScope.launch {
            notificationRepository.deleteLocalHistory()
            _userMessage.value = "Deleted local notification records."
            _isDeleteLocalDialogOpen.value = false
        }
    }

    fun confirmDeleteCloudHistory() {
        viewModelScope.launch {
            val uid = firebaseSyncManager.ensureAuthenticatedUserUid()
            if (uid == null) {
                _userMessage.value = "Failed to authenticate for cloud deletion."
                _isDeleteCloudDialogOpen.value = false
                return@launch
            }

            val result = firebaseSyncManager.deleteUserCloudNotifications(uid)
            if (result.isSuccess) {
                val count = result.getOrDefault(0)
                _userMessage.value = "Deleted $count cloud notification records."
            } else {
                _userMessage.value = "Cloud deletion failed: ${result.exceptionOrNull()?.localizedMessage}"
            }
            _isDeleteCloudDialogOpen.value = false
        }
    }

    fun triggerSyncNow() {
        notificationRepository.triggerSyncWork()
        _userMessage.value = "Cloud sync triggered."
    }

    fun clearUserMessage() {
        _userMessage.value = null
    }
}
