package xyz.habnoir.clean.engine.oem

object OemSpecRegistry {
    private val xiaomiSpec = OemCleanSpec(
        family = OemFamily.XIAOMI_HYPEROS_MIUI,
        displayName = "Xiaomi HyperOS / MIUI",
        settingsPackageIds = setOf("com.android.settings", "com.miui.securitycenter"),
        storageEntryLabels = listOf("penyimpanan", "storage", "penyimpanan internal"),
        storageEntryViewIds = listOf("com.android.settings:id/storage_summary"),
        clearCacheButtonLabels = listOf("bersihkan cache", "hapus cache", "clear cache"),
        clearCacheViewIds = listOf(
            "com.android.settings:id/button2",
            "com.miui.securitycenter:id/am_clear_cache"
        ),
        supportsXiaomiBottomSheet = true,
        bottomSheetTriggerLabels = listOf("bersihkan data", "hapus data", "clear data"),
        bottomSheetOptionExactLabels = listOf("bersihkan cache", "hapus cache", "clear cache"),
        primaryTargetLabelDisplay = "Bersihkan cache",
        oemBatteryAdviceId = "Aktifkan Mulai Otomatis dan set Penghemat Baterai ke Tidak ada pembatasan agar pembersihan tidak terhenti.",
        oemBatteryAdviceEn = "Enable Auto-start and set Battery Saver to No restrictions so staged cleaning is not killed.",
        batterySettingsCandidates = listOf(
            OemComponentCandidate("com.miui.securitycenter", "com.miui.permcenter.autostart.AutoStartManagementActivity"),
            OemComponentCandidate("com.miui.powerkeeper", "com.miui.powerkeeper.ui.HiddenAppsConfigActivity")
        )
    )

    private val oppoSpec = OemCleanSpec(
        family = OemFamily.OPPO_REALME_COLOROS,
        displayName = "OPPO / Realme ColorOS",
        settingsPackageIds = setOf("com.android.settings", "com.oplus.appdetail"),
        storageEntryLabels = listOf("penggunaan penyimpanan", "penyimpanan", "storage usage", "storage"),
        storageEntryViewIds = emptyList(),
        clearCacheButtonLabels = listOf("hapus cache", "bersihkan cache", "clear cache"),
        clearCacheViewIds = listOf("com.android.settings:id/button2"),
        primaryTargetLabelDisplay = "Hapus cache",
        oemBatteryAdviceId = "Izinkan Aktivitas Latar Belakang dan Mulai Otomatis di Pengelola Aplikasi.",
        oemBatteryAdviceEn = "Allow Background Activity and Auto-launch in App Management.",
        batterySettingsCandidates = listOf(
            OemComponentCandidate("com.oplus.safecenter", "com.oplus.safecenter.startupapp.StartupAppListActivity"),
            OemComponentCandidate("com.coloros.safecenter", "com.coloros.safecenter.permission.startup.StartupAppListActivity")
        )
    )

    private val vivoSpec = OemCleanSpec(
        family = OemFamily.VIVO_IQOO_FUNTOUCH,
        displayName = "Vivo / iQOO Funtouch",
        settingsPackageIds = setOf("com.android.settings"),
        storageEntryLabels = listOf("penyimpanan & cache", "penyimpanan", "storage & cache", "internal storage"),
        storageEntryViewIds = emptyList(),
        clearCacheButtonLabels = listOf("hapus cache", "bersihkan cache", "clear cache"),
        clearCacheViewIds = listOf("com.android.settings:id/button2"),
        primaryTargetLabelDisplay = "Hapus cache",
        oemBatteryAdviceId = "Atur penggunaan baterai ke Penggunaan daya latar belakang tinggi agar layanan tidak dihentikan iManager.",
        oemBatteryAdviceEn = "Set battery usage to High background power consumption so iManager does not stop the assistant.",
        batterySettingsCandidates = listOf(
            OemComponentCandidate("com.vivo.permissionmanager", "com.vivo.permissionmanager.activity.BgStartUpManagerActivity"),
            OemComponentCandidate("com.iqoo.secure", "com.iqoo.secure.ui.phoneoptimize.AddWhiteListActivity")
        )
    )

