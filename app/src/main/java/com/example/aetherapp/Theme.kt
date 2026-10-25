package com.example.aetherapp

import android.content.Context
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.widget.LinearLayout
import android.widget.TextView

/** تم نعنایی تیره */
object T {
    val bg = 0xFF06130F.toInt()
    val card = 0xFF0D211B.toInt()
    val stroke = 0xFF1B3D33.toInt()
    val mint = 0xFF5EEAC0.toInt()
    val mintDark = 0xFF1F7A62.toInt()
    val text = 0xFFE8FFF6.toInt()
    val sub = 0xFF8DB5A6.toInt()
    val red = 0xFFFF7A7A.toInt()
}

fun Context.dp(v: Int) = (v * resources.displayMetrics.density).toInt()

fun Context.shape(fill: Int, radius: Int, strokeColor: Int = 0, strokeDp: Int = 0) =
    GradientDrawable().apply {
        setColor(fill); cornerRadius = dp(radius).toFloat()
        if (strokeDp > 0) setStroke(dp(strokeDp), strokeColor)
    }

fun Context.label(t: String, size: Float, color: Int, bold: Boolean = false) =
    TextView(this).apply {
        text = t; textSize = size; setTextColor(color)
        if (bold) setTypeface(typeface, android.graphics.Typeface.BOLD)
    }

fun Context.cardBox(): LinearLayout = LinearLayout(this).apply {
    orientation = LinearLayout.VERTICAL
    background = shape(T.card, 22, T.stroke, 1)
    setPadding(dp(18), dp(16), dp(18), dp(16))
}

/** ردیف گزینه‌های انتخابی (چیپ) */
fun Context.chips(options: List<String>, selected: String, onPick: (String) -> Unit): LinearLayout {
    val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER }
    val views = options.map { opt ->
        label(opt, 13f, T.text).apply {
            gravity = Gravity.CENTER
            setPadding(dp(10), dp(12), dp(10), dp(12))
        }
    }
    fun paint(sel: String) = views.forEachIndexed { i, v ->
        val on = options[i] == sel
        v.background = shape(if (on) T.mintDark else T.bg, 14, if (on) T.mint else T.stroke, 1)
        v.setTextColor(if (on) T.mint else T.sub)
    }
    views.forEachIndexed { i, v ->
        row.addView(v, LinearLayout.LayoutParams(0, -2, 1f).apply { setMargins(dp(3), 0, dp(3), 0) })
        v.setOnClickListener { paint(options[i]); onPick(options[i]) }
    }
    paint(selected)
    return row
}
