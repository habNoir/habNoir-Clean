package xyz.habnoir.clean.ui.viewmodel

import android.Manifest
import android.app.PendingIntent
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.PowerManager
import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import xyz.habnoir.clean.AppContainer
import xyz.habnoir.clean.R
import xyz.habnoir.clean.data.prefs.UserPreferencesSnapshot
import xyz.habnoir.clean.engine.accessibility.AccessibilityCleanerService
import xyz.habnoir.clean.engine.oem.DeviceIdentity
import xyz.habnoir.clean.engine.oem.OemCleanSpec
import xyz.habnoir.clean.engine.reminder.StorageReminderJobService
import xyz.habnoir.clean.engine.trash.TrashOverviewSnapshot
import xyz.habnoir.clean.model.AppCacheItem
import xyz.habnoir.clean.model.BatchPolicy
import xyz.habnoir.clean.model.ByteFormat
import xyz.habnoir.clean.model.CleanSessionState
import xyz.habnoir.clean.model.DeviceStorageOverview
import xyz.habnoir.clean.model.EngineTier
import xyz.habnoir.clean.model.PermissionHealth
import xyz.habnoir.clean.model.TrashLayer1Item

enum class MainTab { SPACE, APPS, TRASH }

enum class AppsFilter { ALL, OVER_150_MB, EXCLUDED }

data class MainUiState(
    val isScanning: Boolean = false,
    val showOnboarding: Boolean = false,
    val onboardingChecked: Boolean = false,
    val allApps: List<AppCacheItem> = emptyList(),
    val filteredApps: List<AppCacheItem> = emptyList(),
    val appsSearchQuery: String = "",
    val appsFilter: AppsFilter = AppsFilter.ALL,
    val storageOverview: DeviceStorageOverview = DeviceStorageOverview(0L, 0L, 0L),
    val trashSnapshot: TrashOverviewSnapshot? = null,
    val prefs: UserPreferencesSnapshot = UserPreferencesSnapshot(),
    val batchPolicy: BatchPolicy,
    val oemSpec: OemCleanSpec,
    val deviceIdentity: DeviceIdentity,
    val permissionHealth: PermissionHealth,
    val cleanSessionState: CleanSessionState = CleanSessionState.Idle,
    val homeVisibleApps: List<AppCacheItem> = emptyList(),
    val cleanableApps: List<AppCacheItem> = emptyList(),
    val cleanableCacheBytes: Long = 0L,
    val trashTotalBytes: Long = 0L,
    val combinedReclaimableBytes: Long = 0L
)

sealed interface MainEffect {
    data class ShowSnackbar(@param:StringRes val messageRes: Int, val args: List<Any> = emptyList()) : MainEffect
    data class LaunchTrashDeleteSender(val pendingIntent: PendingIntent) : MainEffect
    data object OpenSettings : MainEffect
}

sealed interface MainEvent {
    data object RefreshAll : MainEvent
    data object RefreshPermissions : MainEvent
    data object StartStagedClean : MainEvent
    data object PauseClean : MainEvent
    data object ResumeClean : MainEvent
    data object StopClean : MainEvent
    data class ToggleWhitelist(val packageName: String) : MainEvent
    data class UpdateSearchQuery(val query: String) : MainEvent
    data class UpdateAppsFilter(val filter: AppsFilter) : MainEvent
    data class SetEngineTier(val tier: EngineTier) : MainEvent
    data class SetMinThresholdBytes(val bytes: Long) : MainEvent
    data class SetReminderEnabled(val enabled: Boolean) : MainEvent
    data object PurgeLayer1NextChunk : MainEvent
    data object OnLayer1ChunkResult : MainEvent
    data object PurgeLayer2Files : MainEvent
    data class OpenGalleryShortcut(val packageName: String) : MainEvent
    data object OpenUsageAccessSettings : MainEvent
    data object OpenAccessibilitySettings : MainEvent
    data object OpenBatteryOemSettings : MainEvent
    data object OpenOwnAppInfo : MainEvent
    data object OpenAllFilesSettings : MainEvent
    data object CompleteOnboarding : MainEvent
    data object ReopenOnboarding : MainEvent
}

