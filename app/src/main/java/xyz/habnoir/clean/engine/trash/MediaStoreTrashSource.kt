package xyz.habnoir.clean.engine.trash

import android.app.PendingIntent
import android.content.ContentUris
import android.content.Context
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import androidx.core.net.toUri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import xyz.habnoir.clean.model.TrashLayer1Item
import xyz.habnoir.clean.model.TrashLayer1Summary

class MediaStoreTrashSource(private val context: Context) {

    suspend fun scanTrashedMedia(): TrashLayer1Summary = withContext(Dispatchers.IO) {
        val collections = listOf(
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
            MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
            MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
        )

        val queryArgs = Bundle().apply {
            putInt(MediaStore.QUERY_ARG_MATCH_TRASHED, MediaStore.MATCH_ONLY)
        }
        val projection = arrayOf(
            MediaStore.MediaColumns._ID,
            MediaStore.MediaColumns.SIZE,
            MediaStore.MediaColumns.IS_TRASHED
        )

        val found = ArrayList<TrashLayer1Item>(64)
        var totalBytes = 0L

        for (collectionUri in collections) {
            runCatching {
                context.contentResolver.query(collectionUri, projection, queryArgs, null)?.use { cursor ->
                    val idCol = cursor.getColumnIndex(MediaStore.MediaColumns._ID)
                    val sizeCol = cursor.getColumnIndex(MediaStore.MediaColumns.SIZE)
                    val trashedCol = cursor.getColumnIndex(MediaStore.MediaColumns.IS_TRASHED)
                    if (idCol < 0) return@use

                    while (cursor.moveToNext()) {
                        val isTrashed = if (trashedCol >= 0) cursor.getInt(trashedCol) == 1 else true
                        if (!isTrashed) continue
                        val id = cursor.getLong(idCol)
                        val size = if (sizeCol >= 0) maxOf(0L, cursor.getLong(sizeCol)) else 0L
                        val itemUri = ContentUris.withAppendedId(collectionUri, id)
                        found.add(TrashLayer1Item(uriString = itemUri.toString(), sizeBytes = size))
                        totalBytes += size
                    }
                }
            }
        }

        TrashLayer1Summary(
            fileCount = found.size,
            totalBytes = totalBytes,
            items = found
        )
    }

    fun nextDeleteBatchPendingIntent(items: List<TrashLayer1Item>): PendingIntent? {
        if (items.isEmpty()) return null
        val chunk: List<Uri> = items.take(URI_CHUNK_LIMIT).map { it.uriString.toUri() }
        return runCatching {
            MediaStore.createDeleteRequest(context.contentResolver, chunk)
        }.getOrNull()
    }

    companion object {
        const val URI_CHUNK_LIMIT = 150
    }
}
