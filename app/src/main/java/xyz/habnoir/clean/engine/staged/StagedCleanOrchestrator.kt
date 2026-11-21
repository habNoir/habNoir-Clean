package xyz.habnoir.clean.engine.staged

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import xyz.habnoir.clean.engine.oem.OemCleanSpec
import xyz.habnoir.clean.model.AbortReason
import xyz.habnoir.clean.model.AppCacheItem
import xyz.habnoir.clean.model.BatchPolicy
import xyz.habnoir.clean.model.CleanSessionState
import xyz.habnoir.clean.model.DeviceTier
import xyz.habnoir.clean.model.EngineTier
import xyz.habnoir.clean.model.PauseReason
import kotlin.math.ceil

enum class SkipReason {
    BLACKLIST_BLOCKED,
    TIMEOUT,
    BUTTON_NOT_FOUND,
    USER_WHITELISTED
}

sealed interface StepResult {
    data object Cleared : StepResult
    data object AlreadyClean : StepResult
    data class Skipped(val reason: SkipReason) : StepResult
    data class Failed(val retryable: Boolean, val reason: String) : StepResult
}

interface CleanerBackend {
    val tier: EngineTier
    suspend fun isReady(): Boolean
    suspend fun clearAppCache(target: AppCacheItem, spec: OemCleanSpec): StepResult
}

fun interface PolicyProvider {
    fun currentPolicy(): BatchPolicy
}

fun interface CacheDeltaReader {
    suspend fun queryCacheBytes(packageName: String): Long
}