    private val transsionSpec = OemCleanSpec(
        family = OemFamily.TRANSSION_XOS_HIOS,
        displayName = "Infinix / Tecno / itel",
        settingsPackageIds = setOf("com.android.settings", "com.transsion.phonemaster"),
        storageEntryLabels = listOf("penyimpanan & cache", "penyimpanan", "storage & cache", "storage"),
        storageEntryViewIds = emptyList(),
        clearCacheButtonLabels = listOf("hapus cache", "clear cache"),
        clearCacheViewIds = listOf("com.android.settings:id/button2"),
        primaryTargetLabelDisplay = "Hapus cache",
        oemBatteryAdviceId = "Tambahkan habNoir Clean ke daftar abaikan di Phone Master atau Battery Lab.",
        oemBatteryAdviceEn = "Add habNoir Clean to Protected Apps in Phone Master or Battery Lab.",
        batterySettingsCandidates = listOf(
            OemComponentCandidate("com.transsion.phonemaster", "com.transsion.phonemaster.MainActivity")
        )
    )

    private val samsungSpec = OemCleanSpec(
        family = OemFamily.SAMSUNG_ONEUI,
        displayName = "Samsung One UI",
        settingsPackageIds = setOf("com.android.settings"),
        storageEntryLabels = listOf("penyimpanan", "storage"),
        storageEntryViewIds = emptyList(),
        clearCacheButtonLabels = listOf("hapus memori", "hapus cache", "clear cache"),
        clearCacheViewIds = listOf("com.android.settings:id/button2"),
        primaryTargetLabelDisplay = "Hapus memori",
        oemBatteryAdviceId = "Atur baterai ke Tidak dibatasi dan masukkan ke daftar Aplikasi tidak pernah tidur di Perawatan Perangkat.",
        oemBatteryAdviceEn = "Set battery to Unrestricted and add to Never auto sleeping apps in Device Care.",
        batterySettingsCandidates = emptyList()
    )

    private val aospSpec = OemCleanSpec(
        family = OemFamily.AOSP_GENERIC,
        displayName = "Android Standar",
        settingsPackageIds = setOf("com.android.settings"),
        storageEntryLabels = listOf("penyimpanan & cache", "penyimpanan", "storage & cache", "storage"),
        storageEntryViewIds = emptyList(),
        clearCacheButtonLabels = listOf("hapus cache", "clear cache"),
        clearCacheViewIds = listOf("com.android.settings:id/button2"),
        primaryTargetLabelDisplay = "Hapus cache",
        oemBatteryAdviceId = "Atur baterai ke Tidak dibatasi agar layanan aksesibilitas tidak berhenti saat jeda batch.",
        oemBatteryAdviceEn = "Set battery usage to Unrestricted so accessibility stays active across batch pauses.",
        batterySettingsCandidates = emptyList()
    )

    fun specFor(family: OemFamily): OemCleanSpec = when (family) {
        OemFamily.XIAOMI_HYPEROS_MIUI -> xiaomiSpec
        OemFamily.OPPO_REALME_COLOROS -> oppoSpec
        OemFamily.VIVO_IQOO_FUNTOUCH -> vivoSpec
        OemFamily.TRANSSION_XOS_HIOS -> transsionSpec
        OemFamily.SAMSUNG_ONEUI -> samsungSpec
        OemFamily.AOSP_GENERIC -> aospSpec
    }

