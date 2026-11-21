package xyz.habnoir.clean.engine.accessibility

import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.receiveAsFlow
import java.util.concurrent.atomic.AtomicBoolean

data class WindowSnapshot(
    val packageName: String,
    val nodes: List<NodeSnapshot>
)

interface AccessibilityDriver {
    fun clickSnapshotNode(nodeId: Int): Boolean
    fun performBack()
}

class AccessibilitySessionBridge {
    private val sessionActive = AtomicBoolean(false)
    private val snapshots = Channel<WindowSnapshot>(Channel.CONFLATED)

    @Volatile
    private var driver: AccessibilityDriver? = null

    fun attachDriver(driver: AccessibilityDriver) {
        this.driver = driver
    }

    fun detachDriver() {
        this.driver = null
        sessionActive.set(false)
    }

    fun isServiceConnected(): Boolean = driver != null

    fun isSessionActive(): Boolean = sessionActive.get()

    fun setSessionActive(active: Boolean) {
        sessionActive.set(active)
        if (!active) {
            while (snapshots.tryReceive().isSuccess) {
                // Drain stale window events
            }
        }
    }

    fun publishSnapshot(snapshot: WindowSnapshot) {
        if (!sessionActive.get()) return
        snapshots.trySend(snapshot)
    }

    fun snapshotFlow(): Flow<WindowSnapshot> = snapshots.receiveAsFlow()

    fun clickNode(nodeId: Int): Boolean = driver?.clickSnapshotNode(nodeId) == true

    fun navigateBack() {
        driver?.performBack()
    }
}
