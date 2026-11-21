package xyz.habnoir.clean.engine.reminder

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.job.JobInfo
import android.app.job.JobParameters
import android.app.job.JobScheduler
import android.app.job.JobService
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import xyz.habnoir.clean.HabNoirApp
import xyz.habnoir.clean.MainActivity
import xyz.habnoir.clean.R
import xyz.habnoir.clean.model.ByteFormat
import java.util.concurrent.TimeUnit

class StorageReminderJobService : JobService() {

    private var job: Job? = null

    override fun onStartJob(params: JobParameters?): Boolean {
        val app = application as? HabNoirApp
        if (app == null) {
            jobFinished(params, false)
            return false
        }

        job = CoroutineScope(Dispatchers.IO).launch {
            try {
                val prefs = app.container.prefsRepository.preferencesFlow.first()
                if (!prefs.reminderEnabled || !app.container.scanner.hasUsageAccessPermission()) {
                    return@launch
                }

                val apps = app.container.scanner.scanAllApps(prefs.whitelistedPackages)
                val cleanableBytes = apps
                    .filter { !it.isWhitelisted && it.cacheBytes >= prefs.minCacheThresholdBytes }
                    .sumOf { it.cacheBytes }

                if (cleanableBytes >= REMINDER_THRESHOLD_BYTES) {
                    postReminderNotification(cleanableBytes)
                }
            } finally {
                jobFinished(params, false)
            }
        }
        return true
    }

    override fun onStopJob(params: JobParameters?): Boolean {
        job?.cancel()
        return false
    }

    private fun postReminderNotification(totalBytes: Long) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val granted = ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
            if (!granted) return
        }

        val manager = getSystemService(NotificationManager::class.java) ?: return
        if (manager.getNotificationChannel(CHANNEL_ID) == null) {
            manager.createNotificationChannel(
                NotificationChannel(
                    CHANNEL_ID,
                    getString(R.string.notif_channel_reminder),
                    NotificationManager.IMPORTANCE_DEFAULT
                )
            )
        }

        val openIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val pi = PendingIntent.getActivity(this, 21, openIntent, PendingIntent.FLAG_IMMUTABLE)

        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_notify_more)
            .setContentTitle(getString(R.string.notif_reminder_title))
            .setContentText(getString(R.string.notif_reminder_text, ByteFormat.format(totalBytes)))
            .setContentIntent(pi)
            .setAutoCancel(true)
            .build()

        manager.notify(NOTIFICATION_ID, notification)
    }

    companion object {
        private const val JOB_ID = 4102
        private const val CHANNEL_ID = "habnoir_storage_reminder"
        private const val NOTIFICATION_ID = 702
        private const val REMINDER_THRESHOLD_BYTES = 1_500_000_000L // 1.5 GB

        fun updateSchedule(context: Context, enabled: Boolean) {
            val scheduler = context.getSystemService(JobScheduler::class.java) ?: return
            if (!enabled) {
                scheduler.cancel(JOB_ID)
                return
            }
            val info = JobInfo.Builder(
                JOB_ID,
                ComponentName(context, StorageReminderJobService::class.java)
            )
                .setPeriodic(TimeUnit.DAYS.toMillis(3))
                .setRequiresBatteryNotLow(true)
                .setPersisted(false)
                .build()
            scheduler.schedule(info)
        }
    }
}
