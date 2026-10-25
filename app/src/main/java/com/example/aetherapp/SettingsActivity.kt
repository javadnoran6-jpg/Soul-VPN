package com.example.aetherapp

import android.os.Bundle
import android.widget.LinearLayout
import android.widget.ScrollView
import androidx.appcompat.app.AppCompatActivity

class SettingsActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = T.bg; window.navigationBarColor = T.bg
        val p = Prefs(this)

        val col = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; setBackgroundColor(T.bg)
            setPadding(dp(20), dp(44), dp(20), dp(24))
        }
        col.addView(label("←  Tunnel settings", 22f, T.text, true).apply {
            setOnClickListener { finish() }; setPadding(0, 0, 0, dp(16))
        })
        col.addView(label(
            if (AppState.running) "Disconnect to change these. They are handed to the engine when it starts."
            else "These are handed to the engine when it starts.", 13f, T.sub).apply { setPadding(0, 0, 0, dp(16)) })

        fun section(title: String, options: List<String>, cur: String, set: (String) -> Unit, perRow: Int = 3) {
            val c = cardBox()
            c.addView(label(title, 14f, T.mint, true).apply { setPadding(0, 0, 0, dp(10)) })
            options.chunked(perRow).forEach { chunk ->
                // هر ردیف مستقل است؛ انتخاب بین ردیف‌ها با بازسازی همگام می‌شود
                c.addView(chipsRow(chunk, options, cur, set))
            }
            col.addView(c, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(14) })
        }

        section("Protocol", listOf("Smart", "MASQUE", "WireGuard", "WARP×2", "MASQUE×2"), p.protocol, { p.protocol = it })
        section("Scan mode", listOf("Turbo", "Balanced"), p.scan, { p.scan = it })
        section("IP version", listOf("IPv4", "IPv6", "Both"), p.ip, { p.ip = it })

        setContentView(ScrollView(this).apply { setBackgroundColor(T.bg); addView(col) })
    }

    private fun chipsRow(chunk: List<String>, all: List<String>, cur: String, set: (String) -> Unit): LinearLayout {
        // ساده: هر انتخاب، همان صفحه را دوباره می‌سازد تا همه ردیف‌ها هماهنگ شوند
        return chips(chunk, if (cur in chunk) cur else "") {
            set(it); recreate()
        }.apply { setPadding(0, 0, 0, dp(6)) }
    }
}
