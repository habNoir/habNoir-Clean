package xyz.habnoir.clean.engine.accessibility

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import xyz.habnoir.clean.engine.oem.OemFamily
import xyz.habnoir.clean.engine.oem.OemSpecRegistry

class SafetyNodeMatcherTest {

    @Test
    fun `never clicks clear data uninstall or force stop variants across all OEMs`() {
        val dangerousLabels = listOf(
            "Hapus data",
            "Hapus semua data",
            "Hapus penyimpanan",
            "Bersihkan semua data",
            "Clear data",
            "Clear all data",
            "Clear storage",
            "Clear user data",
            "Manage space",
            "Kelola ruang",
            "Uninstall",
            "Copot pemasangan",
            "Hapus instalan",
            "Force stop",
            "Paksa berhenti",
            "Nonaktifkan",
            "Hapus akun"
        )

        for (family in OemFamily.entries) {
            val spec = OemSpecRegistry.specFor(family)
            val pkg = spec.settingsPackageIds.first()
            for (label in dangerousLabels) {
                val nodes = listOf(
                    NodeSnapshot(
                        id = 1,
                        parentId = null,
                        text = label,
                        contentDesc = label,
                        viewId = "com.android.settings:id/button1",
                        isClickable = true,
                        isEnabled = true
                    )
                )
                val decision = SafetyNodeMatcher.evaluateWindow(
                    packageName = pkg,
                    nodes = nodes,
                    spec = spec,
                    allowXiaomiBottomSheetTrigger = false
                )
                assertTrue(
                    "Expected NotReadyYet for dangerous label '$label' on $family but got $decision",
                    decision is MatchDecision.NotReadyYet
                )
            }
        }
    }

    @Test
    fun `matches Samsung Indonesian unique Hapus memori button`() {
        val spec = OemSpecRegistry.specFor(OemFamily.SAMSUNG_ONEUI)
        val nodes = listOf(
            NodeSnapshot(1, null, "Hapus data", null, null, isClickable = true, isEnabled = true),
            NodeSnapshot(2, null, "Hapus memori", null, null, isClickable = true, isEnabled = true)
        )
        val decision = SafetyNodeMatcher.evaluateWindow("com.android.settings", nodes, spec)
        assertEquals(MatchDecision.ClickNode(2, ActionStep.CLEAR_CACHE), decision)
    }

    @Test
    fun `matches disabled clear cache button as AlreadyClean`() {
        val spec = OemSpecRegistry.specFor(OemFamily.VIVO_IQOO_FUNTOUCH)
        val nodes = listOf(
            NodeSnapshot(1, null, "Hapus penyimpanan", null, null, isClickable = true, isEnabled = true),
            NodeSnapshot(2, null, "Hapus cache", null, null, isClickable = true, isEnabled = false)
        )
        val decision = SafetyNodeMatcher.evaluateWindow("com.android.settings", nodes, spec)
        assertEquals(MatchDecision.AlreadyClean, decision)
    }

    @Test
    fun `walks up to clickable ancestor and rejects container wrapping clear data`() {
        val spec = OemSpecRegistry.specFor(OemFamily.OPPO_REALME_COLOROS)
        // Unsafe container wraps both labels under one clickable parent -> must reject!
        val unsafeNodes = listOf(
            NodeSnapshot(1, null, null, null, null, isClickable = true, isEnabled = true),
            NodeSnapshot(2, 1, "Hapus cache", null, null, isClickable = false, isEnabled = true),
            NodeSnapshot(3, 1, "Hapus semua data", null, null, isClickable = false, isEnabled = true)
        )
        val rejected = SafetyNodeMatcher.evaluateWindow("com.android.settings", unsafeNodes, spec)
        assertEquals(MatchDecision.NotReadyYet, rejected)

        // Safe separate container wraps only Hapus cache
        val safeNodes = listOf(
            NodeSnapshot(10, null, null, null, null, isClickable = true, isEnabled = true),
            NodeSnapshot(11, 10, "Hapus cache", null, null, isClickable = false, isEnabled = true)
        )
        val accepted = SafetyNodeMatcher.evaluateWindow("com.android.settings", safeNodes, spec)
        assertEquals(MatchDecision.ClickNode(10, ActionStep.CLEAR_CACHE), accepted)
    }

    @Test
    fun `xiaomi bottom sheet selects only Bersihkan cache when both options appear`() {
        val spec = OemSpecRegistry.specFor(OemFamily.XIAOMI_HYPEROS_MIUI)
        val nodes = listOf(
            NodeSnapshot(1, null, "Hapus semua data", null, null, isClickable = true, isEnabled = true),
            NodeSnapshot(2, null, "Bersihkan cache", null, null, isClickable = true, isEnabled = true)
        )
        val decision = SafetyNodeMatcher.evaluateWindow("com.miui.securitycenter", nodes, spec)
        assertEquals(MatchDecision.ClickNode(2, ActionStep.CONFIRM_BOTTOM_SHEET_CACHE), decision)
    }

    @Test
    fun `xiaomi prefers direct storage submenu before bottom sheet trigger`() {
        val spec = OemSpecRegistry.specFor(OemFamily.XIAOMI_HYPEROS_MIUI)
        val nodes = listOf(
            NodeSnapshot(1, null, "Penyimpanan", null, null, isClickable = true, isEnabled = true),
            NodeSnapshot(2, null, "Bersihkan data", null, null, isClickable = true, isEnabled = true)
        )
        val decision = SafetyNodeMatcher.evaluateWindow("com.miui.securitycenter", nodes, spec)
        assertEquals(MatchDecision.ClickNode(1, ActionStep.OPEN_STORAGE_SUBMENU), decision)
    }

    @Test
    fun `foreign window triggers ForeignWindow decision`() {
        val spec = OemSpecRegistry.specFor(OemFamily.VIVO_IQOO_FUNTOUCH)
        val decision = SafetyNodeMatcher.evaluateWindow(
            packageName = "com.whatsapp",
            nodes = listOf(NodeSnapshot(1, null, "Hapus cache", null, null, true, true)),
            spec = spec
        )
        assertEquals(MatchDecision.ForeignWindow("com.whatsapp"), decision)
    }
}
