package xyz.habnoir.clean.engine.scanner

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.util.LruCache
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.graphics.createBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class AppIconMemoryCache(private val context: Context) {

    private val cache = object : LruCache<String, CachedBitmap>(MAX_BYTES) {
        override fun sizeOf(key: String, value: CachedBitmap): Int = value.byteSize
    }

    private data class CachedBitmap(val image: ImageBitmap, val byteSize: Int)

    fun getCached(packageName: String): ImageBitmap? = cache.get(packageName)?.image

    suspend fun prewarm(packageNames: List<String>, sizePx: Int) {
        for (pkg in packageNames) {
            if (cache.get(pkg) == null) {
                getOrLoadIcon(pkg, sizePx)
            }
        }
    }

    suspend fun getOrLoadIcon(packageName: String, sizePx: Int): ImageBitmap? {
        cache.get(packageName)?.let { return it.image }

        return withContext(Dispatchers.Default) {
            cache.get(packageName)?.let { return@withContext it.image }

            val drawable = runCatching {
                context.packageManager.getApplicationIcon(packageName)
            }.getOrNull() ?: return@withContext null

            val safePx = sizePx.coerceIn(32, 192)
            val bitmap: Bitmap = createBitmap(safePx, safePx, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bitmap)
            drawable.setBounds(0, 0, safePx, safePx)
            drawable.draw(canvas)
            bitmap.prepareToDraw()

            val imageBitmap = bitmap.asImageBitmap()
            cache.put(packageName, CachedBitmap(imageBitmap, bitmap.allocationByteCount))
            imageBitmap
        }
    }

    fun clear() {
        cache.evictAll()
    }

    companion object {
        private const val MAX_BYTES = 4 * 1024 * 1024 // 4 MB cap
    }
}
