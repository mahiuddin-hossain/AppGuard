package com.mahiuddin.appguard.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.mahiuddin.appguard.data.AppBlockRepository
import com.mahiuddin.appguard.data.AppInfo
import com.mahiuddin.appguard.data.InstalledAppsRepository
import com.mahiuddin.appguard.service.AppBlockAccessibilityService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class AppGuardViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = AppBlockRepository.getInstance(application)
    private val installedAppsRepository = InstalledAppsRepository(application)

    val isBlockingEnabled: StateFlow<Boolean> = repository.isBlockingEnabledFlow
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = true
        )

    val blockedPackages: StateFlow<Set<String>> = repository.blockedPackagesFlow
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptySet()
        )

    private val _installedApps = MutableStateFlow<List<AppInfo>>(emptyList())
    val installedApps: StateFlow<List<AppInfo>> = _installedApps.asStateFlow()

    private val _isAccessibilityEnabled = MutableStateFlow(false)
    val isAccessibilityEnabled: StateFlow<Boolean> = _isAccessibilityEnabled.asStateFlow()

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _isAddAppsSheetOpen = MutableStateFlow(false)
    val isAddAppsSheetOpen: StateFlow<Boolean> = _isAddAppsSheetOpen.asStateFlow()

    private val _selectedPackagesInSheet = MutableStateFlow<Set<String>>(emptySet())
    val selectedPackagesInSheet: StateFlow<Set<String>> = _selectedPackagesInSheet.asStateFlow()

    // Custom package name dialog state
    private val _isCustomPackageDialogOpen = MutableStateFlow(false)
    val isCustomPackageDialogOpen: StateFlow<Boolean> = _isCustomPackageDialogOpen.asStateFlow()

    private val _customPackageInput = MutableStateFlow("")
    val customPackageInput: StateFlow<String> = _customPackageInput.asStateFlow()

    private val _customPackageError = MutableStateFlow<String?>(null)
    val customPackageError: StateFlow<String?> = _customPackageError.asStateFlow()

    // Filtered installed apps based on search query in Add Apps dialog
    val filteredInstalledApps: StateFlow<List<AppInfo>> = combine(
        _installedApps,
        _searchQuery
    ) { apps, query ->
        if (query.isBlank()) {
            apps
        } else {
            val lowerQuery = query.lowercase().trim()
            apps.filter { app ->
                app.label.lowercase().contains(lowerQuery) ||
                        app.packageName.lowercase().contains(lowerQuery)
            }
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    // Resolved AppInfo for currently blocked packages
    val blockedAppsList: StateFlow<List<AppInfo>> = combine(
        blockedPackages,
        _installedApps
    ) { blockedPkgs, allApps ->
        val appMap = allApps.associateBy { it.packageName }
        blockedPkgs.map { pkg ->
            appMap[pkg] ?: installedAppsRepository.getCachedOrFetchAppInfo(pkg)
        }.sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.label })
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    init {
        refreshAllData()
    }

    fun refreshAllData() {
        refreshAccessibilityStatus()
        refreshInstalledApps(force = false)
    }

    fun refreshAccessibilityStatus() {
        _isAccessibilityEnabled.value = AppBlockAccessibilityService.isAccessibilityServiceEnabled(getApplication())
    }

    fun refreshInstalledApps(force: Boolean = false) {
        viewModelScope.launch {
            val apps = installedAppsRepository.getInstalledApps(forceRefresh = force)
            _installedApps.value = apps
        }
    }

    fun setBlockingEnabled(enabled: Boolean) {
        viewModelScope.launch {
            repository.setBlockingEnabled(enabled)
        }
    }

    fun removeBlockedApp(packageName: String) {
        viewModelScope.launch {
            repository.removePackage(packageName)
        }
    }

    fun openAddAppsSheet() {
        _selectedPackagesInSheet.value = blockedPackages.value
        _searchQuery.value = ""
        _isAddAppsSheetOpen.value = true
        refreshInstalledApps(force = false)
    }

    fun closeAddAppsSheet() {
        _isAddAppsSheetOpen.value = false
    }

    fun updateSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun togglePackageSelectionInSheet(packageName: String) {
        val current = _selectedPackagesInSheet.value.toMutableSet()
        if (current.contains(packageName)) {
            current.remove(packageName)
        } else {
            current.add(packageName)
        }
        _selectedPackagesInSheet.value = current
    }

    fun confirmPackageSelectionInSheet() {
        viewModelScope.launch {
            repository.setBlockedPackages(_selectedPackagesInSheet.value)
            _isAddAppsSheetOpen.value = false
        }
    }

    fun openCustomPackageDialog() {
        _customPackageInput.value = ""
        _customPackageError.value = null
        _isCustomPackageDialogOpen.value = true
    }

    fun closeCustomPackageDialog() {
        _isCustomPackageDialogOpen.value = false
        _customPackageError.value = null
    }

    fun updateCustomPackageInput(input: String) {
        _customPackageInput.value = input
        if (_customPackageError.value != null) {
            _customPackageError.value = null
        }
    }

    fun addCustomPackage() {
        val trimmed = _customPackageInput.value.trim()
        if (trimmed.isEmpty()) {
            _customPackageError.value = "Package name cannot be empty"
            return
        }

        if (trimmed.contains(" ")) {
            _customPackageError.value = "Package name cannot contain spaces"
            return
        }

        val selfPackage = getApplication<Application>().packageName
        if (trimmed == selfPackage) {
            _customPackageError.value = "Cannot block AppGuard itself"
            return
        }

        viewModelScope.launch {
            repository.addPackage(trimmed)
            if (_isAddAppsSheetOpen.value) {
                val current = _selectedPackagesInSheet.value.toMutableSet()
                current.add(trimmed)
                _selectedPackagesInSheet.value = current
            }
            closeCustomPackageDialog()
            refreshInstalledApps(force = true)
        }
    }
}
