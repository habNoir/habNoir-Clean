package xyz.habnoir.clean.engine.staged

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import kotlinx.coroutines.delay
import xyz.habnoir.clean.engine.oem.OemCleanSpec
import xyz.habnoir.clean.model.AppCacheItem
import xyz.habnoir.clean.model.EngineTier

class GuidedQueueCleanerBackend(
    private val context: Context
) : CleanerBackend {

    override val tier: EngineTier = EngineTier.GUIDED_QUEUE

    override suspend fun isReady(): Boolean = true

    override suspend fun clearAppCache(target: AppCacheItem, spec: OemCleanSpec): StepResult {
        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = Uri.fromParts("package", target.packageName.raw, null)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        val opened = runCatching {
            context.startActivity(intent)
            true
        }.getOrDefault(false)

        if (!opened) return StepResult.Skipped(SkipReason.BUTTON_NOT_FOUND)
        delay(GUIDED_PACE_DELAY_MS)
        return StepResult.Cleared
    }

    companion object {
        private const val GUIDED_PACE_DELAY_MS = 3_500L
    }
}
