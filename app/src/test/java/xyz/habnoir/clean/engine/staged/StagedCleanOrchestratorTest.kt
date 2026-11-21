package xyz.habnoir.clean.engine.staged

import app.cash.turbine.test
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import xyz.habnoir.clean.engine.oem.OemCleanSpec
import xyz.habnoir.clean.engine.oem.OemFamily
import xyz.habnoir.clean.engine.oem.OemSpecRegistry
import xyz.habnoir.clean.model.AbortReason
import xyz.habnoir.clean.model.AppCacheItem
import xyz.habnoir.clean.model.BatchPolicy
import xyz.habnoir.clean.model.CleanSessionState
import xyz.habnoir.clean.model.DeviceTier
import xyz.habnoir.clean.model.EngineTier
import xyz.habnoir.clean.model.PackageName

@OptIn(ExperimentalCoroutinesApi::class)
class StagedCleanOrchestratorTest {

    private val spec: OemCleanSpec = OemSpecRegistry.specFor(OemFamily.VIVO_IQOO_FUNTOUCH)

    private fun makeApps(count: Int, bytesEach: Long = 100_000_000L): List<AppCacheItem> =
        (1..count).map { idx ->
            AppCacheItem(
                packageName = PackageName("com.test.app$idx"),
                appLabel = "App $idx",
                cacheBytes = bytesEach,
                formattedSize = "100 MB",
                isWhitelisted = false
            )
        }

    @Test
    fun `processes apps in batches with cooldown and aggregates reclaimed bytes`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val scope = TestScope(dispatcher)
        var committedBytes = 0L

        val orchestrator = StagedCleanOrchestrator(
            policyProvider = {
                BatchPolicy(DeviceTier.LOW_RAM, batchSize = 2, interAppDelayMs = 100L, batchCooldownMs = 500L)
            },
            cacheDeltaReader = { 0L },
            onBytesReclaimedCommitted = { committedBytes += it },
            scope = scope
        )

        val fakeBackend = object : CleanerBackend {
            override val tier = EngineTier.ACCESSIBILITY_STAGED
            override suspend fun isReady() = true
            override suspend fun clearAppCache(target: AppCacheItem, spec: OemCleanSpec) = StepResult.Cleared
        }

