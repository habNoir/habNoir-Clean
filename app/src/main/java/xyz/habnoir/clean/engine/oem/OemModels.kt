package xyz.habnoir.clean.engine.oem

enum class OemFamily {
    XIAOMI_HYPEROS_MIUI,
    OPPO_REALME_COLOROS,
    VIVO_IQOO_FUNTOUCH,
    TRANSSION_XOS_HIOS,
    SAMSUNG_ONEUI,
    AOSP_GENERIC
}

data class OemComponentCandidate(
    val packageName: String,
    val className: String
)

data class OemCleanSpec(
    val family: OemFamily,
    val displayName: String,
    val settingsPackageIds: Set<String>,
    val storageEntryLabels: List<String>,
    val storageEntryViewIds: List<String>,
    val clearCacheButtonLabels: List<String>,
    val clearCacheViewIds: List<String>,
    val supportsXiaomiBottomSheet: Boolean = false,
    val bottomSheetTriggerLabels: List<String> = emptyList(),
    val bottomSheetOptionExactLabels: List<String> = emptyList(),
    val primaryTargetLabelDisplay: String,
    val oemBatteryAdviceId: String,
    val oemBatteryAdviceEn: String,
    val batterySettingsCandidates: List<OemComponentCandidate>
)

data class DeviceIdentity(
    val brandBadge: String,
    val modelName: String,
    val osLabel: String,
    val androidRelease: String
) {
    val headerLabel: String
        get() = when {
            osLabel.isBlank() -> brandBadge
            osLabel.startsWith(brandBadge, ignoreCase = true) -> osLabel
            else -> "$brandBadge $osLabel"
        }

    val fullDisplay: String
        get() = "$modelName ($osLabel, Android $androidRelease)"
}
