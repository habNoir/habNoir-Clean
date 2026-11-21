package xyz.habnoir.clean.engine.accessibility

import xyz.habnoir.clean.engine.oem.OemCleanSpec

data class NodeSnapshot(
    val id: Int,
    val parentId: Int?,
    val text: String?,
    val contentDesc: String?,
    val viewId: String?,
    val isClickable: Boolean,
    val isEnabled: Boolean
)

enum class ActionStep {
    CLEAR_CACHE,
    OPEN_STORAGE_SUBMENU,
    OPEN_XIAOMI_BOTTOM_SHEET,
    CONFIRM_BOTTOM_SHEET_CACHE
}

sealed interface MatchDecision {
    data class ClickNode(val nodeId: Int, val step: ActionStep) : MatchDecision
    data object AlreadyClean : MatchDecision
    data object NotReadyYet : MatchDecision
    data class ForeignWindow(val packageName: String) : MatchDecision
}

object SafetyNodeMatcher {

    private val HARD_VETO_SUBSTRINGS = listOf(
        "hapus semua data",
        "hapus semua",
        "hapus penyimpanan",
        "bersihkan semua data",
        "clear all data",
        "clear storage",
        "clear user data",
        "manage space",
        "kelola ruang",
        "uninstall",
        "copot",
        "hapus instalan",
        "force stop",
        "paksa berhenti",
        "nonaktifkan",
        "hapus akun"
    )

    private val GENERIC_DATA_VETO = listOf("hapus data", "clear data")

    private val XIAOMI_DESTRUCTIVE_SHEET_OPTIONS = listOf(
        "hapus semua data",
        "clear all data",
        "bersihkan semua data"
    )

    fun evaluateWindow(
        packageName: String,
        nodes: List<NodeSnapshot>,
        spec: OemCleanSpec,
        allowXiaomiBottomSheetTrigger: Boolean = true
    ): MatchDecision {
        if (packageName !in spec.settingsPackageIds) {
            return MatchDecision.ForeignWindow(packageName)
        }
        if (nodes.isEmpty()) return MatchDecision.NotReadyYet

        val byId = nodes.associateBy { it.id }
        val childrenByParent = nodes.groupBy { it.parentId }

        // 1. Xiaomi SecurityCenter two-option bottom sheet already open (check explicitly first)
        if (spec.supportsXiaomiBottomSheet && packageName == "com.miui.securitycenter") {
            val hasDestructiveOption = nodes.any { node ->
                val combined = normalizedTexts(node)
                combined.any { text -> XIAOMI_DESTRUCTIVE_SHEET_OPTIONS.any { text == it } }
            }
            if (hasDestructiveOption) {
                val safeSheetOption = findSafeNode(
                    nodes = nodes,
                    byId = byId,
                    childrenByParent = childrenByParent,
                    positiveLabels = spec.bottomSheetOptionExactLabels,
                    hintViewIds = emptyList(),
                    exactOnly = true
                )
                if (safeSheetOption != null) {
                    return if (safeSheetOption.clickableNode.isEnabled) {
                        MatchDecision.ClickNode(
                            safeSheetOption.clickableNode.id,
                            ActionStep.CONFIRM_BOTTOM_SHEET_CACHE
                        )
                    } else {
                        MatchDecision.AlreadyClean
                    }
                }
            }
        }

        // 2. Direct Clear Cache button
        val directCache = findSafeNode(
            nodes = nodes,
            byId = byId,
            childrenByParent = childrenByParent,
            positiveLabels = spec.clearCacheButtonLabels,
            hintViewIds = spec.clearCacheViewIds,
            exactOnly = false
        )
        if (directCache != null) {
            return if (directCache.clickableNode.isEnabled) {
                MatchDecision.ClickNode(directCache.clickableNode.id, ActionStep.CLEAR_CACHE)
            } else {
                MatchDecision.AlreadyClean
            }
        }

        // 3. Storage submenu entry (OPPO/Vivo/Samsung/AOSP/HyperOS Settings)
        val storageSubmenu = findSafeNode(
            nodes = nodes,
            byId = byId,
            childrenByParent = childrenByParent,
            positiveLabels = spec.storageEntryLabels,
            hintViewIds = spec.storageEntryViewIds,
            exactOnly = false
        )
        if (storageSubmenu != null && storageSubmenu.clickableNode.isEnabled) {
            return MatchDecision.ClickNode(storageSubmenu.clickableNode.id, ActionStep.OPEN_STORAGE_SUBMENU)
        }

        // 4. Verified Xiaomi SecurityCenter bottom-sheet opener ("Bersihkan data" -> 2-option modal)
        if (allowXiaomiBottomSheetTrigger &&
            spec.supportsXiaomiBottomSheet &&
            packageName == "com.miui.securitycenter"
        ) {
            val trigger = findXiaomiBottomSheetTrigger(nodes, byId, childrenByParent, spec)
            if (trigger != null) {
                return if (trigger.isEnabled) {
                    MatchDecision.ClickNode(trigger.id, ActionStep.OPEN_XIAOMI_BOTTOM_SHEET)
                } else {
                    MatchDecision.AlreadyClean
                }
            }
        }

        return MatchDecision.NotReadyYet
    }

