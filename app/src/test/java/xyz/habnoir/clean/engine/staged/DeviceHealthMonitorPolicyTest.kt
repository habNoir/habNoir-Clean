package xyz.habnoir.clean.engine.staged

import android.os.PowerManager
import org.junit.Assert.assertEquals
import org.junit.Test
import xyz.habnoir.clean.model.DeviceTier

class DeviceHealthMonitorPolicyTest {

    private val gb = 1024L * 1024L * 1024L

    @Test
    fun `low ram device yields 5 apps per batch`() {
        val policy = DeviceHealthMonitor.computeBatchPolicy(
            totalRamBytes = 4L * gb,
            isLowRamDevice = false,
            isLowMemoryNow = false,
            thermalStatus = PowerManager.THERMAL_STATUS_NONE,
            memoryTrimTriggered = false
        )
        assertEquals(DeviceTier.LOW_RAM, policy.tier)
        assertEquals(5, policy.batchSize)
        assertEquals(800L, policy.interAppDelayMs)
        assertEquals(3_500L, policy.batchCooldownMs)
    }

    @Test
    fun `mid range 6gb device yields 8 apps per batch`() {
        val policy = DeviceHealthMonitor.computeBatchPolicy(
            totalRamBytes = 6L * gb,
            isLowRamDevice = false,
            isLowMemoryNow = false,
            thermalStatus = PowerManager.THERMAL_STATUS_LIGHT,
            memoryTrimTriggered = false
        )
        assertEquals(DeviceTier.MID_RANGE, policy.tier)
        assertEquals(8, policy.batchSize)
        assertEquals(500L, policy.interAppDelayMs)
        assertEquals(2_500L, policy.batchCooldownMs)
    }

    @Test
    fun `flagship 12gb device yields 12 apps per batch`() {
        val policy = DeviceHealthMonitor.computeBatchPolicy(
            totalRamBytes = 12L * gb,
            isLowRamDevice = false,
            isLowMemoryNow = false,
            thermalStatus = PowerManager.THERMAL_STATUS_NONE,
            memoryTrimTriggered = false
        )
        assertEquals(DeviceTier.FLAGSHIP, policy.tier)
        assertEquals(12, policy.batchSize)
        assertEquals(350L, policy.interAppDelayMs)
        assertEquals(1_500L, policy.batchCooldownMs)
    }

    @Test
    fun `moderate thermal status throttles down to 3 apps and 5s cooldown`() {
        val policy = DeviceHealthMonitor.computeBatchPolicy(
            totalRamBytes = 12L * gb,
            isLowRamDevice = false,
            isLowMemoryNow = false,
            thermalStatus = PowerManager.THERMAL_STATUS_MODERATE,
            memoryTrimTriggered = false
        )
        assertEquals(DeviceTier.THERMAL_GUARD, policy.tier)
        assertEquals(3, policy.batchSize)
        assertEquals(1_000L, policy.interAppDelayMs)
        assertEquals(5_000L, policy.batchCooldownMs)
    }

    @Test
    fun `onTrimMemory triggers thermal guard policy`() {
        val policy = DeviceHealthMonitor.computeBatchPolicy(
            totalRamBytes = 8L * gb,
            isLowRamDevice = false,
            isLowMemoryNow = false,
            thermalStatus = PowerManager.THERMAL_STATUS_NONE,
            memoryTrimTriggered = true
        )
        assertEquals(DeviceTier.THERMAL_GUARD, policy.tier)
        assertEquals(3, policy.batchSize)
    }
}
