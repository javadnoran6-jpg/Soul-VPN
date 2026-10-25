package com.example.aetherapp

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.net.VpnService
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.widget.*
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {

    private lateinit var orb: ImageView
    private lateinit var status: TextView
    private lateinit var hint: TextView
    private lateinit var timer: TextView
    private lateinit var mode: TextView
    private lateinit var logView: TextView
    private val tick = Handler(Looper.getMainLooper())

    private val vpnPermission =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
            if (it.resultCode == Activity.RESULT_OK) startVpn()
        }
    private val notifPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = T.bg; window.navigationBarColor = T.bg

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; setBackgroundColor(T.bg)
            setPadding(dp(20), dp(44), dp(20), dp(20))
        }

        // نوار بالا
        val top = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL }
        val titles = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        titles.addView(label("Aether", 28f, T.text, true))
        titles.addView(label("Freedom, in one tap", 13f, T.sub))
        top.addView(titles, LinearLayout.LayoutParams(0, -2, 1f))
        top.addView(label("⚙", 26f, T.mint).apply {
            setPadding(dp(12), dp(8), dp(12), dp(8))
            setOnClickListener { startActivity(Intent(this@MainActivity, SettingsActivity::class.java)) }
        })
        root.addView(top)

        // دکمه بزرگ
        orb = ImageView(this).apply {
            setImageResource(R.drawable.ic_launcher_foreground)
            scaleType = ImageView.ScaleType.FIT_CENTER
            setPadding(dp(14), dp(14), dp(14), dp(14))
            setOnClickListener { toggle() }
        }
        root.addView(orb, LinearLayout.LayoutParams(dp(190), dp(190)).apply {
            gravity = Gravity.CENTER_HORIZONTAL; topMargin = dp(36); bottomMargin = dp(28)
        })

        // کارت وضعیت
        val card = cardBox()
        status = label("", 26f, T.mint, true).apply { gravity = Gravity.CENTER }
        hint = label("", 13f, T.sub).apply { gravity = Gravity.CENTER }
        timer = label("00:00:00", 34f, T.text, true).apply { gravity = Gravity.CENTER; setPadding(0, dp(12), 0, dp(8)) }
        mode = label("", 13f, T.mint).apply { gravity = Gravity.CENTER }
        logView = label("", 11f, T.sub).apply { setTextIsSelectable(true) }
        card.addView(status); card.addView(hint); card.addView(timer); card.addView(mode)
        root.addView(card, LinearLayout.LayoutParams(-1, -2))

        // لاگ
        val logCard = cardBox().apply { addView(label("Log", 12f, T.mint, true)) }
        logCard.addView(ScrollView(this).apply { addView(logView) }, LinearLayout.LayoutParams(-1, -1))
        root.addView(logCard, LinearLayout.LayoutParams(-1, 0, 1f).apply { topMargin = dp(14) })

        setContentView(root)

        AppState.onChange = { render() }
        AppState.onLog = { logView.append(it + "\n") }
        if (Build.VERSION.SDK_INT >= 33) notifPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
    }

    override fun onResume() { super.onResume(); render(); tick.post(clock) }
    override fun onPause() { super.onPause(); tick.removeCallbacks(clock) }

    private val clock = object : Runnable {
        override fun run() {
            if (AppState.running) {
                val s = (System.currentTimeMillis() - AppState.connectedAt) / 1000
                timer.text = "%02d:%02d:%02d".format(s / 3600, s % 3600 / 60, s % 60)
            }
            tick.postDelayed(this, 1000)
        }
    }

    private fun toggle() {
        if (AppState.running || AppState.connecting) {
            startService(Intent(this, AetherVpnService::class.java).setAction(AetherVpnService.ACTION_STOP))
        } else {
            val prep = VpnService.prepare(this)
            if (prep != null) vpnPermission.launch(prep) else startVpn()
        }
    }

    private fun startVpn() =
        startForegroundService(Intent(this, AetherVpnService::class.java).setAction(AetherVpnService.ACTION_START))

    private fun render() {
        val on = AppState.running
        val busy = AppState.connecting
        val color = if (on) T.mint else if (busy) 0xFFFFC857.toInt() else T.sub
        orb.setColorFilter(color)
        orb.background = shape(if (on) 0xFF0F3A2E.toInt() else T.card, 95, color, if (on) 4 else 2)
        status.text = if (on) "Connected" else if (busy) "Connecting…" else "Disconnected"
        status.setTextColor(color)
        hint.text = if (on) "Tap to disconnect" else if (busy) "Tap to cancel" else "Tap to connect"
        if (!on) timer.text = "00:00:00"
        val p = Prefs(this)
        mode.text = "${p.protocol} · ${p.scan} · ${p.ip}"
    }

    override fun onDestroy() { AppState.onChange = null; AppState.onLog = null; super.onDestroy() }
}
