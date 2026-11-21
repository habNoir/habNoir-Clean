package xyz.habnoir.clean.engine.trash

import android.app.PendingIntent
import xyz.habnoir.clean.model.OemGalleryShortcut
import xyz.habnoir.clean.model.TrashLayer1Item
import xyz.habnoir.clean.model.TrashLayer1Summary
import java.io.File

data class TrashOverviewSnapshot(
    val layer1: TrashLayer1Summary,
    val layer2HasAccess: Boolean,
    val layer2Folders: List<xyz.habnoir.clean.model.TrashLayer2Folder>,
    val layer2Files: List<File>,
    val layer2TotalBytes: Long,
    val layer3Galleries: List<OemGalleryShortcut>
)

class TrashCoordinator(
    private val mediaStoreSource: MediaStoreTrashSource,
    private val folderTrashSource: AllowlistFolderTrashSource,
    private val galleryShortcuts: OemGalleryTrashShortcuts
) {
    suspend fun scanAll(): TrashOverviewSnapshot {
        val layer1 = mediaStoreSource.scanTrashedMedia()
        val layer2 = folderTrashSource.scanHiddenTrash()
        val galleries = galleryShortcuts.availableGalleryApps()
        return TrashOverviewSnapshot(
            layer1 = layer1,
            layer2HasAccess = layer2.hasAccess,
            layer2Folders = layer2.folders,
            layer2Files = layer2.targetFiles,
            layer2TotalBytes = layer2.totalBytes,
            layer3Galleries = galleries
        )
    }

    fun nextLayer1DeleteIntent(items: List<TrashLayer1Item>): PendingIntent? {
        return mediaStoreSource.nextDeleteBatchPendingIntent(items)
    }

    suspend fun deleteLayer2Files(files: List<File>): Pair<Int, Long> {
        return folderTrashSource.deleteBatchPaced(files)
    }

    fun openGalleryShortcut(packageName: String): Boolean {
        return galleryShortcuts.launchGallery(packageName)
    }
}
