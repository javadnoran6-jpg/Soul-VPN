package com.example.aetherapp

import android.content.Context

class Prefs(c: Context) {
    private val sp = c.getSharedPreferences("aether", Context.MODE_PRIVATE)
    var protocol: String
        get() = sp.getString("protocol", "MASQUE")!!
        set(v) = sp.edit().putString("protocol", v).apply()
    var scan: String
        get() = sp.getString("scan", "Turbo")!!
        set(v) = sp.edit().putString("scan", v).apply()
    var ip: String
        get() = sp.getString("ip", "IPv4")!!
        set(v) = sp.edit().putString("ip", v).apply()
}
