package xyz.habnoir.clean.engine.staged

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import xyz.habnoir.clean.HabNoirApp
import xyz.habnoir.clean.MainActivity
import xyz.habnoir.clean.R

class CleanExecutionForegroundService : Service() {

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val app = application as? HabNoirApp
        when (intent?.action) {
            ACTION_PAUSE -> {
                app?.container?.orchestrator?.pause()
                return START_NOT_STICKY
            }
            ACTION_STOP -> {
                app?.container?.orchestrator?.stop()
                stopSelf()
                return START_NOT_STICKY
            }
        }

        ensureChannel()
        val label = intent?.getStringExtra(EXTRA_LABEL) ?: getString(R.string.app_name)
        val done = intent?.getIntExtra(EXTRA_DONE, 0) ?: 0
        val total = intent?.getIntExtra(EXTRA_TOTAL, 0) ?: 0
        val notification = buildNotification(label, done, total)

        val fgsType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
        } else {
            0
        }
        ServiceCompat.startForeground(this, NOTIFICATION_ID, notification, fgsType)
        return START_NOT_STICKY
    }

    private fun buildNotification(appLabel: String, done: Int, total: Int) =
        NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_download_done)
            .setContentTitle(getString(R.string.notif_cleaning_title))
            .setContentText(
                if (total > 0) getString(R.string.notif_cleaning_text, appLabel, done, total)
                else appLabel
            )
            .setOngoing(true)
            .setSilent(true)
            .setContentIntent(
                PendingIntent.getActivity(
                    this,
                    10,
                    Intent(this, MainActivity::class.java).apply {
                        flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
                    },
                    PendingIntent.FLAG_IMMUTABLE
                )
            )
            .addAction(
                0,
                getString(R.string.btn_pause),
                serviceActionPendingIntent(ACTION_PAUSE, 11)
            )
            .addAction(
                0,
                getString(R.string.btn_stop),
                serviceActionPendingIntent(ACTION_STOP, 12)
            )
            .build()

    private fun serviceActionPendingIntent(action: String, requestCode: Int): PendingIntent {
        val actionIntent = Intent(this, CleanExecutionForegroundService::class.java).apply {
            this.action = action
        }
        return PendingIntent.getService(
            this,
            requestCode,
            actionIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
    }

    private fun ensureChannel() {
        val manager = getSystemService(NotificationManager::class.java) ?: return
        if (manager.getNotificationChannel(CHANNEL_ID) != null) return
        val channel = NotificationChannel(
            CHANNEL_ID,
            getString(R.string.notif_channel_clean),
            NotificationManager.IMPORTANCE_LOW
        )
        manager.createNotificationChannel(channel)
    }

    companion object {
        private const val CHANNEL_ID = "habnoir_clean_progress"
        private const val NOTIFICATION_ID = 701
        private const val ACTION_PAUSE = "xyz.habnoir.clean.ACTION_PAUSE_CLEAN"
        private const val ACTION_STOP = "xyz.habnoir.clean.ACTION_STOP_CLEAN"
        private const val EXTRA_LABEL = "extra_label"
        private const val EXTRA_DONE = "extra_done"
        private const val EXTRA_TOTAL = "extra_total"

        fun startOrUpdate(context: Context, label: String, done: Int, total: Int) {
            val intent = Intent(context, CleanExecutionForegroundService::class.java).apply {
                putExtra(EXTRA_LABEL, label)
                putExtra(EXTRA_DONE, done)
                putExtra(EXTRA_TOTAL, total)
            }
            runCatching { context.startForegroundService(intent) }
        }

        fun stopService(context: Context) {
            runCatching {
                context.stopService(Intent(context, CleanExecutionForegroundService::class.java))
            }
        }
    }
}
