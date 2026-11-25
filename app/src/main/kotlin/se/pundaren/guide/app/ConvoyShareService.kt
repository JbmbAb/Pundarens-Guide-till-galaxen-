package se.pundaren.guide.app

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
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * Notisen under en Convoy-delning (07, T-09: en glömd delning ska synas hela tiden).
 *
 * Tjänsten delar ingenting själv. Den håller appen vid liv medan den ligger i bakgrunden eller
 * skärmen är släckt, så att [ConvoyController] kan fortsätta sända och ta emot fram till sluttiden.
 * Sluttiden bevakas som förut av avsändarens egen kontroll före varje sändning (07, 3.4); tjänsten
 * släpper dessutom sitt vaklås vid sluttiden, så att inget hålls vaket efter den.
 *
 * "Sluta dela" i notisen gör samma sak som knappen i Convoy-rutan.
 */
class ConvoyShareService : Service() {
    private var wakeLock: PowerManager.WakeLock? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            onStopRequested?.invoke()
            stopSelf()
            return START_NOT_STICKY
        }
        val until = intent?.getLongExtra(EXTRA_UNTIL, 0L)?.takeIf { it > 0 }
        if (until == null) {
            // Startad om av systemet utan uppgifter: då finns ingen delning att visa.
            stopSelf()
            return START_NOT_STICKY
        }
        try {
            val notification = notification(Instant.ofEpochMilli(until))
            if (Build.VERSION.SDK_INT >= 29) startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION)
            else startForeground(NOTIFICATION_ID, notification)
        } catch (e: RuntimeException) {
            // Systemet vägrade (till exempel för att appen redan låg i bakgrunden). Delningen fortsätter
            // så länge appen är framme, men utan notis och utan skydd i bakgrunden.
            log?.invoke("CONVOY: notisen kunde inte visas (${e.javaClass.simpleName})")
            stopSelf()
            return START_NOT_STICKY
        }
        hold(until - System.currentTimeMillis())
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        release()
        super.onDestroy()
    }

    /** Vaklåset släpper sig självt vid sluttiden, också om allt annat skulle ha stannat. */
    private fun hold(millis: Long) {
        release()
        if (millis <= 0) return
        val power = getSystemService(PowerManager::class.java) ?: return
        wakeLock = power.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "pundaren:convoy").apply {
            setReferenceCounted(false)
            acquire(millis + 5_000)
        }
    }

    private fun release() {
        wakeLock?.let { if (it.isHeld) it.release() }
        wakeLock = null
    }

    private fun notification(until: Instant): Notification {
        val manager = getSystemService(NotificationManager::class.java)
        manager?.createNotificationChannel(NotificationChannel(CHANNEL, "Convoy-delning", NotificationManager.IMPORTANCE_LOW))
        val open = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP), PendingIntent.FLAG_IMMUTABLE,
        )
        val stop = PendingIntent.getService(
            this, 1, Intent(this, ConvoyShareService::class.java).setAction(ACTION_STOP), PendingIntent.FLAG_IMMUTABLE,
        )
        val clock = DateTimeFormatter.ofPattern("HH.mm").format(until.atZone(ZoneId.systemDefault()))
        return Notification.Builder(this, CHANNEL)
            .setSmallIcon(android.R.drawable.ic_menu_mylocation)
            .setContentTitle("Convoy: du delar din position")
            .setContentText("Delningen pågår till kl. $clock.")
            .setOngoing(true)
            .setContentIntent(open)
            .addAction(Notification.Action.Builder(null, "Sluta dela", stop).build())
            .build()
    }

    companion object {
        private const val CHANNEL = "convoy"
        private const val NOTIFICATION_ID = 6
        private const val ACTION_STOP = "se.pundaren.guide.app.CONVOY_STOP"
        private const val EXTRA_UNTIL = "until"

        /** Sätts av kompositionsroten: vad "Sluta dela" i notisen gör. */
        @Volatile var onStopRequested: (() -> Unit)? = null
        @Volatile private var log: ((String) -> Unit)? = null

        /**
         * Startas när delningen börjar, medan appen är framme. Utan positionsbehörighet finns ingen
         * position att dela i bakgrunden, och då får systemet inte heller starta den här sortens tjänst.
         */
        fun start(context: Context, until: Instant, hasLocation: Boolean, log: (String) -> Unit) {
            this.log = log
            if (!hasLocation) {
                log("CONVOY: ingen positionsbehörighet, delningen pågår bara medan appen är framme")
                return
            }
            try {
                context.startForegroundService(Intent(context, ConvoyShareService::class.java).putExtra(EXTRA_UNTIL, until.toEpochMilli()))
            } catch (e: RuntimeException) {
                log("CONVOY: bakgrundstjänsten gick inte att starta (${e.javaClass.simpleName})")
            }
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, ConvoyShareService::class.java))
        }
    }
}
