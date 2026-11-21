package xyz.habnoir.clean.model

import java.util.Locale

@JvmInline
value class PackageName(val raw: String)

enum class EngineTier {
    ACCESSIBILITY_STAGED,
    GUIDED_QUEUE
}

enum class DeviceTier {
    LOW_RAM,
    MID_RANGE,
    FLAGSHIP,
    THERMAL_GUARD
}

data class BatchPolicy(
    val tier: DeviceTier,
    val batchSize: Int,
    val interAppDelayMs: Long,
    val batchCooldownMs: Long
)

data class AppCacheItem(
    val packageName: PackageName,
    val appLabel: String,
    val cacheBytes: Long,
    val formattedSize: String,
    val isWhitelisted: Boolean
)

data class DeviceStorageOverview(
    val totalBytes: Long,
    val freeBytes: Long,
    val usedBytes: Long
) {
    val privateNonCleanableBytes: Long
        get() = maxOf(0L, usedBytes)
}

enum class PauseReason {
    USER_REQUEST,
    USER_SCREEN_TOUCH
}

enum class AbortReason {
    USER_STOP,
    ACCESSIBILITY_REVOKED
}

sealed interface CleanSessionState {
    data object Idle : CleanSessionState

    data class Scanning(
        val scannedCount: Int,
        val totalPackages: Int
    ) : CleanSessionState

    data class Cleaning(
        val currentBatch: Int,
        val totalBatches: Int,
        val processedApps: Int,
        val totalApps: Int,
        val activeAppLabel: String,
        val activeAppSize: String,
        val reclaimedBytesSoFar: Long,
        val policy: BatchPolicy,
        val isPaused: Boolean,
        val pauseReason: PauseReason? = null,
        val isGuidedMode: Boolean = false
    ) : CleanSessionState

    data class Cooldown(
        val currentBatch: Int,
        val totalBatches: Int,
        val processedApps: Int,
        val totalApps: Int,
        val remainingMs: Long,
        val reclaimedBytesSoFar: Long,
        val isThermalThrottled: Boolean,
        val isPaused: Boolean = false,
        val isGuidedMode: Boolean = false
    ) : CleanSessionState

    data class Completed(
        val cleanedCount: Int,
        val skippedCount: Int,
        val reclaimedBytes: Long
    ) : CleanSessionState

    data class Aborted(
        val reclaimedBytesSoFar: Long,
        val reason: AbortReason
    ) : CleanSessionState
}

data class TrashLayer1Item(
    val uriString: String,
    val sizeBytes: Long
)

data class TrashLayer1Summary(
    val fileCount: Int,
    val totalBytes: Long,
    val items: List<TrashLayer1Item>
)

data class TrashLayer2Folder(
    val relativePath: String,
    val fileCount: Int,
    val totalBytes: Long
)

data class OemGalleryShortcut(
    val packageName: String,
    val label: String
)

data class PermissionHealth(
    val hasUsageAccess: Boolean,
    val hasAccessibilityEnabled: Boolean,
    val hasAllFilesAccess: Boolean,
    val isIgnoringBatteryOptimizations: Boolean,
    val isAndroid17Plus: Boolean,
    val hasNotificationPermission: Boolean = true
)

object ByteFormat {
    private const val KB = 1_000.0
    private const val MB = 1_000_000.0
    private const val GB = 1_000_000_000.0

    fun format(bytes: Long, locale: Locale = Locale.forLanguageTag("id-ID")): String {
        val safe = maxOf(0L, bytes)
        return when {
            safe >= GB -> String.format(locale, "%.2f GB", safe / GB)
            safe >= MB -> {
                val value = safe / MB
                if (value >= 100.0) String.format(locale, "%.0f MB", value)
                else String.format(locale, "%.1f MB", value)
            }
            safe >= KB -> String.format(locale, "%.0f KB", safe / KB)
            else -> "$safe B"
        }
    }
}