    internal fun isHardVetoText(raw: String?, allowGenericClearDataTrigger: Boolean = false): Boolean {
        if (raw.isNullOrBlank()) return false
        val normalized = raw.trim().lowercase()
        if (HARD_VETO_SUBSTRINGS.any { normalized.contains(it) }) return true
        if (!allowGenericClearDataTrigger && GENERIC_DATA_VETO.any { normalized.contains(it) }) {
            return true
        }
        return false
    }

    private data class SafeCandidate(val clickableNode: NodeSnapshot)

    private fun findSafeNode(
        nodes: List<NodeSnapshot>,
        byId: Map<Int, NodeSnapshot>,
        childrenByParent: Map<Int?, List<NodeSnapshot>>,
        positiveLabels: List<String>,
        hintViewIds: List<String>,
        exactOnly: Boolean
    ): SafeCandidate? {
        for (node in nodes) {
            val texts = normalizedTexts(node)
            val viewIdMatches = !node.viewId.isNullOrBlank() && node.viewId in hintViewIds
            val labelMatches = texts.any { text ->
                if (isHardVetoText(text)) return@any false
                positiveLabels.any { target ->
                    if (exactOnly) text == target else (text == target || text.contains(target))
                }
            }
            if (!labelMatches && !viewIdMatches) continue

            val clickable = resolveSafeClickableAncestor(
                start = node,
                byId = byId,
                childrenByParent = childrenByParent,
                allowGenericClearDataTrigger = false
            ) ?: continue

            return SafeCandidate(clickable)
        }
        return null
    }

    private fun findXiaomiBottomSheetTrigger(
        nodes: List<NodeSnapshot>,
        byId: Map<Int, NodeSnapshot>,
        childrenByParent: Map<Int?, List<NodeSnapshot>>,
        spec: OemCleanSpec
    ): NodeSnapshot? {
        for (node in nodes) {
            val texts = normalizedTexts(node)
            val matches = texts.any { text ->
                !HARD_VETO_SUBSTRINGS.any { text.contains(it) } &&
                    spec.bottomSheetTriggerLabels.any { text == it }
            }
            if (!matches) continue
            val clickable = resolveSafeClickableAncestor(
                start = node,
                byId = byId,
                childrenByParent = childrenByParent,
                allowGenericClearDataTrigger = true
            )
            if (clickable != null) return clickable
        }
        return null
    }

    private fun resolveSafeClickableAncestor(
        start: NodeSnapshot,
        byId: Map<Int, NodeSnapshot>,
        childrenByParent: Map<Int?, List<NodeSnapshot>>,
        allowGenericClearDataTrigger: Boolean
    ): NodeSnapshot? {
        var current: NodeSnapshot? = start
        var hops = 0
        while (current != null && hops <= 3) {
            if (current.isClickable) {
                if (subtreeContainsForbiddenLabel(current, childrenByParent, allowGenericClearDataTrigger)) {
                    return null
                }
                return current
            }
            current = current.parentId?.let(byId::get)
            hops++
        }
        return null
    }

    private fun subtreeContainsForbiddenLabel(
        root: NodeSnapshot,
        childrenByParent: Map<Int?, List<NodeSnapshot>>,
        allowGenericClearDataTrigger: Boolean
    ): Boolean {
        val stack = ArrayDeque<NodeSnapshot>()
        stack.addLast(root)
        var visited = 0
        while (stack.isNotEmpty() && visited < 40) {
            val item = stack.removeLast()
            visited++
            if (isHardVetoText(item.text, allowGenericClearDataTrigger) ||
                isHardVetoText(item.contentDesc, allowGenericClearDataTrigger)
            ) {
                return true
            }
            childrenByParent[item.id]?.forEach(stack::addLast)
        }
        return false
    }

    private fun normalizedTexts(node: NodeSnapshot): List<String> {
        return buildList(2) {
            node.text?.trim()?.lowercase()?.takeIf { it.isNotEmpty() }?.let(::add)
            node.contentDesc?.trim()?.lowercase()?.takeIf { it.isNotEmpty() }?.let(::add)
        }
    }
}
