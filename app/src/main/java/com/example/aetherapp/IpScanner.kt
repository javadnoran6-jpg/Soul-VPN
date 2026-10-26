package com.example.aetherapp

import java.net.InetSocketAddress
import java.net.Socket
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

object IpScanner {

    data class Result(
        val ip: String, val port: Int,
        val min: Int, val median: Int, val jitter: Int,
        val ok: Int, val sent: Int
    ) {
        val lossPct: Int get() = (sent - ok) * 100 / sent
        /** هرچه کمتر، بهتر: تاخیر میانه + جریمه نوسان + جریمه‌ی بسته‌ی گم‌شده */
        val score: Int get() = median + 2 * jitter + 400 * (sent - ok) / sent
    }

    // بازه‌های رایج Cloudflare؛ این‌ها فقط نقطه‌ی شروع‌اند و می‌توانید عوضشان کنید.
    private val wgRanges = listOf("162.159.192", "162.159.193", "162.159.195", "188.114.96", "188.114.97", "188.114.98", "188.114.99")
    private val masqueRanges = listOf("162.159.198")

    /** @return لیست آدرس‌ها و پورت؛ لیست خالی یعنی این پروتکل اسکن تکی ندارد (gool). */
    fun pool(protocolUi: String, deep: Boolean): Pair<List<String>, Int> {
        val (ranges, port) = when (protocolUi) {
            "WireGuard" -> wgRanges to 2408
            "WARP×2" -> return emptyList<String>() to 0
            else -> masqueRanges to 443
        }
        val all = ranges.flatMap { p -> (1..254).map { "$p.$it" } }.shuffled()
        return (if (deep) all else all.take(120)) to port
    }

    fun scan(
        ips: List<String>, port: Int, cancel: AtomicBoolean,
        onProgress: (done: Int, total: Int) -> Unit
    ): List<Result> {
        val exec = Executors.newFixedThreadPool(48)
        val out = ConcurrentLinkedQueue<Result>()
        val done = AtomicInteger(0)
        val total = ips.size
        val futures = ips.map { ip ->
            exec.submit(Runnable {
                if (!cancel.get()) probe(ip, port, cancel)?.let { out.add(it) }
                val d = done.incrementAndGet()
                if (d % 5 == 0 || d == total) onProgress(d, total)
            })
        }
        futures.forEach { try { it.get() } catch (_: Throwable) {} }
        exec.shutdownNow()
        return out.toList()
    }

    private fun probe(ip: String, port: Int, cancel: AtomicBoolean): Result? {
        val n = 4
        val times = ArrayList<Int>()
        repeat(n) {
            if (cancel.get()) return@repeat
            val t0 = System.nanoTime()
            try {
                Socket().use { it.connect(InetSocketAddress(ip, port), 1200) }
                times.add(((System.nanoTime() - t0) / 1_000_000).toInt())
            } catch (_: Throwable) {
            }
        }
        if (times.isEmpty()) return null
        times.sort()
        return Result(ip, port, times.first(), times[times.size / 2], times.last() - times.first(), times.size, n)
    }
}