        orchestrator.sessionState.test {
            assertEquals(CleanSessionState.Idle, awaitItem())

            orchestrator.start(makeApps(3, 50_000_000L), fakeBackend, spec)
            scope.advanceTimeBy(1000L)
            scope.runCurrent()

            var completed: CleanSessionState.Completed? = null
            var sawCooldown = false
            while (completed == null) {
                val item = awaitItem()
                if (item is CleanSessionState.Cooldown) sawCooldown = true
                if (item is CleanSessionState.Completed) completed = item
            }

            assertTrue("Expected cooldown between batch 1 (2 apps) and batch 2 (1 app)", sawCooldown)
            assertEquals(3, completed.cleanedCount)
            assertEquals(0, completed.skippedCount)
            assertEquals(150_000_000L, completed.reclaimedBytes)
            assertEquals(150_000_000L, committedBytes)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `retries once on retryable failure then skips cleanly`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val scope = TestScope(dispatcher)
        val attempts = mutableMapOf<String, Int>()

        val orchestrator = StagedCleanOrchestrator(
            policyProvider = { BatchPolicy(DeviceTier.MID_RANGE, 5, 10L, 10L) },
            cacheDeltaReader = { 0L },
            onBytesReclaimedCommitted = {},
            scope = scope
        )

        val flakyBackend = object : CleanerBackend {
            override val tier = EngineTier.ACCESSIBILITY_STAGED
            override suspend fun isReady() = true
            override suspend fun clearAppCache(target: AppCacheItem, spec: OemCleanSpec): StepResult {
                val count = (attempts[target.packageName.raw] ?: 0) + 1
                attempts[target.packageName.raw] = count
                return if (target.packageName.raw.endsWith("1")) {
                    StepResult.Failed(retryable = true, reason = "timeout")
                } else {
                    StepResult.Cleared
                }
            }
        }

        orchestrator.sessionState.test {
            awaitItem() // Idle
            orchestrator.start(makeApps(2, 10_000_000L), flakyBackend, spec)
            scope.advanceTimeBy(500L)
            scope.runCurrent()

            var completed: CleanSessionState.Completed? = null
            while (completed == null) {
                val item = awaitItem()
                if (item is CleanSessionState.Completed) completed = item
            }

            assertEquals(2, attempts["com.test.app1"])
            assertEquals(1, attempts["com.test.app2"])
            assertEquals(1, completed.cleanedCount)
            assertEquals(1, completed.skippedCount)
            assertEquals(10_000_000L, completed.reclaimedBytes)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `stop aborts immediately and reports Aborted`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val scope = TestScope(dispatcher)

        val orchestrator = StagedCleanOrchestrator(
            policyProvider = { BatchPolicy(DeviceTier.LOW_RAM, 2, 1_000L, 5_000L) },
            cacheDeltaReader = { 0L },
            onBytesReclaimedCommitted = {},
            scope = scope
        )

        val slowBackend = object : CleanerBackend {
            override val tier = EngineTier.ACCESSIBILITY_STAGED
            override suspend fun isReady() = true
            override suspend fun clearAppCache(target: AppCacheItem, spec: OemCleanSpec): StepResult {
                kotlinx.coroutines.delay(500L)
                return StepResult.Cleared
            }
        }

        orchestrator.start(makeApps(5), slowBackend, spec)
        scope.advanceTimeBy(100L)
        scope.runCurrent()

        orchestrator.stop(AbortReason.USER_STOP)
        scope.runCurrent()

        val state = orchestrator.sessionState.value
        assertTrue(state is CleanSessionState.Aborted)
        assertEquals(AbortReason.USER_STOP, (state as CleanSessionState.Aborted).reason)
    }

    @Test
    fun `emits real time per-app delta and pauses immediately during cooldown`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val scope = TestScope(dispatcher)
        val cleanedDeltas = mutableListOf<Triple<String, Long, Long>>()
        var finishedCalled = false

        val orchestrator = StagedCleanOrchestrator(
            policyProvider = {
                BatchPolicy(DeviceTier.LOW_RAM, batchSize = 1, interAppDelayMs = 50L, batchCooldownMs = 1_000L)
            },
            cacheDeltaReader = { 5_000_000L },
            onBytesReclaimedCommitted = {},
            onAppCleaned = { pkg, remaining, delta -> cleanedDeltas.add(Triple(pkg, remaining, delta)) },
            onSessionFinished = { finishedCalled = true },
            scope = scope
        )

        val instantBackend = object : CleanerBackend {
            override val tier = EngineTier.ACCESSIBILITY_STAGED
            override suspend fun isReady() = true
            override suspend fun clearAppCache(target: AppCacheItem, spec: OemCleanSpec) = StepResult.Cleared
        }

        orchestrator.start(makeApps(2, 25_000_000L), instantBackend, spec)
        scope.advanceTimeBy(100L)
        scope.runCurrent()

        assertEquals(1, cleanedDeltas.size)
        assertEquals("com.test.app1", cleanedDeltas[0].first)
        assertEquals(5_000_000L, cleanedDeltas[0].second)
        assertEquals(20_000_000L, cleanedDeltas[0].third)

        val duringCooldown = orchestrator.sessionState.value
        assertTrue(duringCooldown is CleanSessionState.Cooldown)

        orchestrator.pause()
        scope.runCurrent()
        val pausedCooldown = orchestrator.sessionState.value
        assertTrue(pausedCooldown is CleanSessionState.Cooldown && pausedCooldown.isPaused)

        scope.advanceTimeBy(2_000L)
        scope.runCurrent()
        assertEquals(1, cleanedDeltas.size)

        orchestrator.resume()
        scope.advanceTimeBy(2_000L)
        scope.runCurrent()

        assertEquals(2, cleanedDeltas.size)
        assertTrue(finishedCalled)
        assertTrue(orchestrator.sessionState.value is CleanSessionState.Completed)
    }
}
