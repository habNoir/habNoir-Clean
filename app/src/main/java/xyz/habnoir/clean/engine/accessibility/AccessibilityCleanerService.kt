package xyz.habnoir.clean.engine.accessibility

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.content.ComponentName
import android.content.Context
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityManager
import android.view.accessibility.AccessibilityNodeInfo
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import xyz.habnoir.clean.HabNoirApp
import xyz.habnoir.clean.model.CleanSessionState
import java.util.concurrent.ConcurrentHashMap

class AccessibilityCleanerService : AccessibilityService(), AccessibilityDriver {

    private var bridge: AccessibilitySessionBridge? = null
    private var serviceScope: CoroutineScope? = null
    private var overlayController: CleanFloatingOverlayController? = null
    private val latestNodeRefs = ConcurrentHashMap<Int, AccessibilityNodeInfo>()

    override fun onServiceConnected() {
        super.onServiceConnected()
        Log.i("HabNoirOverlay", "AccessibilityCleanerService connected")
        val app = application as? HabNoirApp ?: return
        val sessionBridge = app.container.accessibilityBridge
        sessionBridge.attachDriver(this)
        bridge = sessionBridge

        serviceScope?.cancel()
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
        serviceScope = scope

        overlayController?.dismiss()
        val overlay = CleanFloatingOverlayController(
            service = this,
            onTogglePause = {
                when (val current = app.container.orchestrator.sessionState.value) {
                    is CleanSessionState.Cleaning -> if (current.isPaused) {
                        app.container.orchestrator.resume()
                    } else {
                        app.container.orchestrator.pause()
                    }
                    is CleanSessionState.Cooldown -> if (current.isPaused) {
                        app.container.orchestrator.resume()
                    } else {
                        app.container.orchestrator.pause()
                    }
                    else -> Unit
                }
            },
            onStop = { app.container.orchestrator.stop() }
        )
        overlayController = overlay

        scope.launch {
            app.container.orchestrator.sessionState.collect { state ->
                val showOverlay = when (state) {
                    is CleanSessionState.Cleaning -> !state.isGuidedMode
                    is CleanSessionState.Cooldown -> !state.isGuidedMode
                    else -> false
                }
                if (showOverlay) {
                    overlay.render(state)
                } else {
                    overlay.dismiss()
                }
            }
        }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        val activeBridge = bridge ?: return
        if (!activeBridge.isSessionActive()) return

        val root = rootInActiveWindow ?: return
        val windowPkg = event?.packageName?.toString()?.takeIf { it.isNotBlank() }
            ?: root.packageName?.toString()
            ?: return
        if (windowPkg == packageName) return

        latestNodeRefs.clear()
        val extracted = ArrayList<NodeSnapshot>(64)
        collectNodes(
            node = root,
            parentId = null,
            depth = 0,
            out = extracted
        )
        if (extracted.isNotEmpty()) {
            activeBridge.publishSnapshot(WindowSnapshot(packageName = windowPkg, nodes = extracted))
        }
    }

    override fun onInterrupt() {
        latestNodeRefs.clear()
    }

    override fun onUnbind(intent: android.content.Intent?): Boolean {
        overlayController?.dismiss()
        overlayController = null
        serviceScope?.cancel()
        serviceScope = null
        latestNodeRefs.clear()
        bridge?.detachDriver()
        bridge = null
        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        overlayController?.dismiss()
        overlayController = null
        serviceScope?.cancel()
        serviceScope = null
        super.onDestroy()
    }

    override fun clickSnapshotNode(nodeId: Int): Boolean {
        val node = latestNodeRefs[nodeId] ?: return false
        return runCatching {
            node.performAction(AccessibilityNodeInfo.ACTION_CLICK)
        }.getOrDefault(false)
    }

    override fun performBack() {
        runCatching { performGlobalAction(GLOBAL_ACTION_BACK) }
    }

    private fun collectNodes(
        node: AccessibilityNodeInfo,
        parentId: Int?,
        depth: Int,
        out: MutableList<NodeSnapshot>
    ) {
        if (depth > MAX_TREE_DEPTH || out.size >= MAX_SNAPSHOT_NODES) return

        val id = out.size + 1
        latestNodeRefs[id] = node
        out.add(
            NodeSnapshot(
                id = id,
                parentId = parentId,
                text = node.text?.toString(),
                contentDesc = node.contentDescription?.toString(),
                viewId = node.viewIdResourceName,
                isClickable = node.isClickable,
                isEnabled = node.isEnabled
            )
        )

        val childCount = node.childCount
        for (i in 0 until childCount) {
            if (out.size >= MAX_SNAPSHOT_NODES) break
            val child = node.getChild(i) ?: continue
            collectNodes(child, id, depth + 1, out)
        }
    }

    companion object {
        private const val MAX_TREE_DEPTH = 12
        private const val MAX_SNAPSHOT_NODES = 220

        fun isServiceEnabledInSystem(context: Context): Boolean {
            val am = context.getSystemService(AccessibilityManager::class.java) ?: return false
            val expected = ComponentName(context, AccessibilityCleanerService::class.java)
            val enabled = runCatching {
                am.getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_ALL_MASK)
            }.getOrDefault(emptyList())

            return enabled.any { info ->
                val svcInfo = info.resolveInfo?.serviceInfo ?: return@any false
                ComponentName(svcInfo.packageName, svcInfo.name) == expected
            }
        }
    }
}
