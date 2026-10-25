package com.example.aetherapp

import android.content.Context
import java.io.File
import java.net.InetSocketAddress
import java.net.Socket
import kotlin.concurrent.thread

/** هسته Aether را به‌صورت یک پروسه جدا اجرا می‌کند و پراکسی SOCKS5 محلی می‌دهد. */
class AetherCore(private val context: Context) {

    companion object {
        const val SOCKS_HOST = "127.0.0.1"
        const val SOCKS_PORT = 1819
    }

    private var process: Process? = null

    // فقط masque و turbo/balanced در مستندات تأیید شده‌اند؛ بقیه مقدارها حدسی‌اند، با --help هسته چک کنید
    private fun protocolValue(ui: String) = when (ui) {
        "Smart" -> "smart"
        "MASQUE" -> "masque"
        "WireGuard" -> "wireguard"
        "WARP×2" -> "gool"
        "MASQUE×2" -> "masque-x2"
        else -> "masque"
    }

    fun start() {
        val bin = File(context.applicationInfo.nativeLibraryDir, "libaether.so")
        require(bin.exists()) { "libaether.so پیدا نشد؛ باینری اندروید را در jniLibs/arm64-v8a بگذارید" }

        val prefs = Prefs(context)
        val args = mutableListOf(bin.absolutePath)
        when (prefs.ip) { "IPv4" -> args += "-4"; "IPv6" -> args += "-6" }
        val pb = ProcessBuilder(args)
        pb.directory(context.filesDir) // محل ذخیره هویت و تنظیمات هسته
        pb.environment().apply {
            put("HOME", context.filesDir.absolutePath)
            // متغیرهای محیطی مستند‌شده در README پروژه (به جای پرامپت‌های تعاملی)
            put("AETHER_PROTOCOL", protocolValue(prefs.protocol))
            put("AETHER_SCAN", prefs.scan.lowercase())
        }
        pb.redirectErrorStream(true)
        val p = pb.start()
        process = p

        thread(name = "aether-log", isDaemon = true) {
            p.inputStream.bufferedReader().forEachLine { AppState.log(it) }
            AppState.log("هسته متوقف شد")
        }
    }

    /** تا باز شدن پورت SOCKS5 صبر می‌کند (مثلاً تا ۹۰ ثانیه). */
    fun waitUntilReady(timeoutMs: Long = 90_000): Boolean {
        val end = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < end) {
            if (process?.isAlive != true) return false
            try {
                Socket().use { it.connect(InetSocketAddress(SOCKS_HOST, SOCKS_PORT), 500) }
                return true
            } catch (_: Exception) { Thread.sleep(500) }
        }
        return false
    }

    fun stop() {
        process?.destroy()
        process = null
    }
}
