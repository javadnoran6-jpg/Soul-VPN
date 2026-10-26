package com.example.aetherapp

import android.content.Context
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.widget.LinearLayout
import android.widget.TextView

/** تم روح: بنفش شبانه + درخشش اکتوپلاسمی */
object T {
    val bg = 0xFF0A0A0A.toInt()
    val bgTop = 0xFF1C1C1C.toInt()
    val card = 0xFF151515.toInt()
    val cardOn = 0xFF262626.toInt()
    val stroke = 0xFF303030.toInt()
    val mint = 0xFFF5F5F5.toInt()
    val mintDark = 0xFF555555.toInt()
    val text = 0xFFF5F5F5.toInt()
    val sub = 0xFF8E8E8E.toInt()
    val red = 0xFFA8A8A8.toInt()
    val amber = 0xFFA8A8A8.toInt()
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

fun Context.circle(fill: Int, strokeColor: Int, strokeDp: Int) =
    GradientDrawable().apply {
        shape = GradientDrawable.OVAL
        setColor(fill)
        if (strokeDp > 0) setStroke(dp(strokeDp), strokeColor)
    }

fun fmtBytes(b: Long): String {
    if (b < 1024) return "$b B"
    val units = arrayOf("KB", "MB", "GB", "TB")
    var v = b / 1024.0
    var i = 0
    while (v >= 1024 && i < units.lastIndex) { v /= 1024; i++ }
    return String.format(java.util.Locale.US, "%.1f %s", v, units[i])
}
