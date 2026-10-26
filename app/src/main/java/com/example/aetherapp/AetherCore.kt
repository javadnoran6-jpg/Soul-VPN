package com.example.aetherapp

import android.content.Context
import java.io.File
import java.net.InetSocketAddress
import java.net.Socket
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread

/** هسته Aether را به‌صورت یک پروسه جدا اجرا می‌کند و پراکسی SOCKS5 محلی می‌دهد. */
class AetherCore(private val context: Context) {

    companion object {
        const val SOCKS_HOST = "127.0.0.1"
        const val SOCKS_PORT = 1819
    }

    /** وقتی هسته بدون درخواست ما بمیرد صدا زده می‌شود (نه موقع stop عادی). */
    @Volatile var onExit: (() -> Unit)? = null

    private var process: Process? = null
    private var stopping = false

    // طبق مستندات Aether مقدارهای مجاز AETHER_PROTOCOL فقط masque و wg و gool است
    private fun protocolValue(ui: String) = when (ui) {
        "Smart" -> "masque"
        "MASQUE" -> "masque"
        "WireGuard" -> "wg"
        "WARP×2" -> "gool"
        "MASQUE×2" -> "masque"
        else -> "masque"
    }

    fun start() {
        val bin = File(context.applicationInfo.nativeLibraryDir, "libaether.so")
        require(bin.exists()) { "libaether.so پیدا نشد؛ باینری اندروید را در jniLibs/arm64-v8a بگذارید" }

        val prefs = Prefs(context)
        val args = mutableListOf(bin.absolutePath)
        when (prefs.ip) { "IPv4" -> args += "-4"; "IPv6" -> args += "-6"; else -> args += "--dual" }
        val proto = protocolValue(prefs.protocol)
        if (prefs.peer.isNotBlank() && proto != "gool") {
            val port = if (proto == "wg") 2408 else 443
            args += listOf("--peer", "${prefs.peer}:$port")
        }
        val pb = ProcessBuilder(args)
        pb.directory(context.filesDir) // محل ذخیره هویت و تنظیمات هسته
        pb.environment().apply {
            put("HOME", context.filesDir.absolutePath)
            put("AETHER_PROTOCOL", protocolValue(prefs.protocol))
            put("AETHER_SCAN", prefs.scan.lowercase())
            // هر متغیری که ست نشود، هسته پرامپت تعاملی می‌پرسد و بدون ترمینال گیر می‌کند
            put("AETHER_NOIZE", if (proto == "masque") "firewall" else "balanced")
            put("AETHER_QUICK_RECONNECT", "1")
        }
        pb.redirectErrorStream(true)
        val p = pb.start()

        synchronized(this) {
            if (stopping) { p.destroy(); return }
            process = p
        }

        thread(name = "aether-log", isDaemon = true) {
            // وقتی stop() پروسه را می‌کشد، استریم بسته می‌شود و IOException می‌آید.
            // قبلاً این استثنا گرفته نمی‌شد و کل اپ را کرش می‌کرد.
            try {
                p.inputStream.bufferedReader().forEachLine { AppState.log(it) }
            } catch (_: Throwable) {
            }
            val code = try { p.waitFor() } catch (_: Throwable) { -1 }
            val intentional = synchronized(this@AetherCore) { stopping }
            if (!intentional) {
                AppState.log("هسته ناگهانی متوقف شد (کد $code)")
                onExit?.invoke()
            }
        }
    }

    /** تا باز شدن پورت SOCKS5 صبر می‌کند (مثلاً تا ۹۰ ثانیه). */
    fun waitUntilReady(timeoutMs: Long = 90_000): Boolean {
        val end = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < end) {
            val p = synchronized(this) { process }
            if (p == null || !p.isAlive) return false
            try {
                Socket().use { it.connect(InetSocketAddress(SOCKS_HOST, SOCKS_PORT), 500) }
                return true
            } catch (_: Exception) {
                try { Thread.sleep(500) } catch (_: InterruptedException) { return false }
            }
        }
        return false
    }

    /** idempotent و امن برای چندبار صدا زدن؛ از ترد اصلی صدا نزنید (تا ۲ ثانیه صبر می‌کند). */
    fun stop() {
        var p: Process? = null
        synchronized(this) { stopping = true; p = process; process = null }
        val proc = p ?: return
        proc.destroy()
        try {
            if (!proc.waitFor(2, TimeUnit.SECONDS)) proc.destroyForcibly()
        } catch (_: Throwable) {
        }
    }
}
