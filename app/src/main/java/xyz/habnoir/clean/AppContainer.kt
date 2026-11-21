package xyz.habnoir.clean

import android.content.Context
import android.content.Intent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import xyz.habnoir.clean.data.prefs.UserPrefsRepository
import xyz.habnoir.clean.engine.accessibility.AccessibilityCleanerBackend
import xyz.habnoir.clean.engine.accessibility.AccessibilitySessionBridge
import xyz.habnoir.clean.engine.oem.OemDetector
import xyz.habnoir.clean.engine.scanner.AppIconMemoryCache
import xyz.habnoir.clean.engine.scanner.StorageStatsCacheScanner
import xyz.habnoir.clean.engine.scanner.SystemProcessGuard
import xyz.habnoir.clean.engine.staged.CleanExecutionForegroundService
import xyz.habnoir.clean.engine.staged.DeviceHealthMonitor
import xyz.habnoir.clean.engine.staged.GuidedQueueCleanerBackend
import xyz.habnoir.clean.engine.staged.StagedCleanOrchestrator
import xyz.habnoir.clean.engine.trash.AllowlistFolderTrashSource
import xyz.habnoir.clean.engine.trash.MediaStoreTrashSource
import xyz.habnoir.clean.engine.trash.OemGalleryTrashShortcuts
import xyz.habnoir.clean.engine.trash.TrashCoordinator
import xyz.habnoir.clean.model.PauseReason

data class AppCleanedDelta(
    val packageName: String,
    val remainingCacheBytes: Long,
    val deltaBytes: Long
)

class AppContainer(context: Context) {
    private val appContext = context.applicationContext
    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private val _appCleanedEvents = MutableSharedFlow<AppCleanedDelta>(extraBufferCapacity = 64)
    val appCleanedEvents: SharedFlow<AppCleanedDelta> = _appCleanedEvents.asSharedFlow()

    val prefsRepository = UserPrefsRepository(appContext)
    val oemDetector = OemDetector(appContext)
    val systemProcessGuard = SystemProcessGuard(appContext)
    val iconMemoryCache = AppIconMemoryCache(appContext)
    val scanner = StorageStatsCacheScanner(appContext, systemProcessGuard)
    val healthMonitor = DeviceHealthMonitor(appContext)
    val accessibilityBridge = AccessibilitySessionBridge()

    val orchestrator: StagedCleanOrchestrator = StagedCleanOrchestrator(
        policyProvider = { healthMonitor.computePolicy() },
        cacheDeltaReader = { pkg -> scanner.querySinglePackageCacheBytes(pkg) },
        onBytesReclaimedCommitted = { bytes ->
            prefsRepository.addFreedBytes(bytes, System.currentTimeMillis())
        },
        onForegroundStart = {
            CleanExecutionForegroundService.startOrUpdate(appContext, appContext.getString(R.string.app_name), 0, 0)
        },
        onForegroundUpdate = { label, done, total ->
            CleanExecutionForegroundService.startOrUpdate(appContext, label, done, total)
        },
        onForegroundStop = {
            CleanExecutionForegroundService.stopService(appContext)
        },
        onAppCleaned = { pkg, remaining, delta ->
            _appCleanedEvents.tryEmit(AppCleanedDelta(pkg, remaining, delta))
        },
        onSessionFinished = { isGuidedMode ->
            if (!isGuidedMode) {
                runCatching {
                    val returnIntent = Intent(appContext, MainActivity::class.java).apply {
                        addFlags(
                            Intent.FLAG_ACTIVITY_NEW_TASK or
                                Intent.FLAG_ACTIVITY_SINGLE_TOP or
                                Intent.FLAG_ACTIVITY_REORDER_TO_FRONT
                        )
                    }
                    appContext.startActivity(returnIntent)
                }
            }
        },
        scope = appScope
    )

    val accessibilityBackend = AccessibilityCleanerBackend(
        context = appContext,
        bridge = accessibilityBridge,
        onUserTouchDetected = {
            orchestrator.pause(PauseReason.USER_SCREEN_TOUCH)
        }
    )

    val guidedBackend = GuidedQueueCleanerBackend(appContext)

    val trashCoordinator = TrashCoordinator(
        mediaStoreSource = MediaStoreTrashSource(appContext),
        folderTrashSource = AllowlistFolderTrashSource(),
        galleryShortcuts = OemGalleryTrashShortcuts(appContext)
    )
}