class MainViewModel(
    private val appContext: Context,
    private val container: AppContainer
) : ViewModel() {

    private val rawApps = MutableStateFlow<List<AppCacheItem>>(emptyList())
    private val pendingLayer1Queue = MutableStateFlow<List<TrashLayer1Item>>(emptyList())
    private var scanJob: Job? = null

    private val _state: MutableStateFlow<MainUiState> = run {
        val spec = container.oemDetector.detectSpec()
        val identity = container.oemDetector.detectDeviceIdentity(spec.family)
        MutableStateFlow(
            MainUiState(
                batchPolicy = container.healthMonitor.computePolicy(),
                oemSpec = spec,
                deviceIdentity = identity,
                permissionHealth = readPermissionHealth()
            )
        )
    }
    val state: StateFlow<MainUiState> = _state.asStateFlow()

    private val _effects = Channel<MainEffect>(Channel.BUFFERED)
    val effects: Flow<MainEffect> = _effects.receiveAsFlow()

    init {
        viewModelScope.launch {
            combine(
                container.prefsRepository.preferencesFlow,
                container.orchestrator.sessionState,
                rawApps
            ) { prefs, session, apps ->
                Triple(prefs, session, apps)
            }.collect { (prefs, session, apps) ->
                val updatedApps = apps.map { item ->
                    item.copy(isWhitelisted = item.packageName.raw in prefs.whitelistedPackages)
                }
                _state.update { current ->
                    withDerivedLists(
                        base = current.copy(
                            prefs = prefs,
                            cleanSessionState = session,
                            allApps = updatedApps,
                            showOnboarding = if (!current.onboardingChecked) !prefs.onboardingCompleted else current.showOnboarding,
                            onboardingChecked = true
                        ),
                        apps = updatedApps,
                        prefs = prefs,
                        trash = current.trashSnapshot
                    )
                }
            }
        }
        viewModelScope.launch {
            container.appCleanedEvents.collect { delta ->
                rawApps.update { list ->
                    list.map { item ->
                        if (item.packageName.raw == delta.packageName) {
                            item.copy(
                                cacheBytes = delta.remainingCacheBytes,
                                formattedSize = ByteFormat.format(delta.remainingCacheBytes)
                            )
                        } else {
                            item
                        }
                    }
                }
                if (delta.deltaBytes > 0L) {
                    _state.update { current ->
                        val newUsed = maxOf(0L, current.storageOverview.usedBytes - delta.deltaBytes)
                        current.copy(
                            storageOverview = current.storageOverview.copy(usedBytes = newUsed)
                        )
                    }
                }
            }
        }
        viewModelScope.launch {
            container.orchestrator.sessionState.collect { session ->
                when (session) {
                    is CleanSessionState.Completed -> {
                        emitSnackbar(
                            R.string.snack_cleaned_summary,
                            listOf(ByteFormat.format(session.reclaimedBytes), session.cleanedCount)
                        )
                        container.orchestrator.resetToIdle()
                        refreshAll()
                    }
                    is CleanSessionState.Aborted -> {
                        emitSnackbar(
                            R.string.snack_stopped_summary,
                            listOf(ByteFormat.format(session.reclaimedBytesSoFar))
                        )
                        container.orchestrator.resetToIdle()
                        refreshAll()
                    }
                    else -> Unit
                }
            }
        }
        refreshAll()
    }

    fun onEvent(event: MainEvent) {
        when (event) {
            MainEvent.RefreshAll -> refreshAll()
            MainEvent.RefreshPermissions -> refreshPermissionsAndHealth()
            MainEvent.StartStagedClean -> startStagedClean()
            MainEvent.PauseClean -> container.orchestrator.pause()
            MainEvent.ResumeClean -> container.orchestrator.resume()
            MainEvent.StopClean -> container.orchestrator.stop()
            is MainEvent.ToggleWhitelist -> toggleWhitelist(event.packageName)
            is MainEvent.UpdateSearchQuery -> _state.update { current ->
                current.copy(
                    appsSearchQuery = event.query,
                    filteredApps = applyAppsFilter(current.allApps, event.query, current.appsFilter)
                )
            }
            is MainEvent.UpdateAppsFilter -> _state.update { current ->
                current.copy(
                    appsFilter = event.filter,
                    filteredApps = applyAppsFilter(current.allApps, current.appsSearchQuery, event.filter)
                )
            }
            is MainEvent.SetEngineTier -> viewModelScope.launch {
                container.prefsRepository.setPreferredEngineTier(event.tier)
            }
            is MainEvent.SetMinThresholdBytes -> viewModelScope.launch {
                container.prefsRepository.setMinCacheThresholdBytes(event.bytes)
            }
            is MainEvent.SetReminderEnabled -> viewModelScope.launch {
                container.prefsRepository.setReminderEnabled(event.enabled)
                StorageReminderJobService.updateSchedule(appContext, event.enabled)
            }
            MainEvent.PurgeLayer1NextChunk -> startLayer1Purge()
            MainEvent.OnLayer1ChunkResult -> continueLayer1AfterPrompt()
            MainEvent.PurgeLayer2Files -> purgeLayer2Files()
            is MainEvent.OpenGalleryShortcut -> {
                if (!container.trashCoordinator.openGalleryShortcut(event.packageName)) {
                    emitSnackbar(R.string.snack_gallery_unavailable)
                }
            }
            MainEvent.OpenUsageAccessSettings -> {
                if (!container.oemDetector.openUsageAccessSettings()) {
                    container.oemDetector.openOwnAppDetailsSettings()
                }
            }
            MainEvent.OpenAccessibilitySettings -> {
                if (!container.oemDetector.openAccessibilitySettings()) {
                    container.oemDetector.openOwnAppDetailsSettings()
                }
            }
            MainEvent.OpenBatteryOemSettings -> {
                val jumpedDirect = container.oemDetector.launchBatteryOrAutoStartSettings(_state.value.oemSpec)
                if (!jumpedDirect) emitSnackbar(R.string.snack_settings_fallback)
            }
            MainEvent.OpenOwnAppInfo -> {
                container.oemDetector.openOwnAppDetailsSettings()
            }
            MainEvent.OpenAllFilesSettings -> {
                container.oemDetector.openAllFilesAccessSettings()
            }
            MainEvent.CompleteOnboarding -> viewModelScope.launch {
                container.prefsRepository.setOnboardingCompleted(true)
                _state.update { it.copy(showOnboarding = false) }
                refreshAll()
            }
            MainEvent.ReopenOnboarding -> {
                _state.update { it.copy(showOnboarding = true) }
            }
        }
    }

    private fun refreshAll() {
        scanJob?.cancel()
        scanJob = viewModelScope.launch {
            executeFullScan()
        }
    }

    private suspend fun executeFullScan() = coroutineScope {
        val health = readPermissionHealth()
        val policy = container.healthMonitor.computePolicy()
        _state.update {
            it.copy(
                isScanning = true,
                permissionHealth = health,
                batchPolicy = policy
            )
        }

        val whitelisted = _state.value.prefs.whitelistedPackages
        val storageDeferred = async { container.scanner.readDeviceStorageOverview() }
        val appsDeferred = async {
            if (health.hasUsageAccess) container.scanner.scanAllApps(whitelisted) else emptyList()
        }
        val trashDeferred = async { container.trashCoordinator.scanAll() }

        val storage = storageDeferred.await()
        val scannedApps = appsDeferred.await()
        rawApps.value = scannedApps
        _state.update { current ->
            withDerivedLists(
                base = current.copy(
                    isScanning = false,
                    storageOverview = storage,
                    allApps = scannedApps
                ),
                apps = scannedApps,
                prefs = current.prefs,
                trash = current.trashSnapshot
            )
        }

        if (scannedApps.isNotEmpty()) {
            val prewarmPkgs = scannedApps.take(30).map { it.packageName.raw }
            viewModelScope.launch(Dispatchers.Default) {
                container.iconMemoryCache.prewarm(prewarmPkgs, 120)
            }
        }

        val trash = trashDeferred.await()
        _state.update { current ->
            withDerivedLists(
                base = current.copy(trashSnapshot = trash),
                apps = current.allApps,
                prefs = current.prefs,
                trash = trash
            )
        }
    }

    private fun refreshPermissionsAndHealth() {
        viewModelScope.launch(Dispatchers.Default) {
            val health = readPermissionHealth()
            val policy = container.healthMonitor.computePolicy()
            val hadUsage = _state.value.permissionHealth.hasUsageAccess
            _state.update {
                it.copy(
                    permissionHealth = health,
                    batchPolicy = policy
                )
            }
            if (!hadUsage && health.hasUsageAccess) {
                refreshAll()
            }
        }
    }

    private fun startStagedClean() {
        val snapshot = _state.value
        if (!snapshot.permissionHealth.hasUsageAccess) {
            container.oemDetector.openUsageAccessSettings()
            return
        }
        val queue = snapshot.cleanableApps
        if (queue.isEmpty()) {
            refreshAll()
            return
        }

        val backend = when {
            snapshot.prefs.preferredEngineTier == EngineTier.ACCESSIBILITY_STAGED &&
                snapshot.permissionHealth.hasAccessibilityEnabled -> container.accessibilityBackend
            snapshot.prefs.preferredEngineTier == EngineTier.GUIDED_QUEUE -> container.guidedBackend
            else -> {
                viewModelScope.launch {
                    _effects.send(MainEffect.OpenSettings)
                }
                emitSnackbar(R.string.banner_setup_body)
                return
            }
        }

        container.orchestrator.start(
            queue = queue,
            backend = backend,
            spec = snapshot.oemSpec
        )
    }

    private fun toggleWhitelist(packageName: String) {
        viewModelScope.launch {
            val nowExcluded = container.prefsRepository.toggleWhitelist(packageName)
            val label = _state.value.allApps
                .firstOrNull { it.packageName.raw == packageName }
                ?.appLabel ?: packageName
            emitSnackbar(
                if (nowExcluded) R.string.snack_whitelisted else R.string.snack_unwhitelisted,
                listOf(label)
            )
        }
    }

    private fun startLayer1Purge() {
        val items = _state.value.trashSnapshot?.layer1?.items.orEmpty()
        if (items.isEmpty()) return
        pendingLayer1Queue.value = items
        launchNextLayer1Chunk()
    }

    private fun continueLayer1AfterPrompt() {
        viewModelScope.launch {
            val beforeBytes = _state.value.trashSnapshot?.layer1?.totalBytes ?: 0L
            val currentRemaining = pendingLayer1Queue.value.drop(150)
            if (currentRemaining.isNotEmpty()) {
                pendingLayer1Queue.value = currentRemaining
                launchNextLayer1Chunk()
            } else {
                val updatedTrash = container.trashCoordinator.scanAll()
                val freed = maxOf(0L, beforeBytes - updatedTrash.layer1.totalBytes)
                if (freed > 0L) {
                    container.prefsRepository.addFreedBytes(freed, System.currentTimeMillis())
                    emitSnackbar(R.string.snack_trash_cleared, listOf(ByteFormat.format(freed)))
                }
                _state.update { current ->
                    withDerivedLists(
                        base = current.copy(trashSnapshot = updatedTrash),
                        apps = current.allApps,
                        prefs = current.prefs,
                        trash = updatedTrash
                    )
                }
            }
        }
    }

    private fun launchNextLayer1Chunk() {
        val pi = container.trashCoordinator.nextLayer1DeleteIntent(pendingLayer1Queue.value) ?: return
        viewModelScope.launch {
            _effects.send(MainEffect.LaunchTrashDeleteSender(pi))
        }
    }

    private fun purgeLayer2Files() {
        val files = _state.value.trashSnapshot?.layer2Files.orEmpty()
        if (files.isEmpty()) return
        viewModelScope.launch(Dispatchers.IO) {
            val (_, freed) = container.trashCoordinator.deleteLayer2Files(files)
            if (freed > 0L) {
                container.prefsRepository.addFreedBytes(freed, System.currentTimeMillis())
                emitSnackbar(R.string.snack_trash_cleared, listOf(ByteFormat.format(freed)))
            }
            val updatedTrash = container.trashCoordinator.scanAll()
            _state.update { current ->
                withDerivedLists(
                    base = current.copy(trashSnapshot = updatedTrash),
                    apps = current.allApps,
                    prefs = current.prefs,
                    trash = updatedTrash
                )
            }
        }
    }

    private fun withDerivedLists(
        base: MainUiState,
        apps: List<AppCacheItem>,
        prefs: UserPreferencesSnapshot,
        trash: TrashOverviewSnapshot?
    ): MainUiState {
        val threshold = prefs.minCacheThresholdBytes
        val homeVisible = apps.filter { it.cacheBytes >= threshold }
        val cleanable = homeVisible.filter { !it.isWhitelisted }
        val cacheBytes = cleanable.sumOf { it.cacheBytes }
        val trashBytes = (trash?.layer1?.totalBytes ?: 0L) + (trash?.layer2TotalBytes ?: 0L)
        return base.copy(
            filteredApps = applyAppsFilter(apps, base.appsSearchQuery, base.appsFilter),
            homeVisibleApps = homeVisible,
            cleanableApps = cleanable,
            cleanableCacheBytes = cacheBytes,
            trashTotalBytes = trashBytes,
            combinedReclaimableBytes = cacheBytes + trashBytes
        )
    }

    private fun readPermissionHealth(): PermissionHealth {
        val pm = appContext.getSystemService(PowerManager::class.java)
        val batteryIgnored = pm?.isIgnoringBatteryOptimizations(appContext.packageName) == true
        val hasNotificationPermission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            appContext.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) ==
                PackageManager.PERMISSION_GRANTED
        } else {
            true
        }
        return PermissionHealth(
            hasUsageAccess = container.scanner.hasUsageAccessPermission(),
            hasAccessibilityEnabled = container.accessibilityBridge.isServiceConnected() ||
                AccessibilityCleanerService.isServiceEnabledInSystem(appContext),
            hasAllFilesAccess = android.os.Environment.isExternalStorageManager(),
            isIgnoringBatteryOptimizations = batteryIgnored,
            isAndroid17Plus = Build.VERSION.SDK_INT >= 37,
            hasNotificationPermission = hasNotificationPermission
        )
    }

    private fun applyAppsFilter(
        apps: List<AppCacheItem>,
        query: String,
        filter: AppsFilter
    ): List<AppCacheItem> {
        val q = query.trim()
        return apps.filter { item ->
            val matchesQuery = q.isEmpty() || item.appLabel.contains(q, ignoreCase = true)
            val matchesFilter = when (filter) {
                AppsFilter.ALL -> true
                AppsFilter.OVER_150_MB -> item.cacheBytes >= 150_000_000L
                AppsFilter.EXCLUDED -> item.isWhitelisted
            }
            matchesQuery && matchesFilter
        }
    }

    private fun emitSnackbar(@StringRes messageRes: Int, args: List<Any> = emptyList()) {
        viewModelScope.launch {
            _effects.send(MainEffect.ShowSnackbar(messageRes, args))
        }
    }

    class Factory(
        private val appContext: Context,
        private val container: AppContainer
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return MainViewModel(appContext, container) as T
        }
    }
}
