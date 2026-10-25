package com.example.aetherapp

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Intent
import android.net.VpnService
import android.os.ParcelFileDescriptor
import hev.htproxy.TProxyService
import java.io.File
import kotlin.concurrent.thread

class AetherVpnService : VpnService() {

    companion object {
        const val ACTION_START = "start"
        const val ACTION_STOP = "stop"
        private const val CHANNEL = "aether"
    }

    private var core: AetherCore? = null
    private var tun: ParcelFileDescriptor? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) { shutdown(); return START_NOT_STICKY }
        if (AppState.running) return START_STICKY

        AppState.updateConnecting(true)
        startForeground(1, buildNotification())
        thread(name = "aether-start") { connect() }
        return START_STICKY
    }

    private fun connect() {
        try {
            AppState.log("در حال اجرای هسته…")
            val c = AetherCore(this).also { core = it }
            c.start()
            if (!c.waitUntilReady()) {
                AppState.log("هسته آماده نشد")
                shutdown(); return
            }
            AppState.log("پراکسی آماده است؛ ساخت تونل…")

            val pfd = Builder()
                .setSession("Aether")
                .setMtu(8500)
                .addAddress("198.18.0.1", 32)
                .addRoute("0.0.0.0", 0)
                .addDnsServer("1.1.1.1")
                .addDisallowedApplication(packageName) // جلوگیری از حلقه
                .establish()
            if (pfd == null) { AppState.log("ساخت VPN ناموفق بود"); shutdown(); return }
            tun = pfd

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
            if (!TProxyService.TProxyStartService(cfg.absolutePath, pfd.fd)) { AppState.log("hev start failed"); shutdown(); return }
            AppState.updateRunning(true)
            AppState.log("متصل شد")
        } catch (e: Throwable) {
            AppState.log("خطا: ${e.message}")
            shutdown()
        }
    }

    private fun shutdown() {
        try { TProxyService.TProxyStopService() } catch (_: Throwable) {}
        tun?.close(); tun = null
        core?.stop(); core = null
        AppState.updateRunning(false)
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onRevoke() { shutdown() }
    override fun onDestroy() { shutdown(); super.onDestroy() }

    private fun buildNotification(): Notification {
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel(CHANNEL, "Aether", NotificationManager.IMPORTANCE_LOW))
        return Notification.Builder(this, CHANNEL)
            .setContentTitle("Aether")
            .setContentText("اتصال فعال است")
            .setSmallIcon(android.R.drawable.ic_lock_lock)
            .setOngoing(true)
            .build()
    }
}
