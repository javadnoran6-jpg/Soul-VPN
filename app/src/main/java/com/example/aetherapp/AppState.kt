package com.example.aetherapp

import android.os.Handler
import android.os.Looper
import java.time.LocalTime
import java.time.format.DateTimeFormatter

object AppState {
    @Volatile var running = false
    @Volatile var connecting = false
    @Volatile var connectedAt = 0L

    // آمار ترافیک (بایت و بایت بر ثانیه)
    @Volatile var rxTotal = 0L
    @Volatile var txTotal = 0L
    @Volatile var rxRate = 0L
    @Volatile var txRate = 0L

    @Volatile var onChange: (() -> Unit)? = null
    @Volatile var onLog: ((String) -> Unit)? = null
    @Volatile var onStats: (() -> Unit)? = null

    private const val MAX_LINES = 300
    private val main = Handler(Looper.getMainLooper())
    private val buffer = ArrayDeque<String>()
    private val timeFmt = DateTimeFormatter.ofPattern("HH:mm:ss")

    fun updateRunning(v: Boolean) {
        running = v; connecting = false
        connectedAt = if (v) System.currentTimeMillis() else 0L
        if (!v) { rxTotal = 0L; txTotal = 0L; rxRate = 0L; txRate = 0L }
        main.post { onChange?.invoke(); onStats?.invoke() }
    }

    fun updateConnecting(v: Boolean) { connecting = v; main.post { onChange?.invoke() } }

    fun setStats(rx: Long, tx: Long, rxSpeed: Long, txSpeed: Long) {
        rxTotal = rx; txTotal = tx; rxRate = rxSpeed; txRate = txSpeed
        main.post { onStats?.invoke() }
    }

    /** لاگ در حافظه می‌ماند تا با چرخش صفحه یا بازشدن دوباره اکتیویتی از بین نرود. */
    fun log(line: String) {
        val stamped = LocalTime.now().format(timeFmt) + "  " + line
        synchronized(buffer) {
            buffer.addLast(stamped)
            while (buffer.size > MAX_LINES) buffer.removeFirst()
        }
        main.post { onLog?.invoke(stamped) }
    }

    fun logText(): String = synchronized(buffer) { buffer.joinToString("\n") }

    fun clearLog() { synchronized(buffer) { buffer.clear() } }
}
