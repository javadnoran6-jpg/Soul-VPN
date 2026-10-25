package com.example.aetherapp

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.graphics.drawable.Icon
import android.net.VpnService
import android.os.ParcelFileDescriptor
import android.os.SystemClock
import hev.htproxy.TProxyService
import java.io.File
import kotlin.concurrent.thread

class AetherVpnService : VpnService() {

    companion object {
        const val ACTION_START = "start"
        const val ACTION_STOP = "stop"
        private const val CHANNEL = "aether"
        private const val NOTIF_ID = 1
    }

    // همه‌ی وضعیت‌های زیر فقط داخل synchronized(lock) خوانده/نوشته می‌شوند.
    private val lock = Any()
    private var generation = 0      // با هر start/stop بالا می‌رود؛ تردهای قدیمی خودشان را کنار می‌کشند
    private var active = false
    private var tunnelUp = false    // فقط وقتی hev واقعاً استارت شده true است
    private var core: AetherCore? = null
    private var tun: ParcelFileDescriptor? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> {
                // startForegroundService → باید همیشه و سریع startForeground صدا زده شود
                try {
                    startForeground(NOTIF_ID, buildNotification("روح در حال بیدار شدن…"))
                } catch (e: Throwable) {
                    AppState.log("خطا در اعلان سرویس: ${e.message}")
                    stopSelf()
                    return START_NOT_STICKY
                }
                val gen = begin()
                if (gen > 0) {
                    AppState.setConnecting(true)
                    thread(name = "aether-start") { connect(gen) }
                }
            }
            ACTION_STOP -> stopAsync()
            else -> stopSelf()
        }
        return START_NOT_STICKY
    }

    private fun begin(): Int = synchronized(lock) {
        if (active) 0 else { active = true; ++generation }
    }

    private fun isCurrent(gen: Int): Boolean = synchronized(lock) { active && gen == generation }

    private fun connect(gen: Int) {
        try {
            AppState.log("در حال اجرای هسته…")
            val c = AetherCore(this)
            c.onExit = { if (isCurrent(gen)) shutdown() }
            synchronized(lock) {
                if (!(active && gen == generation)) return
                core = c
            }
            c.start()
            if (!c.waitUntilReady()) {
                if (isCurrent(gen)) { AppState.log("هسته آماده نشد"); shutdown() }
                return
            }
            if (!isCurrent(gen)) return
            AppState.log("پراکسی آماده است؛ ساخت تونل…")

            val pfd = Builder()
                .setSession("Soul")
                .setMtu(8500)
                .addAddress("198.18.0.1", 32)
                .addRoute("0.0.0.0", 0)
                .addDnsServer("1.1.1.1")
                .addDnsServer("1.0.0.1")
                .addDisallowedApplication(packageName) // جلوگیری از حلقه
                .establish()
            if (pfd == null) { AppState.log("ساخت VPN ناموفق بود"); shutdown(); return }

            val cfg = File(filesDir, "hev.yml")
            cfg.writeText(
                """
                tunnel:
                  mtu: 8500
                  ipv4: 198.18.0.1
                socks5:
                  address: ${AetherCore.SOCKS_HOST}
                  port: ${AetherCore.SOCKS_PORT}
                """.trimIndent()
            )

            var started = false
            var stale = false
            synchronized(lock) {
                if (!(active && gen == generation)) {
                    stale = true
                } else {
                    tun = pfd
                    started = TProxyService.TProxyStartService(cfg.absolutePath, pfd.fd)
                    tunnelUp = started
                }
            }
            if (stale) { try { pfd.close() } catch (_: Throwable) {}; return }
            if (!started) { AppState.log("هسته تونل (hev) شروع نشد"); shutdown(); return }

            AppState.setRunning(true)
            updateNotification("روحت آزاد است")
            AppState.log("متصل شد")
            startStats(gen)
        } catch (e: Throwable) {
            AppState.log("خطا: ${e.message}")
            shutdown()
        }
    }

    /** آمار ترافیک؛ هم‌زمان با shutdown و زیر همان lock خوانده می‌شود تا هرگز بعد از stop به hev نرسد. */
    private fun startStats(gen: Int) {
        thread(name = "aether-stats", isDaemon = true) {
            var lastRx = 0L
            var lastTx = 0L
            var lastAt = SystemClock.elapsedRealtime()
            var first = true
            while (true) {
                try { Thread.sleep(1000) } catch (_: InterruptedException) { break }
                val s = readStats(gen) ?: break
                if (s.size < 4) continue
                val now = SystemClock.elapsedRealtime()
                val tx = s[1]
                val rx = s[3]
                val dt = maxOf(1L, now - lastAt)
                val txRate = if (first) 0L else maxOf(0L, (tx - lastTx) * 1000L / dt)
                val rxRate = if (first) 0L else maxOf(0L, (rx - lastRx) * 1000L / dt)
                first = false
                lastRx = rx; lastTx = tx; lastAt = now
                AppState.setStats(rx, tx, rxRate, txRate)
            }
        }
    }

    private fun readStats(gen: Int): LongArray? = synchronized(lock) {
        if (!(active && gen == generation && tunnelUp)) null
        else try { TProxyService.TProxyGetStats() } catch (_: Throwable) { null }
    }

    private fun stopAsync() {
        // stop ممکن است چند ثانیه طول بکشد؛ روی ترد اصلی نباید باشد
        thread(name = "aether-stop") { shutdown() }
    }

    /**
     * idempotent: چندبار صدا زدنش (دکمه، onRevoke، onDestroy، مرگ هسته) بی‌خطر است.
     * ترتیب مهم است: اول hev، بعد tun، بعد هسته.
     */
    private fun shutdown() {
        var c: AetherCore? = null
        var t: ParcelFileDescriptor? = null
        synchronized(lock) {
            active = false
            generation++
            if (tunnelUp) {
                tunnelUp = false
                try { TProxyService.TProxyStopService() } catch (_: Throwable) {}
            }
            c = core; core = null
            t = tun; tun = null
        }
        try { t?.close() } catch (_: Throwable) {}
        c?.stop()
        AppState.setRunning(false)
        try { stopForeground(STOP_FOREGROUND_REMOVE) } catch (_: Throwable) {}
        stopSelf()
    }

    override fun onRevoke() { stopAsync() }
    override fun onDestroy() { shutdown(); super.onDestroy() }

    private fun buildNotification(text: String): Notification {
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel(CHANNEL, "Soul", NotificationManager.IMPORTANCE_LOW))

        val open = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java)
                .setFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val stop = PendingIntent.getService(
            this, 1,
            Intent(this, AetherVpnService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val stopAction = Notification.Action.Builder(
            Icon.createWithResource(this, android.R.drawable.ic_menu_close_clear_cancel),
            "قطع اتصال", stop
        ).build()

        return Notification.Builder(this, CHANNEL)
            .setContentTitle("Soul")
            .setContentText(text)
            .setSmallIcon(R.drawable.ic_ghost_stat)
            .setContentIntent(open)
            .addAction(stopAction)
            .setCategory(Notification.CATEGORY_SERVICE)
            .setOngoing(true)
            .build()
    }

    private fun updateNotification(text: String) {
        try {
            getSystemService(NotificationManager::class.java).notify(NOTIF_ID, buildNotification(text))
        } catch (_: Throwable) {
        }
    }
}
