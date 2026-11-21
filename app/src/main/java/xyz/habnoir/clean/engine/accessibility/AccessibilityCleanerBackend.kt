package xyz.habnoir.clean.engine.accessibility

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.SystemClock
import android.provider.Settings
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull
import xyz.habnoir.clean.engine.oem.OemCleanSpec
import xyz.habnoir.clean.engine.staged.CleanerBackend
import xyz.habnoir.clean.engine.staged.SkipReason
import xyz.habnoir.clean.engine.staged.StepResult
import xyz.habnoir.clean.model.AppCacheItem
import xyz.habnoir.clean.model.EngineTier

class AccessibilityCleanerBackend(
    private val context: Context,
    private val bridge: AccessibilitySessionBridge,
    private val onUserTouchDetected: () -> Unit
) : CleanerBackend {

    override val tier: EngineTier = EngineTier.ACCESSIBILITY_STAGED

    override suspend fun isReady(): Boolean {
        return bridge.isServiceConnected() || AccessibilityCleanerService.isServiceEnabledInSystem(context)
    }

    override suspend fun clearAppCache(target: AppCacheItem, spec: OemCleanSpec): StepResult {
        if (!bridge.isServiceConnected()) {
            return StepResult.Failed(retryable = false, reason = "Accessibility service disconnected")
        }

        val launched = openAppDetails(target.packageName.raw)
        if (!launched) {
            return StepResult.Skipped(SkipReason.BUTTON_NOT_FOUND)
        }

        bridge.setSessionActive(true)
        return try {
            driveSettingsSequence(target, spec)
        } finally {
            bridge.setSessionActive(false)
            bridge.navigateBack()
        }
    }

    private suspend fun driveSettingsSequence(
        target: AppCacheItem,
        spec: OemCleanSpec
    ): StepResult {
        var submenuOpens = 0
        var xiaomiSheetOpens = 0
        var foreignWindowCount = 0
        var sawSettingsWindow = false
        val startedAtMs = SystemClock.elapsedRealtime()

        val result = withTimeoutOrNull(STEP_TIMEOUT_MS) {
            while (true) {
                val snapshot = bridge.snapshotFlow().first()
                when (val decision = SafetyNodeMatcher.evaluateWindow(
                    packageName = snapshot.packageName,
                    nodes = snapshot.nodes,
                    spec = spec,
                    allowXiaomiBottomSheetTrigger = xiaomiSheetOpens == 0
                )) {
                    is MatchDecision.ClickNode -> {
                        sawSettingsWindow = true
                        foreignWindowCount = 0
                        when (decision.step) {
                            ActionStep.CLEAR_CACHE,
                            ActionStep.CONFIRM_BOTTOM_SHEET_CACHE -> {
                                val clicked = bridge.clickNode(decision.nodeId)
                                if (clicked) {
                                    delay(POST_CLICK_SETTLE_MS)
                                    return@withTimeoutOrNull StepResult.Cleared
                                }
                            }
                            ActionStep.OPEN_STORAGE_SUBMENU -> {
                                if (submenuOpens < 2 && bridge.clickNode(decision.nodeId)) {
                                    submenuOpens++
                                    delay(SUBMENU_TRANSITION_MS)
                                }
                            }
                            ActionStep.OPEN_XIAOMI_BOTTOM_SHEET -> {
                                if (xiaomiSheetOpens == 0 && bridge.clickNode(decision.nodeId)) {
                                    xiaomiSheetOpens++
                                    delay(SUBMENU_TRANSITION_MS)
                                }
                            }
                        }
                    }
                    MatchDecision.AlreadyClean -> {
                        sawSettingsWindow = true
                        return@withTimeoutOrNull StepResult.AlreadyClean
                    }
                    is MatchDecision.ForeignWindow -> {
                        if (decision.packageName == target.packageName.raw) {
                            // App opened its own custom storage/ManageSpace screen instead of Settings
                            return@withTimeoutOrNull StepResult.Skipped(SkipReason.BUTTON_NOT_FOUND)
                        }
                        if (!isTransientSystemPackage(decision.packageName) &&
                            sawSettingsWindow &&
                            SystemClock.elapsedRealtime() - startedAtMs >= TOUCH_GUARD_GRACE_MS
                        ) {
                            foreignWindowCount++
                            if (foreignWindowCount >= 3) {
                                onUserTouchDetected()
                                return@withTimeoutOrNull StepResult.Failed(
                                    retryable = true,
                                    reason = "User moved away from settings"
                                )
                            }
                        }
                    }
                    MatchDecision.NotReadyYet -> {
                        sawSettingsWindow = true
                    }
                }
            }
            @Suppress("UNREACHABLE_CODE")
            StepResult.Skipped(SkipReason.TIMEOUT)
        }

        return result ?: StepResult.Failed(retryable = true, reason = "Step timeout")
    }

    private fun isTransientSystemPackage(pkg: String): Boolean {
        if (pkg == context.packageName) return true
        if (pkg in TRANSIENT_SYSTEM_PACKAGES) return true
        return TRANSIENT_PREFIXES.any { pkg.startsWith(it) }
    }

    private fun openAppDetails(packageName: String): Boolean {
        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = Uri.fromParts("package", packageName, null)
            addFlags(
                Intent.FLAG_ACTIVITY_NEW_TASK or
                    Intent.FLAG_ACTIVITY_NO_HISTORY or
                    Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS
            )
        }
        return runCatching {
            context.startActivity(intent)
            true
        }.getOrDefault(false)
    }

    companion object {
        private const val STEP_TIMEOUT_MS = 4_500L
        private const val POST_CLICK_SETTLE_MS = 280L
        private const val SUBMENU_TRANSITION_MS = 320L
        private const val TOUCH_GUARD_GRACE_MS = 700L

        private val TRANSIENT_SYSTEM_PACKAGES = setOf(
            "com.android.systemui",
            "com.android.permissioncontroller",
            "com.google.android.permissioncontroller"
        )
        private val TRANSIENT_PREFIXES = listOf(
            "com.transsion.smartpanel",
            "com.transsion.keyguardgesture",
            "com.android.inputmethod",
            "com.google.android.inputmethod"
        )
    }
}
