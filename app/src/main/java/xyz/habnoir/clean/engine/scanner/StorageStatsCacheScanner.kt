package xyz.habnoir.clean.engine.scanner

import android.app.AppOpsManager
import android.app.usage.StorageStatsManager
import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.os.Build
import android.os.Environment
import android.os.Process
import android.os.StatFs
import android.os.storage.StorageManager
import androidx.collection.MutableIntSet
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import xyz.habnoir.clean.model.AppCacheItem
import xyz.habnoir.clean.model.ByteFormat
import xyz.habnoir.clean.model.DeviceStorageOverview
import xyz.habnoir.clean.model.PackageName
import java.util.concurrent.atomic.AtomicInteger

class StorageStatsCacheScanner(
    private val context: Context,
    private val systemProcessGuard: SystemProcessGuard
) {
    fun hasUsageAccessPermission(): Boolean {
        val appOps = context.getSystemService(AppOpsManager::class.java) ?: return false
        @Suppress("DEPRECATION")
        val mode = appOps.unsafeCheckOpNoThrow(
            AppOpsManager.OPSTR_GET_USAGE_STATS,
            Process.myUid(),
            context.packageName
        )
        return mode == AppOpsManager.MODE_ALLOWED
    }

    suspend fun readDeviceStorageOverview(): DeviceStorageOverview = withContext(Dispatchers.IO) {
        val statFs = runCatching { StatFs(Environment.getDataDirectory().path) }.getOrNull()
        if (statFs == null) {
            return@withContext DeviceStorageOverview(totalBytes = 0L, freeBytes = 0L, usedBytes = 0L)
        }
        val total = statFs.totalBytes
        val avail = statFs.availableBytes
        val used = maxOf(0L, total - avail)
        DeviceStorageOverview(totalBytes = total, freeBytes = avail, usedBytes = used)
    }

    suspend fun scanAllApps(
        whitelistedPackages: Set<String>,
        onProgress: (scanned: Int, total: Int) -> Unit = { _, _ -> }
    ): List<AppCacheItem> = withContext(Dispatchers.IO) {
        if (!hasUsageAccessPermission()) return@withContext emptyList()

        val statsManager = context.getSystemService(StorageStatsManager::class.java)
            ?: return@withContext emptyList()
        val pm = context.packageManager
        val userHandle = Process.myUserHandle()
        val uuid = StorageManager.UUID_DEFAULT
        val protectedPackages = systemProcessGuard.buildProtectedPackages()

        val installed = getInstalledAppsCompat(pm)
            .filter { app ->
                val isLaunchable = pm.getLaunchIntentForPackage(app.packageName) != null
                val isNonSystem = (app.flags and ApplicationInfo.FLAG_SYSTEM) == 0 ||
                    (app.flags and ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) != 0
                (isNonSystem || isLaunchable) && app.packageName !in protectedPackages
            }

        val totalCount = installed.size
        if (totalCount == 0) return@withContext emptyList()

        val semaphore = Semaphore(IPC_CONCURRENCY_LIMIT)
        val scannedCounter = AtomicInteger(0)
        val seenSharedUids = MutableIntSet(totalCount)
        val uidLock = Any()

        val rawResults = coroutineScope {
            installed.map { app ->
                async {
                    semaphore.withPermit {
                        val entry = runCatching {
                            val isFirstUidVisit = synchronized(uidLock) {
                                seenSharedUids.add(app.uid)
                            }
                            if (!isFirstUidVisit) {
                                // Skip duplicate UID accounting for packages sharing the same Linux UID
                                return@runCatching null
                            }
                            val stats = statsManager.queryStatsForPackage(uuid, app.packageName, userHandle)
                            val cacheBytes = maxOf(0L, stats.cacheBytes)
                            if (cacheBytes <= 0L) return@runCatching null

                            val label = pm.getApplicationLabel(app).toString().ifBlank { app.packageName }
                            AppCacheItem(
                                packageName = PackageName(app.packageName),
                                appLabel = label,
                                cacheBytes = cacheBytes,
                                formattedSize = ByteFormat.format(cacheBytes),
                                isWhitelisted = app.packageName in whitelistedPackages
                            )
                        }.getOrNull()

                        val done = scannedCounter.incrementAndGet()
                        if (done % 10 == 0 || done == totalCount) {
                            onProgress(done, totalCount)
                        }
                        entry
                    }
                }
            }.awaitAll()
        }

        withContext(Dispatchers.Default) {
            rawResults.filterNotNull().sortedByDescending { it.cacheBytes }
        }
    }

    suspend fun querySinglePackageCacheBytes(packageName: String): Long = withContext(Dispatchers.IO) {
        if (!hasUsageAccessPermission()) return@withContext 0L
        val statsManager = context.getSystemService(StorageStatsManager::class.java) ?: return@withContext 0L
        runCatching {
            val stats = statsManager.queryStatsForPackage(
                StorageManager.UUID_DEFAULT,
                packageName,
                Process.myUserHandle()
            )
            maxOf(0L, stats.cacheBytes)
        }.getOrDefault(0L)
    }

    private fun getInstalledAppsCompat(pm: PackageManager): List<ApplicationInfo> {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            pm.getInstalledApplications(PackageManager.ApplicationInfoFlags.of(0L))
        } else {
            @Suppress("DEPRECATION")
            pm.getInstalledApplications(0)
        }
    }

    companion object {
        private const val IPC_CONCURRENCY_LIMIT = 4
    }
}
