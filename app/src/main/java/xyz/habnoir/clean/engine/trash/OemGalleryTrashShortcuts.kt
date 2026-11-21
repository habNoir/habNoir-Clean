package xyz.habnoir.clean.engine.trash

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import xyz.habnoir.clean.model.OemGalleryShortcut

class OemGalleryTrashShortcuts(private val context: Context) {

    fun availableGalleryApps(): List<OemGalleryShortcut> {
        val pm = context.packageManager
        return GALLERY_CANDIDATES.mapNotNull { (pkg, fallbackLabel) ->
            val launchIntent = pm.getLaunchIntentForPackage(pkg) ?: return@mapNotNull null
            val resolved = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                pm.resolveActivity(launchIntent, PackageManager.ResolveInfoFlags.of(0L))
            } else {
                @Suppress("DEPRECATION")
                pm.resolveActivity(launchIntent, 0)
            }
            if (resolved?.activityInfo?.exported != true) return@mapNotNull null
            val label = resolved.loadLabel(pm).toString().ifBlank { fallbackLabel }
            OemGalleryShortcut(packageName = pkg, label = label)
        }
    }

    fun launchGallery(packageName: String): Boolean {
        if (GALLERY_CANDIDATES.none { it.first == packageName }) return false
        val intent = context.packageManager.getLaunchIntentForPackage(packageName) ?: return false
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return runCatching {
            context.startActivity(intent)
            true
        }.getOrDefault(false)
    }

    companion object {
        private val GALLERY_CANDIDATES = listOf(
            "com.miui.gallery" to "Galeri Xiaomi",
            "com.coloros.gallery3d" to "Foto OPPO",
            "com.oneplus.gallery" to "Galeri",
            "com.vivo.gallery" to "Galeri Vivo",
            "com.sec.android.gallery3d" to "Galeri Samsung",
            "com.transsion.gallery" to "Galeri",
            "com.google.android.apps.photos" to "Google Photos",
            "com.google.android.apps.nbu.files" to "Files by Google"
        )
    }
}
