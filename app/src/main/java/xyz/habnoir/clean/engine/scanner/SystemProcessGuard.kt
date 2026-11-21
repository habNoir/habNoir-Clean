package xyz.habnoir.clean.engine.scanner

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings

class SystemProcessGuard(private val context: Context) {

    fun buildProtectedPackages(): Set<String> {
        val protectedSet = HashSet<String>(STATIC_PROTECTED_PACKAGES)
        protectedSet.add(context.packageName)
        resolveActiveLauncherPackage()?.let(protectedSet::add)
        resolveActiveImePackage()?.let(protectedSet::add)
        return protectedSet
    }

    private fun resolveActiveLauncherPackage(): String? {
        val pm = context.packageManager
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
        val info = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            pm.resolveActivity(intent, PackageManager.ResolveInfoFlags.of(PackageManager.MATCH_DEFAULT_ONLY.toLong()))
        } else {
            @Suppress("DEPRECATION")
            pm.resolveActivity(intent, PackageManager.MATCH_DEFAULT_ONLY)
        }
        return info?.activityInfo?.packageName
    }

    private fun resolveActiveImePackage(): String? {
        val raw = runCatching {
            Settings.Secure.getString(context.contentResolver, Settings.Secure.DEFAULT_INPUT_METHOD)
        }.getOrNull() ?: return null
        return ComponentName.unflattenFromString(raw)?.packageName
    }

    companion object {
        private val STATIC_PROTECTED_PACKAGES = setOf(
            "android",
            "com.android.systemui",
            "com.android.settings",
            "com.android.providers.settings",
            "com.android.packageinstaller",
            "com.google.android.packageinstaller",
            "com.android.permissioncontroller",
            "com.google.android.permissioncontroller",
            "com.google.android.gms",
            "com.google.android.gsf",
            "com.google.android.providers.media.module",
            "com.miui.securitycenter",
            "com.coloros.safecenter",
            "com.oplus.safecenter",
            "com.oplus.appdetail",
            "com.vivo.permissionmanager",
            "com.iqoo.secure",
            "com.transsion.phonemaster",
            "com.samsung.android.lool"
        )
    }
}