    fun resolveFamily(
        manufacturer: String,
        brand: String,
        hasPackage: (String) -> Boolean
    ): OemFamily {
        val id = "${manufacturer.lowercase()} ${brand.lowercase()}"
        return when {
            id.contains("xiaomi") || id.contains("redmi") || id.contains("poco") ||
                hasPackage("com.miui.securitycenter") -> OemFamily.XIAOMI_HYPEROS_MIUI
            id.contains("oppo") || id.contains("realme") || id.contains("oneplus") ||
                hasPackage("com.oplus.appdetail") -> OemFamily.OPPO_REALME_COLOROS
            id.contains("vivo") || id.contains("iqoo") ||
                hasPackage("com.vivo.permissionmanager") -> OemFamily.VIVO_IQOO_FUNTOUCH
            id.contains("infinix") || id.contains("tecno") || id.contains("itel") ||
                hasPackage("com.transsion.phonemaster") -> OemFamily.TRANSSION_XOS_HIOS
            id.contains("samsung") || hasPackage("com.samsung.android.lool") -> OemFamily.SAMSUNG_ONEUI
            else -> OemFamily.AOSP_GENERIC
        }
    }

    fun resolveDeviceIdentity(
        manufacturer: String,
        brand: String,
        model: String,
        family: OemFamily,
        sdkInt: Int,
        androidRelease: String
    ): DeviceIdentity {
        val rawBrand = brand.ifBlank { manufacturer }.trim()
        val lowerBrand = rawBrand.lowercase()
        val combined = "${manufacturer.lowercase()} $lowerBrand"

        val brandBadge = when {
            lowerBrand == "tecno" || combined.contains("tecno") -> "TECNO"
            lowerBrand == "infinix" || combined.contains("infinix") -> "Infinix"
            lowerBrand == "itel" || combined.contains("itel") -> "itel"
            lowerBrand == "poco" || combined.contains("poco") -> "POCO"
            lowerBrand == "redmi" || combined.contains("redmi") -> "Redmi"
            lowerBrand == "xiaomi" || combined.contains("xiaomi") -> "Xiaomi"
            lowerBrand == "realme" || combined.contains("realme") -> "realme"
            lowerBrand == "oneplus" || combined.contains("oneplus") -> "OnePlus"
            lowerBrand == "oppo" || combined.contains("oppo") -> "OPPO"
            lowerBrand == "iqoo" || combined.contains("iqoo") -> "iQOO"
            lowerBrand == "vivo" || combined.contains("vivo") -> "vivo"
            lowerBrand == "samsung" || combined.contains("samsung") -> "Samsung"
            lowerBrand == "google" || combined.contains("google") -> "Google"
            rawBrand.isNotBlank() -> rawBrand.replaceFirstChar { it.uppercase() }
            else -> "Android"
        }

        val osLabel = when {
            lowerBrand == "tecno" || combined.contains("tecno") -> "HiOS"
            lowerBrand == "infinix" || combined.contains("infinix") -> "XOS"
            lowerBrand == "itel" || combined.contains("itel") -> "itel OS"
            family == OemFamily.XIAOMI_HYPEROS_MIUI -> if (sdkInt >= 34) "HyperOS" else "MIUI"
            lowerBrand == "realme" || combined.contains("realme") -> "realme UI"
            lowerBrand == "oneplus" || combined.contains("oneplus") -> "OxygenOS"
            family == OemFamily.OPPO_REALME_COLOROS -> "ColorOS"
            family == OemFamily.VIVO_IQOO_FUNTOUCH -> "Funtouch OS"
            family == OemFamily.SAMSUNG_ONEUI -> "One UI"
            family == OemFamily.TRANSSION_XOS_HIOS -> "Transsion OS"
            else -> "Android"
        }

        val trimmedModel = model.trim()
        val normalizedModel = when {
            trimmedModel.isEmpty() -> brandBadge
            trimmedModel.startsWith(brandBadge, ignoreCase = true) -> trimmedModel
            else -> "$brandBadge $trimmedModel"
        }
        val cleanRelease = androidRelease.trim().ifEmpty { sdkInt.toString() }

        return DeviceIdentity(
            brandBadge = brandBadge,
            modelName = normalizedModel,
            osLabel = osLabel,
            androidRelease = cleanRelease
        )
    }
}
