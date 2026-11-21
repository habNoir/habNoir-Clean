package xyz.habnoir.clean.engine.staged

import android.app.ActivityManager
import android.content.ComponentCallbacks2
import android.content.Context
import android.content.res.Configuration
import android.os.PowerManager
import xyz.habnoir.clean.model.BatchPolicy
import xyz.habnoir.clean.model.DeviceTier
import java.util.concurrent.atomic.AtomicBoolean

class DeviceHealthMonitor(private val context: Context) : ComponentCallbacks2 {

    private val memoryTrimTriggered = AtomicBoolean(false)

    fun computePolicy(): BatchPolicy {
        val am = context.getSystemService(ActivityManager::class.java)
        val pm = context.getSystemService(PowerManager::class.java)

        val memInfo = ActivityManager.MemoryInfo()
        am?.getMemoryInfo(memInfo)

        val thermal = pm?.currentThermalStatus ?: PowerManager.THERMAL_STATUS_NONE
        return computeBatchPolicy(
            totalRamBytes = memInfo.totalMem,
            isLowRamDevice = am?.isLowRamDevice == true,
            isLowMemoryNow = memInfo.lowMemory,
            thermalStatus = thermal,
            memoryTrimTriggered = memoryTrimTriggered.getAndSet(false)
        )
    }

    override fun onTrimMemory(level: Int) {
        if (level >= ComponentCallbacks2.TRIM_MEMORY_BACKGROUND) {
            memoryTrimTriggered.set(true)
        }
    }

    override fun onConfigurationChanged(newConfig: Configuration) = Unit

    @Deprecated("Deprecated in Java")
    override fun onLowMemory() {
        memoryTrimTriggered.set(true)
    }

    companion object {
        private const val GB_BYTES = 1024.0 * 1024.0 * 1024.0

        fun computeBatchPolicy(
            totalRamBytes: Long,
            isLowRamDevice: Boolean,
            isLowMemoryNow: Boolean,
            thermalStatus: Int,
            memoryTrimTriggered: Boolean
        ): BatchPolicy {
            val totalRamGb = totalRamBytes / GB_BYTES
            val isThermalHot = thermalStatus >= PowerManager.THERMAL_STATUS_MODERATE

            return when {
                isThermalHot || isLowMemoryNow || memoryTrimTriggered -> BatchPolicy(
                    tier = DeviceTier.THERMAL_GUARD,
                    batchSize = 3,
                    interAppDelayMs = 1_000L,
                    batchCooldownMs = 5_000L
                )
                isLowRamDevice || totalRamGb <= 4.2 -> BatchPolicy(
                    tier = DeviceTier.LOW_RAM,
                    batchSize = 5,
                    interAppDelayMs = 800L,
                    batchCooldownMs = 3_500L
                )
                totalRamGb <= 8.5 -> BatchPolicy(
                    tier = DeviceTier.MID_RANGE,
                    batchSize = 8,
                    interAppDelayMs = 500L,
                    batchCooldownMs = 2_500L
                )
                else -> BatchPolicy(
                    tier = DeviceTier.FLAGSHIP,
                    batchSize = 12,
                    interAppDelayMs = 350L,
                    batchCooldownMs = 1_500L
                )
            }
        }
    }
}
