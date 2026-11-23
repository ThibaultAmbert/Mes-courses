package fr.mescourses.app.work

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.os.SystemClock
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat

/**
 * Service au premier plan pendant que le chef travaille (proposition, validation du menu) :
 * MagicOS ne gèle pas l'application quand on la quitte, et l'écran peut s'éteindre sans tout arrêter.
 */
class MenuWorkService : Service() {

    private var wakeLock: PowerManager.WakeLock? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val text = intent?.getStringExtra(EXTRA_TEXT) ?: "Le chef prépare ton menu…"
        ServiceCompat.startForeground(
            this, NOTIFICATION_ID, notification(this, text),
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC else 0,
        )
        started = true
        if (!active) {
            ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
            stopSelf(startId)
            return START_NOT_STICKY
        }
        if (wakeLock == null) {
            wakeLock = (getSystemService(POWER_SERVICE) as PowerManager)
                .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "MesCourses:chef")
                .apply { acquire(30 * 60 * 1000L) }
        }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        started = false
        wakeLock?.takeIf { it.isHeld }?.release()
        wakeLock = null
        super.onDestroy()
    }

    companion object {
        private const val CHANNEL = "chef"
        private const val NOTIFICATION_ID = 42
        private const val EXTRA_TEXT = "text"
        private const val UPDATE_INTERVAL_MS = 250L

        private val notifyLock = Any()
        @Volatile private var channelReady = false
        @Volatile private var active = false
        /** Le service a appelé startForeground : on peut l'arrêter sans risque. */
        @Volatile private var started = false
        @Volatile private var lastText: String? = null
        @Volatile private var lastNotifyAt = 0L
        @Volatile private var cachedOpenIntent: PendingIntent? = null

        private fun openPendingIntent(context: Context): PendingIntent {
            cachedOpenIntent?.let { return it }
            val app = context.applicationContext
            val launch = app.packageManager.getLaunchIntentForPackage(app.packageName)
                ?: Intent().setClassName(app, "fr.mescourses.app.MainActivity").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            return PendingIntent.getActivity(
                app, 0, launch, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            ).also { cachedOpenIntent = it }
        }

        private fun notification(context: Context, text: String): Notification {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && !channelReady) {
                val nm = context.getSystemService(NotificationManager::class.java)
                if (nm.getNotificationChannel(CHANNEL) == null) {
                    nm.createNotificationChannel(NotificationChannel(CHANNEL, "Le chef au travail", NotificationManager.IMPORTANCE_LOW))
                }
                channelReady = true
            }
            return NotificationCompat.Builder(context, CHANNEL)
                .setSmallIcon(android.R.drawable.stat_notify_sync)
                .setContentTitle("Mes courses")
                .setContentText(text)
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .setContentIntent(openPendingIntent(context))
                .build()
        }

        fun start(context: Context, text: String) {
            synchronized(notifyLock) {
                active = true
                lastText = text
                lastNotifyAt = SystemClock.elapsedRealtime()
            }
            runCatching {
                ContextCompat.startForegroundService(context, Intent(context, MenuWorkService::class.java).putExtra(EXTRA_TEXT, text))
            }
        }

        /** Met à jour le texte de la notification (« recette 2/5 »). */
        fun update(context: Context, text: String) {
            val shouldNotify = synchronized(notifyLock) {
                if (!active || text == lastText) return
                val now = SystemClock.elapsedRealtime()
                if (now - lastNotifyAt < UPDATE_INTERVAL_MS) return
                lastText = text
                lastNotifyAt = now
                true
            }
            if (shouldNotify) {
                runCatching { context.getSystemService(NotificationManager::class.java).notify(NOTIFICATION_ID, notification(context, text)) }
            }
        }

        fun stop(context: Context) {
            synchronized(notifyLock) {
                active = false
                lastText = null
            }
            // Arrêté avant d'avoir démarré, le service ferait planter l'appli (startForeground jamais appelé) :
            // onStartCommand voit alors `active = false`, passe au premier plan puis s'arrête de lui-même.
            if (started) runCatching { context.stopService(Intent(context, MenuWorkService::class.java)) }
        }
    }
}
