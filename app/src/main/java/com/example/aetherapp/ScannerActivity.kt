package com.example.aetherapp

import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.graphics.Typeface
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.concurrent.thread

class ScannerActivity : AppCompatActivity() {

    private lateinit var prefs: Prefs
    private lateinit var startBtn: TextView
    private lateinit var progress: TextView
    private lateinit var forced: TextView
    private lateinit var results: LinearLayout
    private var deep = false
    private val cancel = AtomicBoolean(false)
    @Volatile private var scanning = false
    private val ui = Handler(Looper.getMainLooper())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = T.bg; window.navigationBarColor = T.bg
        prefs = Prefs(this)

        val col = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; setBackgroundColor(T.bg)
            setPadding(dp(20), dp(44), dp(20), dp(24))
        }
        col.addView(label("←  IP Scanner", 22f, T.text, true).apply {
            setOnClickListener { finish() }; setPadding(0, 0, 0, dp(10))
        })
        col.addView(label(
            "Measures the real connect time to Cloudflare endpoints, outside the tunnel, " +
                "then picks the fastest and the most stable one.", 13f, T.sub
        ).apply { setPadding(0, 0, 0, dp(14)) })

        val (pool, port) = IpScanner.pool(prefs.protocol, false)
        val poolCard = cardBox().apply {
            addView(label("Scan", 14f, T.mint, true).apply { setPadding(0, 0, 0, dp(6)) })
            addView(label(
                if (pool.isEmpty()) "${prefs.protocol}: needs two hops, scanner not available. Switch to MASQUE or WireGuard."
                else "${prefs.protocol}  ·  port $port  ·  Quick 120 IPs / Deep all", 12f, T.sub
            ).apply { setPadding(0, 0, 0, dp(10)) })
            addView(chips(listOf("Quick", "Deep"), "Quick") { deep = it == "Deep" })
        }
        col.addView(poolCard, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(12) })

        startBtn = label("Start scan", 16f, T.text, true).apply {
            gravity = Gravity.CENTER
            setPadding(dp(16), dp(14), dp(16), dp(14))
            background = shape(T.mintDark, 16, T.mint, 1)
            setOnClickListener { start() }
        }
        col.addView(startBtn, LinearLayout.LayoutParams(-1, -2))
        progress = label("", 12f, T.sub).apply { gravity = Gravity.CENTER; setPadding(0, dp(10), 0, dp(10)) }
        col.addView(progress)

        forced = label("", 12f, T.text).apply {
            setPadding(dp(14), dp(10), dp(14), dp(10))
            background = shape(T.bg, 14, T.stroke, 1)
            setOnClickListener {
                if (prefs.peer.isNotBlank()) { prefs.peer = ""; renderForced(); toast("Back to automatic scan") }
            }
        }
        col.addView(forced, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(12) })
        renderForced()

        results = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        col.addView(results)
        setContentView(ScrollView(this).apply { setBackgroundColor(T.bg); addView(col) })
    }

    private fun renderForced() {
        forced.text = if (prefs.peer.isBlank()) "Endpoint: automatic (Aether scans by itself)"
        else "Endpoint forced: ${prefs.peer}   ·   tap to reset"
    }

    private fun start() {
        if (scanning) { cancel.set(true); return }
        val (ips, port) = IpScanner.pool(prefs.protocol, deep)
        if (ips.isEmpty()) { toast("Not available for ${prefs.protocol}"); return }
        scanning = true; cancel.set(false)
        startBtn.text = "Stop"
        results.removeAllViews()
        progress.text = "Scanning…"
        thread(name = "ip-scan", isDaemon = true) {
            val list = IpScanner.scan(ips, port, cancel) { d, t -> ui.post { progress.text = "Scanned $d / $t" } }
            ui.post { finishScan(list, ips.size) }
        }
    }

    private fun finishScan(list: List<IpScanner.Result>, total: Int) {
        scanning = false
        startBtn.text = "Start scan"
        if (isDestroyed) return
        progress.text = "${list.size} of $total addresses reachable" + if (cancel.get()) "  (stopped)" else ""
        if (list.isEmpty()) {
            results.addView(label("No reachable address. Check your connection and try again.", 13f, T.sub))
            return
        }
        val fastest = list.minByOrNull { it.min }!!
        val stable = list.filter { it.ok * 3 >= it.sent * 2 }.minByOrNull { it.score } ?: list.minByOrNull { it.score }!!
        results.addView(resultCard("Fastest", fastest))
        results.addView(resultCard("Best (stable)", stable))

        val top = cardBox()
        top.addView(label("Top 10", 12f, T.mint, true).apply { setPadding(0, 0, 0, dp(6)) })
        list.sortedBy { it.score }.take(10).forEach {
            top.addView(label("%-15s %4d ms  loss %d%%".format(it.ip, it.median, it.lossPct), 11f, T.sub).apply {
                typeface = Typeface.MONOSPACE
            })
        }
        results.addView(top, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(12) })
    }

    private fun resultCard(title: String, r: IpScanner.Result): LinearLayout {
        val c = cardBox()
        c.addView(label(title, 12f, T.mint, true))
        c.addView(label("${r.ip}:${r.port}", 20f, T.text, true).apply {
            typeface = Typeface.MONOSPACE; setPadding(0, dp(6), 0, dp(2))
        })
        c.addView(label("min ${r.min} ms  ·  median ${r.median} ms  ·  jitter ${r.jitter} ms  ·  loss ${r.lossPct}%", 12f, T.sub))
        c.addView(label("Use this IP", 14f, T.text, true).apply {
            gravity = Gravity.CENTER
            setPadding(dp(12), dp(10), dp(12), dp(10))
            background = shape(T.bg, 14, T.mint, 1)
            setOnClickListener {
                prefs.peer = r.ip; renderForced()
                toast("Saved. Reconnect to apply.")
            }
        }, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(10) })
        c.layoutParams = LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(12) }
        return c
    }

    private fun toast(s: String) = Toast.makeText(this, s, Toast.LENGTH_SHORT).show()

    override fun onDestroy() { cancel.set(true); super.onDestroy() }
}