class StagedCleanOrchestrator(
    private val policyProvider: PolicyProvider,
    private val cacheDeltaReader: CacheDeltaReader,
    private val onBytesReclaimedCommitted: suspend (Long) -> Unit,
    private val onForegroundStart: () -> Unit = {},
    private val onForegroundUpdate: (title: String, processed: Int, total: Int) -> Unit = { _, _, _ -> },
    private val onForegroundStop: () -> Unit = {},
    private val onAppCleaned: (packageName: String, remainingCacheBytes: Long, deltaBytes: Long) -> Unit = { _, _, _ -> },
    private val onSessionFinished: (isGuidedMode: Boolean) -> Unit = {},
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.Default)
) {
    private val sessionMutex = Mutex()
    private val _sessionState = MutableStateFlow<CleanSessionState>(CleanSessionState.Idle)
    val sessionState: StateFlow<CleanSessionState> = _sessionState.asStateFlow()

    private val pausedGate = MutableStateFlow(false)
    private val pauseReasonState = MutableStateFlow<PauseReason?>(null)
    private var activeJob: Job? = null

    fun start(
        queue: List<AppCacheItem>,
        backend: CleanerBackend,
        spec: OemCleanSpec
    ) {
        if (queue.isEmpty()) return
        scope.launch {
            sessionMutex.withLock {
                if (activeJob?.isActive == true) return@withLock
                pausedGate.value = false
                pauseReasonState.value = null
                activeJob = scope.launch {
                    runSession(queue, backend, spec)
                }
            }
        }
    }

    fun pause(reason: PauseReason = PauseReason.USER_REQUEST) {
        pausedGate.value = true
        pauseReasonState.value = reason
        _sessionState.update { current ->
            when (current) {
                is CleanSessionState.Cleaning -> current.copy(isPaused = true, pauseReason = reason)
                is CleanSessionState.Cooldown -> current.copy(isPaused = true)
                else -> current
            }
        }
    }

    fun resume() {
        pausedGate.value = false
        pauseReasonState.value = null
        _sessionState.update { current ->
            when (current) {
                is CleanSessionState.Cleaning -> current.copy(isPaused = false, pauseReason = null)
                is CleanSessionState.Cooldown -> current.copy(isPaused = false)
                else -> current
            }
        }
    }

    fun stop(reason: AbortReason = AbortReason.USER_STOP) {
        val snapshot = _sessionState.value
        val currentReclaimed = when (snapshot) {
            is CleanSessionState.Cleaning -> snapshot.reclaimedBytesSoFar
            is CleanSessionState.Cooldown -> snapshot.reclaimedBytesSoFar
            else -> 0L
        }
        val isGuided = when (snapshot) {
            is CleanSessionState.Cleaning -> snapshot.isGuidedMode
            is CleanSessionState.Cooldown -> snapshot.isGuidedMode
            else -> true
        }
        val wasActive = activeJob?.isActive == true
        pausedGate.value = false
        activeJob?.cancel()
        activeJob = null
        onForegroundStop()
        _sessionState.update { CleanSessionState.Aborted(currentReclaimed, reason) }
        if (wasActive) {
            onSessionFinished(isGuided)
        }
    }

    fun resetToIdle() {
        if (activeJob?.isActive == true) return
        _sessionState.update { CleanSessionState.Idle }
    }

    private suspend fun runSession(
        queue: List<AppCacheItem>,
        backend: CleanerBackend,
        spec: OemCleanSpec
    ) {
        onForegroundStart()
        var reclaimedTotal = 0L
        var cleanedCount = 0
        var skippedCount = 0
        var index = 0
        var batchNumber = 0
        val totalApps = queue.size
        val isGuided = backend.tier == EngineTier.GUIDED_QUEUE

        try {
            while (index < totalApps) {
                val policy = policyProvider.currentPolicy()
                val remaining = totalApps - index
                val currentBatchCount = minOf(policy.batchSize, remaining)
                batchNumber++
                val estimatedTotalBatches = batchNumber + ceil(
                    maxOf(0, remaining - currentBatchCount).toDouble() / policy.batchSize
                ).toInt()

                val batchApps = queue.subList(index, index + currentBatchCount)
                for ((posInBatch, app) in batchApps.withIndex()) {
                    awaitIfPaused(
                        currentBatch = batchNumber,
                        totalBatches = estimatedTotalBatches,
                        processed = index,
                        totalApps = totalApps,
                        app = app,
                        reclaimed = reclaimedTotal,
                        policy = policy,
                        isGuided = isGuided
                    )

                    _sessionState.update {
                        CleanSessionState.Cleaning(
                            currentBatch = batchNumber,
                            totalBatches = estimatedTotalBatches,
                            processedApps = index,
                            totalApps = totalApps,
                            activeAppLabel = app.appLabel,
                            activeAppSize = app.formattedSize,
                            reclaimedBytesSoFar = reclaimedTotal,
                            policy = policy,
                            isPaused = false,
                            isGuidedMode = isGuided
                        )
                    }
                    onForegroundUpdate(app.appLabel, index + 1, totalApps)

                    var outcome = backend.clearAppCache(app, spec)
                    if (outcome is StepResult.Failed && outcome.retryable) {
                        awaitIfPaused(
                            batchNumber,
                            estimatedTotalBatches,
                            index,
                            totalApps,
                            app,
                            reclaimedTotal,
                            policy,
                            isGuided
                        )
                        outcome = backend.clearAppCache(app, spec)
                    }

                    when (outcome) {
                        StepResult.Cleared -> {
                            cleanedCount++
                            val afterBytes = cacheDeltaReader.queryCacheBytes(app.packageName.raw)
                            val delta = maxOf(0L, app.cacheBytes - afterBytes)
                            val effectiveDelta = if (delta > 0L) delta else app.cacheBytes
                            val remainingCache = if (delta > 0L) afterBytes else 0L
                            reclaimedTotal += effectiveDelta
                            onAppCleaned(app.packageName.raw, remainingCache, effectiveDelta)
                        }
                        StepResult.AlreadyClean -> {
                            cleanedCount++
                            onAppCleaned(app.packageName.raw, 0L, 0L)
                        }
                        is StepResult.Skipped,
                        is StepResult.Failed -> {
                            skippedCount++
                        }
                    }

                    index++
                    _sessionState.update {
                        CleanSessionState.Cleaning(
                            currentBatch = batchNumber,
                            totalBatches = estimatedTotalBatches,
                            processedApps = index,
                            totalApps = totalApps,
                            activeAppLabel = app.appLabel,
                            activeAppSize = app.formattedSize,
                            reclaimedBytesSoFar = reclaimedTotal,
                            policy = policy,
                            isPaused = pausedGate.value,
                            pauseReason = pauseReasonState.value,
                            isGuidedMode = isGuided
                        )
                    }

                    if (posInBatch < batchApps.lastIndex) {
                        awaitIfPaused(
                            batchNumber,
                            estimatedTotalBatches,
                            index,
                            totalApps,
                            app,
                            reclaimedTotal,
                            policy,
                            isGuided
                        )
                        delay(policy.interAppDelayMs)
                    }
                }

                if (index < totalApps) {
                    runCooldown(
                        batchNumber = batchNumber,
                        totalBatches = estimatedTotalBatches,
                        processedApps = index,
                        totalApps = totalApps,
                        cooldownMs = policy.batchCooldownMs,
                        reclaimed = reclaimedTotal,
                        isThermal = policy.tier == DeviceTier.THERMAL_GUARD,
                        isGuided = isGuided
                    )
                }
            }

            if (reclaimedTotal > 0L) {
                onBytesReclaimedCommitted(reclaimedTotal)
            }
            _sessionState.update {
                CleanSessionState.Completed(
                    cleanedCount = cleanedCount,
                    skippedCount = skippedCount,
                    reclaimedBytes = reclaimedTotal
                )
            }
            onSessionFinished(isGuided)
        } catch (ce: CancellationException) {
            if (reclaimedTotal > 0L) {
                onBytesReclaimedCommitted(reclaimedTotal)
            }
            throw ce
        } finally {
            onForegroundStop()
        }
    }

    private suspend fun awaitIfPaused(
        currentBatch: Int,
        totalBatches: Int,
        processed: Int,
        totalApps: Int,
        app: AppCacheItem,
        reclaimed: Long,
        policy: BatchPolicy,
        isGuided: Boolean
    ) {
        if (!pausedGate.value) return
        _sessionState.update {
            CleanSessionState.Cleaning(
                currentBatch = currentBatch,
                totalBatches = totalBatches,
                processedApps = processed,
                totalApps = totalApps,
                activeAppLabel = app.appLabel,
                activeAppSize = app.formattedSize,
                reclaimedBytesSoFar = reclaimed,
                policy = policy,
                isPaused = true,
                pauseReason = pauseReasonState.value,
                isGuidedMode = isGuided
            )
        }
        pausedGate.first { !it }
    }

    private suspend fun runCooldown(
        batchNumber: Int,
        totalBatches: Int,
        processedApps: Int,
        totalApps: Int,
        cooldownMs: Long,
        reclaimed: Long,
        isThermal: Boolean,
        isGuided: Boolean
    ) {
        var remaining = cooldownMs
        val step = 250L
        while (remaining > 0L) {
            if (pausedGate.value) {
                _sessionState.update {
                    CleanSessionState.Cooldown(
                        currentBatch = batchNumber,
                        totalBatches = totalBatches,
                        processedApps = processedApps,
                        totalApps = totalApps,
                        remainingMs = remaining,
                        reclaimedBytesSoFar = reclaimed,
                        isThermalThrottled = isThermal,
                        isPaused = true,
                        isGuidedMode = isGuided
                    )
                }
                pausedGate.first { !it }
            }
            _sessionState.update {
                CleanSessionState.Cooldown(
                    currentBatch = batchNumber,
                    totalBatches = totalBatches,
                    processedApps = processedApps,
                    totalApps = totalApps,
                    remainingMs = remaining,
                    reclaimedBytesSoFar = reclaimed,
                    isThermalThrottled = isThermal,
                    isPaused = false,
                    isGuidedMode = isGuided
                )
            }
            val slice = minOf(step, remaining)
            delay(slice)
            remaining -= slice
        }
    }
}
