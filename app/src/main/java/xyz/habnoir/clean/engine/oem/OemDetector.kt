package xyz.habnoir.clean.engine.oem

import android.Manifest
import android.annotation.SuppressLint
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings

class OemDetector(private val context: Context) {

    fun detectSpec(): OemCleanSpec {
        val family = OemSpecRegistry.resolveFamily(
            manufacturer = Build.MANUFACTURER.orEmpty(),
            brand = Build.BRAND.orEmpty(),
            hasPackage = ::isPackageInstalled
        )
        return OemSpecRegistry.specFor(family)
    }

    fun detectDeviceIdentity(family: OemFamily = detectSpec().family): DeviceIdentity {
        return OemSpecRegistry.resolveDeviceIdentity(
            manufacturer = Build.MANUFACTURER.orEmpty(),
            brand = Build.BRAND.orEmpty(),
            model = Build.MODEL.orEmpty(),
            family = family,
            sdkInt = Build.VERSION.SDK_INT,
            androidRelease = Build.VERSION.RELEASE.orEmpty()
        )
    }

    @SuppressLint("BatteryLife")
    fun launchBatteryOrAutoStartSettings(spec: OemCleanSpec): Boolean {
        val powerManager = context.getSystemService(PowerManager::class.java)
        val alreadyIgnored = powerManager?.isIgnoringBatteryOptimizations(context.packageName) == true
        val hasRequestPerm = context.checkSelfPermission(Manifest.permission.REQUEST_IGNORE_BATTERY_OPTIMIZATIONS) ==
            PackageManager.PERMISSION_GRANTED
        if (!alreadyIgnored && hasRequestPerm) {
            val popupIntent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                data = Uri.fromParts("package", context.packageName, null)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            if (startSafeActivity(popupIntent)) {
                return true
            }
        }

        val pm = context.packageManager
        for (candidate in spec.batterySettingsCandidates) {
            val intent = Intent().apply {
                component = ComponentName(candidate.packageName, candidate.className)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            val resolved = resolveExportedActivity(pm, intent)
            if (resolved != null) {
                runCatching {
                    context.startActivity(intent)
                    return true
                }
            }
        }
        return openOwnAppDetailsSettings()
    }

    fun openOwnAppDetailsSettings(): Boolean {
        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = Uri.fromParts("package", context.packageName, null)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        return startSafeActivity(intent)
    }

    fun openUsageAccessSettings(): Boolean {
        val specificIntent = Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS).apply {
            data = Uri.fromParts("package", context.packageName, null)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        if (startSafeActivity(specificIntent)) return true

        val genericIntent = Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        return startSafeActivity(genericIntent)
    }

    fun openAccessibilitySettings(): Boolean {
        val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        return startSafeActivity(intent)
    }

    fun openAllFilesAccessSettings(): Boolean {
        val intent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION).apply {
            data = Uri.fromParts("package", context.packageName, null)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        if (startSafeActivity(intent)) return true

        val fallback = Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        return startSafeActivity(fallback)
    }

    private fun startSafeActivity(intent: Intent): Boolean {
        val resolved = resolveExportedActivity(context.packageManager, intent) ?: return false
        intent.component = resolved
        return runCatching {
            context.startActivity(intent)
            true
        }.getOrDefault(false)
    }

    private fun resolveExportedActivity(pm: PackageManager, intent: Intent): ComponentName? {
        val info = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            pm.resolveActivity(intent, PackageManager.ResolveInfoFlags.of(PackageManager.MATCH_DEFAULT_ONLY.toLong()))
        } else {
            @Suppress("DEPRECATION")
            pm.resolveActivity(intent, PackageManager.MATCH_DEFAULT_ONLY)
        } ?: return null

        val activityInfo = info.activityInfo ?: return null
        if (!activityInfo.exported) return null
        return ComponentName(activityInfo.packageName, activityInfo.name)
    }

    private fun isPackageInstalled(packageName: String): Boolean {
        val pm = context.packageManager
        return runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                pm.getPackageInfo(packageName, PackageManager.PackageInfoFlags.of(0L))
            } else {
                @Suppress("DEPRECATION")
                pm.getPackageInfo(packageName, 0)
            }
            true
        }.getOrDefault(false)
    }
}
