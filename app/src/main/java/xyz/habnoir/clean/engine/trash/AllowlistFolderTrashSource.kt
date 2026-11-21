package xyz.habnoir.clean.engine.trash

import android.os.Environment
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.coroutines.yield
import xyz.habnoir.clean.model.TrashLayer2Folder
import java.io.File

data class Layer2ScanResult(
    val hasAccess: Boolean,
    val folders: List<TrashLayer2Folder>,
    val targetFiles: List<File>,
    val totalBytes: Long
)

class AllowlistFolderTrashSource {

    fun hasAllFilesAccess(): Boolean = Environment.isExternalStorageManager()

    suspend fun scanHiddenTrash(): Layer2ScanResult = withContext(Dispatchers.IO) {
        if (!hasAllFilesAccess()) {
            return@withContext Layer2ScanResult(
                hasAccess = false,
                folders = emptyList(),
                targetFiles = emptyList(),
                totalBytes = 0L
            )
        }

        val root = Environment.getExternalStorageDirectory()
        val rootCanonical = runCatching { root.canonicalPath }.getOrNull()
            ?: return@withContext Layer2ScanResult(true, emptyList(), emptyList(), 0L)

        val matchedFiles = ArrayList<File>(64)
        val folders = ArrayList<TrashLayer2Folder>()
        val visitedTrashDirs = HashSet<String>()

        discoverKnownTrashDirs(rootCanonical, root, visitedTrashDirs, folders, matchedFiles)

        // Check immediate media roots + depth-1 child albums for orphan .trashed-* files
        val mediaDirs = listOf("DCIM", "Pictures", "Movies", "Download")
        for (dirName in mediaDirs) {
            val dir = File(root, dirName)
            if (!dir.isDirectory || !TrashAllowlistValidator.isSafeRootChild(rootCanonical, dir)) continue
            scanOrphansInDir(rootCanonical, dir, depth = 0, outFiles = matchedFiles)
        }

        val distinctFiles = matchedFiles.distinctBy { it.canonicalPath }
        val totalBytes = distinctFiles.sumOf { maxOf(0L, it.length()) }

        Layer2ScanResult(
            hasAccess = true,
            folders = folders,
            targetFiles = distinctFiles,
            totalBytes = totalBytes
        )
    }

    suspend fun deleteBatchPaced(files: List<File>): Pair<Int, Long> = withContext(Dispatchers.IO) {
        if (!hasAllFilesAccess() || files.isEmpty()) return@withContext 0 to 0L
        val rootCanonical = runCatching { Environment.getExternalStorageDirectory().canonicalPath }
            .getOrNull() ?: return@withContext 0 to 0L

        var deletedCount = 0
        var freedBytes = 0L
        files.chunked(DELETE_CHUNK_SIZE).forEach { chunk ->
            for (file in chunk) {
                val safeToDelete = TrashAllowlistValidator.isSafeRootChild(rootCanonical, file) &&
                    (TrashAllowlistValidator.isAllowedOrphanTrashedFile(rootCanonical, file) ||
                        file.parentFile?.let {
                            TrashAllowlistValidator.isAllowedTrashDirectory(rootCanonical, it)
                        } == true)

                if (safeToDelete && file.isFile) {
                    val size = maxOf(0L, file.length())
                    if (runCatching { file.delete() }.getOrDefault(false)) {
                        deletedCount++
                        freedBytes += size
                    }
                }
            }
            yield()
            delay(CHUNK_PACING_MS)
        }
        deletedCount to freedBytes
    }

