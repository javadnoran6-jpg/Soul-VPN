package com.example.aetherapp

import android.os.Handler
import android.os.Looper

object AppState {
    @Volatile var running = false
    @Volatile var connecting = false
    @Volatile var connectedAt = 0L
    var onChange: (() -> Unit)? = null
    var onLog: ((String) -> Unit)? = null
    private val main = Handler(Looper.getMainLooper())

    fun updateRunning(v: Boolean) {
        running = v; connecting = false
        connectedAt = if (v) System.currentTimeMillis() else 0L
        main.post { onChange?.invoke() }
    }
    fun updateConnecting(v: Boolean) { connecting = v; main.post { onChange?.invoke() } }
    fun log(line: String) { main.post { onLog?.invoke(line) } }
}
