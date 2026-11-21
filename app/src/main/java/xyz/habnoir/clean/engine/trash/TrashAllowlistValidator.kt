package xyz.habnoir.clean.engine.trash

import java.io.File
import java.nio.file.Files

object TrashAllowlistValidator {

    private val EXPLICIT_RELATIVE_TRASH_DIRS = setOf(
        "miui/gallery/cloud/.trashbin",
        "dcim/.trash",
        "pictures/.trash",
        "movies/.trash",
        "download/.trash",
        ".coloros/.recycle",
        ".vivo_recycle",
        ".hios_trash",
        ".xos_trash"
    )

    private val EXACT_TRASH_DIR_NAMES = setOf(
        ".trash",
        ".trashbin",
        ".recycle",
        ".recyclebin",
        "recyclebin",
        ".globaltrash"
    )

    private val TRASH_PREFIX_DIR_REGEX = Regex("^\\.trash-.+$", RegexOption.IGNORE_CASE)
    private val ORPHAN_TRASHED_FILE_REGEX = Regex("^\\.trashed-\\d+-.*$")

    fun isSafeRootChild(rootCanonical: String, candidate: File): Boolean {
        if (Files.isSymbolicLink(candidate.toPath())) return false
        val canonical = runCatching { candidate.canonicalPath }.getOrNull() ?: return false
        val prefix = if (rootCanonical.endsWith(File.separator)) rootCanonical else "$rootCanonical${File.separator}"
        return canonical.startsWith(prefix)
    }

    fun isAllowedTrashDirectory(rootCanonical: String, dir: File): Boolean {
        if (!dir.isDirectory || !isSafeRootChild(rootCanonical, dir)) return false

        val normalizedRoot = rootCanonical.trimEnd(File.separatorChar)
        val rel = dir.canonicalPath
            .removePrefix(normalizedRoot)
            .trimStart(File.separatorChar)
            .replace(File.separatorChar, '/')
            .lowercase()

        if (rel.isEmpty()) return false
        if (rel in EXPLICIT_RELATIVE_TRASH_DIRS) return true

        val segments = rel.split('/')
        if (segments.size > 4) return false

        // Never treat top-level Android/data or WhatsApp active folders as trash containers
        if (segments.firstOrNull() == "android") return false

        return segments.any { seg ->
            seg in EXACT_TRASH_DIR_NAMES || TRASH_PREFIX_DIR_REGEX.matches(seg)
        }
    }

    fun isAllowedOrphanTrashedFile(
        rootCanonical: String,
        file: File,
        nowMs: Long = System.currentTimeMillis()
    ): Boolean {
        val name = file.name
        if (!name.startsWith(".trashed-") || !ORPHAN_TRASHED_FILE_REGEX.matches(name)) return false
        if (!file.isFile || !isSafeRootChild(rootCanonical, file)) return false
        // Grace period ensures an active MediaStore write is never interrupted
        return (nowMs - file.lastModified()) > ORPHAN_GRACE_PERIOD_MS
    }

    const val ORPHAN_GRACE_PERIOD_MS = 60_000L
}