    private fun discoverKnownTrashDirs(
        rootCanonical: String,
        root: File,
        visitedTrashDirs: MutableSet<String>,
        outFolders: MutableList<TrashLayer2Folder>,
        outFiles: MutableList<File>
    ) {
        val candidates = ArrayList<File>(KNOWN_TRASH_RELATIVE_PATHS.size + 16)
        for (rel in KNOWN_TRASH_RELATIVE_PATHS) {
            candidates.add(File(root, rel))
        }

        // Also inspect depth-0 hidden/recycle dirs and depth-1 hidden dirs inside top-level user folders
        val topDirs = root.listFiles { f -> f.isDirectory && f.name != "Android" }.orEmpty()
        for (top in topDirs) {
            if (top.name.startsWith(".") || top.name.equals("RecycleBin", ignoreCase = true)) {
                candidates.add(top)
            } else {
                val hiddenChildren = top.listFiles { child ->
                    child.isDirectory && child.name.startsWith(".")
                }.orEmpty()
                for (child in hiddenChildren) {
                    candidates.add(child)
                }
            }
        }

        for (candidate in candidates) {
            if (!candidate.isDirectory || !TrashAllowlistValidator.isAllowedTrashDirectory(rootCanonical, candidate)) {
                continue
            }
            val canonical = runCatching { candidate.canonicalPath }.getOrNull() ?: continue
            if (!visitedTrashDirs.add(canonical)) continue

            val filesInside = collectRegularFilesBounded(rootCanonical, candidate)
            if (filesInside.isNotEmpty()) {
                val rel = canonical.removePrefix(rootCanonical).trimStart(File.separatorChar)
                outFolders.add(
                    TrashLayer2Folder(
                        relativePath = rel,
                        fileCount = filesInside.size,
                        totalBytes = filesInside.sumOf { maxOf(0L, it.length()) }
                    )
                )
                outFiles.addAll(filesInside)
            }
        }
    }

    private fun scanOrphansInDir(
        rootCanonical: String,
        dir: File,
        depth: Int,
        outFiles: MutableList<File>
    ) {
        if (depth > 1) return
        val now = System.currentTimeMillis()
        val orphanCandidates = dir.listFiles { _, name -> name.startsWith(".trashed-") }.orEmpty()
        var inspectedOrphans = 0
        for (candidate in orphanCandidates) {
            if (inspectedOrphans >= MAX_ORPHAN_FILES_PER_DIR) break
            inspectedOrphans++
            if (TrashAllowlistValidator.isAllowedOrphanTrashedFile(rootCanonical, candidate, now)) {
                outFiles.add(candidate)
            }
        }

        if (depth < 1) {
            val subDirs = dir.listFiles { f -> f.isDirectory && !f.name.startsWith(".") }.orEmpty()
            var inspectedSubdirs = 0
            for (sub in subDirs) {
                if (inspectedSubdirs >= MAX_SUBDIRS_PER_MEDIA_DIR) break
                inspectedSubdirs++
                if (TrashAllowlistValidator.isSafeRootChild(rootCanonical, sub)) {
                    scanOrphansInDir(rootCanonical, sub, depth + 1, outFiles)
                }
            }
        }
    }

    private fun collectRegularFilesBounded(rootCanonical: String, trashRoot: File): List<File> {
        val result = ArrayList<File>()
        val queue = ArrayDeque<Pair<File, Int>>()
        queue.add(trashRoot to 0)
        while (queue.isNotEmpty() && result.size < MAX_FILES_PER_TRASH_DIR) {
            val (current, depth) = queue.removeFirst()
            if (depth > 3) continue
            val children = current.listFiles() ?: continue
            for (child in children) {
                if (result.size >= MAX_FILES_PER_TRASH_DIR) break
                if (!TrashAllowlistValidator.isSafeRootChild(rootCanonical, child)) continue
                if (child.isFile) result.add(child)
                else if (child.isDirectory) queue.add(child to depth + 1)
            }
        }
        return result
    }

    companion object {
        private val KNOWN_TRASH_RELATIVE_PATHS = listOf(
            "MIUI/Gallery/cloud/.trashBin",
            "DCIM/.Trash",
            "Pictures/.Trash",
            "Movies/.Trash",
            "Download/.Trash",
            ".ColorOS/.recycle",
            ".vivo_recycle",
            ".HiOS_Trash",
            ".XOS_Trash",
            ".Trash",
            ".TrashBin",
            ".recycle",
            ".recyclebin",
            "RecycleBin",
            ".globalTrash"
        )
        private const val MAX_SUBDIRS_PER_MEDIA_DIR = 40
        private const val MAX_ORPHAN_FILES_PER_DIR = 500
        private const val MAX_FILES_PER_TRASH_DIR = 1_000
        private const val DELETE_CHUNK_SIZE = 50
        private const val CHUNK_PACING_MS = 50L
    }
}
