package com.example.aetherapp

import java.net.HttpURLConnection
import java.net.InetSocketAddress
import java.net.Proxy
import java.net.URL
import java.util.Locale

/** آی‌پی خروجی و کشور آن. */
data class IpInfo(val ip: String, val code: String) {
    val flag: String
        get() = if (code.length == 2 && code.all { it in 'A'..'Z' })
            StringBuilder().appendCodePoint(0x1F1E6 + (code[0] - 'A'))
                .appendCodePoint(0x1F1E6 + (code[1] - 'A')).toString()
        else "🌐"

    val country: String
        get() = try {
            Locale("", code).displayCountry.ifBlank { code }
        } catch (_: Throwable) { code }
}

object IpLookup {
    // خود اپ از VPN مستثنی است؛ پس درخواست را مستقیم از پراکسی SOCKS5 هسته می‌فرستیم
    // تا آی‌پی خروجی تونل را ببینیم، نه آی‌پی واقعی خودمان را.
    private val proxy = Proxy(Proxy.Type.SOCKS, InetSocketAddress(AetherCore.SOCKS_HOST, AetherCore.SOCKS_PORT))
    private val urls = listOf(
        "https://www.cloudflare.com/cdn-cgi/trace",
        "https://1.1.1.1/cdn-cgi/trace"
    )

    fun fetch(): IpInfo? {
        for (u in urls) {
            try {
                val c = URL(u).openConnection(proxy) as HttpURLConnection
                c.connectTimeout = 8000; c.readTimeout = 8000
                val text = c.inputStream.bufferedReader().use { it.readText() }
                c.disconnect()
                val m = text.lines().mapNotNull {
                    val i = it.indexOf('=')
                    if (i > 0) it.substring(0, i) to it.substring(i + 1).trim() else null
                }.toMap()
                val ip = m["ip"] ?: continue
                return IpInfo(ip, (m["loc"] ?: "").uppercase())
            } catch (_: Throwable) {
            }
        }
        return null
    }
}
