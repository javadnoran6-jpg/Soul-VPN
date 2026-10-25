package com.example.aetherapp

import android.Manifest
import android.animation.ObjectAnimator
import android.animation.PropertyValuesHolder
import android.animation.ValueAnimator
import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.net.VpnService
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.method.ScrollingMovementMethod
import android.view.Gravity
import android.view.HapticFeedbackConstants
import android.view.View
import android.view.animation.DecelerateInterpolator
import android.widget.*
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {

    private lateinit var orb: ImageView
    private lateinit var ringA: View
    private lateinit var ringB: View
    private lateinit var status: TextView
    private lateinit var hint: TextView
    private lateinit var timer: TextView
    private lateinit var mode: TextView
    private lateinit var logView: TextView
    private lateinit var downRate: TextView
    private lateinit var downTotal: TextView
    private lateinit var upRate: TextView
    private lateinit var upTotal: TextView
    private val tick = Handler(Looper.getMainLooper())

    private var ripples: List<ObjectAnimator> = emptyList()
    private var colorAnim: ValueAnimator? = null
    private var floatAnim: ObjectAnimator? = null
    private var shownColor = T.sub
    private var shownState = -1 // 0 قطع، 1 در حال اتصال، 2 متصل

    private val vpnPermission =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
            if (it.resultCode == Activity.RESULT_OK) startVpn()
            else AppState.log("مجوز VPN داده نشد")
        }
    private val notifPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = T.bg; window.navigationBarColor = T.bg

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(44), dp(20), dp(24))
        }

        // نوار بالا
        val top = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL }
        val titles = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        titles.addView(label("Soul", 30f, T.text, true))
        titles.addView(label("Let your soul slip free", 13f, T.mint))
        top.addView(titles, LinearLayout.LayoutParams(0, -2, 1f))
        top.addView(label("⚙", 26f, T.mint).apply {
            setPadding(dp(12), dp(8), dp(12), dp(8))
            setOnClickListener { startActivity(Intent(this@MainActivity, SettingsActivity::class.java)) }
        })
        root.addView(top)

        // صحنه‌ی دکمه: دو حلقه‌ی موجی پشت دکمه‌ی اصلی
        val stage = FrameLayout(this).apply { clipChildren = false }
        ringA = View(this).apply { alpha = 0f }
        ringB = View(this).apply { alpha = 0f }
        orb = ImageView(this).apply {
            setImageResource(R.drawable.ic_ghost)
            scaleType = ImageView.ScaleType.FIT_CENTER
            setPadding(dp(44), dp(38), dp(44), dp(38))
            setOnClickListener { toggle() }
        }
        stage.addView(ringA, FrameLayout.LayoutParams(dp(190), dp(190), Gravity.CENTER))
        stage.addView(ringB, FrameLayout.LayoutParams(dp(190), dp(190), Gravity.CENTER))
        stage.addView(orb, FrameLayout.LayoutParams(dp(190), dp(190), Gravity.CENTER))
        root.addView(stage, LinearLayout.LayoutParams(-1, dp(300)).apply { topMargin = dp(8) })

        // کارت وضعیت
        val card = cardBox()
        status = label("", 26f, T.mint, true).apply { gravity = Gravity.CENTER }
        hint = label("", 13f, T.sub).apply { gravity = Gravity.CENTER }
        timer = label("00:00:00", 34f, T.text, true).apply { gravity = Gravity.CENTER; setPadding(0, dp(12), 0, dp(10)) }
        mode = label("", 12f, T.mint).apply {
            gravity = Gravity.CENTER
            setPadding(dp(14), dp(6), dp(14), dp(6))
            background = shape(T.bg, 14, T.stroke, 1)
        }
        card.addView(status); card.addView(hint); card.addView(timer)
        card.addView(mode, LinearLayout.LayoutParams(-2, -2).apply { gravity = Gravity.CENTER_HORIZONTAL })
        root.addView(card, LinearLayout.LayoutParams(-1, -2))

        // آمار ترافیک
        downRate = label("0 B/s", 18f, T.text, true); downTotal = label("0 B", 11f, T.sub)
        upRate = label("0 B/s", 18f, T.text, true); upTotal = label("0 B", 11f, T.sub)
        fun stat(title: String, color: Int, rate: TextView, total: TextView): LinearLayout =
            cardBox().apply {
                addView(label(title, 12f, color, true))
                addView(rate.apply { setPadding(0, dp(6), 0, 0) })
                addView(total)
            }
        val statsRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        statsRow.addView(stat("↓ Download", T.mint, downRate, downTotal),
            LinearLayout.LayoutParams(0, -2, 1f).apply { rightMargin = dp(7) })
        statsRow.addView(stat("↑ Upload", T.amber, upRate, upTotal),
            LinearLayout.LayoutParams(0, -2, 1f).apply { leftMargin = dp(7) })
        root.addView(statsRow, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(14) })

        // لاگ
        val logCard = cardBox()
        val head = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL }
        head.addView(label("Log", 12f, T.mint, true), LinearLayout.LayoutParams(0, -2, 1f))
        head.addView(label("Copy", 12f, T.sub).apply {
            setPadding(dp(10), dp(4), dp(10), dp(4)); setOnClickListener { copyLog() }
        })
        head.addView(label("Clear", 12f, T.sub).apply {
            setPadding(dp(10), dp(4), dp(0), dp(4))
            setOnClickListener { AppState.clearLog(); refreshLog() }
        })
        logView = label("", 11f, T.sub).apply {
            typeface = Typeface.MONOSPACE
            movementMethod = ScrollingMovementMethod.getInstance()
        }
        logCard.addView(head)
        logCard.addView(logView, LinearLayout.LayoutParams(-1, dp(150)).apply { topMargin = dp(8) })
        root.addView(logCard, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(14) })

        setContentView(ScrollView(this).apply {
            background = GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, intArrayOf(T.bgTop, T.bg))
            isFillViewport = true; addView(root)
        })

        AppState.onChange = { render() }
        AppState.onLog = { refreshLog() }
        AppState.onStats = { renderStats() }
        if (Build.VERSION.SDK_INT >= 33) notifPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
    }

    override fun onResume() {
        super.onResume()
        render(); renderStats(); refreshLog()
        startFloat()
        tick.post(clock)
    }

    override fun onPause() {
        super.onPause()
        tick.removeCallbacks(clock)
        // انیمیشن‌ها در پس‌زمینه باتری نخورند؛ موقع برگشت دوباره ساخته می‌شوند
        colorAnim?.cancel()
        floatAnim?.cancel(); orb.translationY = 0f
        stopRipples()
        shownState = -1
    }

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
        orb.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
        orb.animate().scaleX(0.92f).scaleY(0.92f).setDuration(90).withEndAction {
            orb.animate().scaleX(1f).scaleY(1f).setDuration(140).start()
        }.start()

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
        val state = if (AppState.running) 2 else if (AppState.connecting) 1 else 0
        val color = when (state) { 2 -> T.mint; 1 -> T.amber; else -> T.sub }
        val fill = if (state == 2) T.cardOn else T.card
        if (state != shownState) {
            paintOrb(color, fill, shownState != -1)
            startRipples(state)
            shownState = state
        }
        status.text = when (state) { 2 -> "Soul is free"; 1 -> "Awakening…"; else -> "Asleep" }
        hint.text = when (state) { 2 -> "Tap to disconnect"; 1 -> "Tap to cancel"; else -> "Tap to awaken" }
        if (state != 2) timer.text = "00:00:00"
        val p = Prefs(this)
        mode.text = "${p.protocol}  ·  ${p.scan}  ·  ${p.ip}"
    }

    private fun renderStats() {
        val on = AppState.running
        downRate.text = fmtBytes(if (on) AppState.rxRate else 0L) + "/s"
        upRate.text = fmtBytes(if (on) AppState.txRate else 0L) + "/s"
        downTotal.text = "Total  " + fmtBytes(if (on) AppState.rxTotal else 0L)
        upTotal.text = "Total  " + fmtBytes(if (on) AppState.txTotal else 0L)
    }

    private fun paintOrb(color: Int, fill: Int, animate: Boolean) {
        colorAnim?.cancel()
        fun paint(c: Int) {
            shownColor = c
            orb.setColorFilter(c)
            orb.background = circle(fill, c, 3)
            status.setTextColor(c)
        }
        if (!animate) { paint(color); return }
        colorAnim = ValueAnimator.ofArgb(shownColor, color).apply {
            duration = 350
            addUpdateListener { paint(it.animatedValue as Int) }
            start()
        }
    }

    private fun startRipples(state: Int) {
        stopRipples()
        if (state == 0) return
        val c = if (state == 2) T.mint else T.amber
        val dur = if (state == 2) 2800L else 1500L
        ringA.background = circle(0, c, 2)
        ringB.background = circle(0, c, 2)
        ripples = listOf(ringA to 0L, ringB to dur / 2).map { (v, delay) ->
            ObjectAnimator.ofPropertyValuesHolder(
                v,
                PropertyValuesHolder.ofFloat(View.SCALE_X, 1f, 1.5f),
                PropertyValuesHolder.ofFloat(View.SCALE_Y, 1f, 1.5f),
                PropertyValuesHolder.ofFloat(View.ALPHA, 0.55f, 0f)
            ).apply {
                duration = dur
                startDelay = delay
                repeatCount = ValueAnimator.INFINITE
                interpolator = DecelerateInterpolator()
                start()
            }
        }
    }

    /** روح روی هوا بالا و پایین می‌رود. */
    private fun startFloat() {
        floatAnim?.cancel()
        floatAnim = ObjectAnimator.ofFloat(orb, View.TRANSLATION_Y, dp(6).toFloat(), -dp(6).toFloat()).apply {
            duration = 2200
            repeatMode = ValueAnimator.REVERSE
            repeatCount = ValueAnimator.INFINITE
            interpolator = android.view.animation.AccelerateDecelerateInterpolator()
            start()
        }
    }

    private fun stopRipples() {
        ripples.forEach { it.cancel() }
        ripples = emptyList()
        listOf(ringA, ringB).forEach { it.alpha = 0f; it.scaleX = 1f; it.scaleY = 1f }
    }

    private fun refreshLog() {
        logView.text = AppState.logText()
        logView.post {
            val l = logView.layout ?: return@post
            val y = l.getLineTop(logView.lineCount) - (logView.height - logView.paddingTop - logView.paddingBottom)
            logView.scrollTo(0, maxOf(0, y))
        }
    }

    private fun copyLog() {
        val cm = getSystemService(ClipboardManager::class.java)
        cm.setPrimaryClip(ClipData.newPlainText("aether-log", AppState.logText()))
        Toast.makeText(this, "Log copied", Toast.LENGTH_SHORT).show()
    }

    override fun onDestroy() {
        AppState.onChange = null; AppState.onLog = null; AppState.onStats = null
        super.onDestroy()
    }
}
