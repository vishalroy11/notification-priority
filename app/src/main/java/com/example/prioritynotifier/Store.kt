package com.example.prioritynotifier

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

val LEVELS = listOf("Critical", "High", "Normal", "Low")
val APPS = linkedMapOf(
    "com.whatsapp" to "WhatsApp",
    "com.whatsapp.w4b" to "WhatsApp Business",
    "com.google.android.gm" to "Gmail",
    "com.microsoft.office.outlook" to "Outlook",
    "com.yahoo.mobile.client.android.mail" to "Yahoo Mail",
    "com.google.android.apps.messaging" to "Messages (SMS)"
)

data class Rule(val type: String, val value: String, val level: Int) // type: "sender" or "word"

private val DEFAULTS = listOf(
    Rule("word", "urgent", 0), Rule("word", "meeting", 1),
    Rule("word", "sale", 3), Rule("word", "offer", 3)
)

class Store(ctx: Context) {
    private val p = ctx.getSharedPreferences("pn", Context.MODE_PRIVATE)

    var enabled: Boolean
        get() = p.getBoolean("on", true)
        set(v) = p.edit().putBoolean("on", v).apply()
    var quietOnSilent: Boolean
        get() = p.getBoolean("quiet", true)
        set(v) = p.edit().putBoolean("quiet", v).apply()
    var rate: Float
        get() = p.getFloat("rate", 1f)
        set(v) = p.edit().putFloat("rate", v).apply()

    fun speak(l: Int) = p.getBoolean("sp$l", l < 3)
    fun setSpeak(l: Int, v: Boolean) = p.edit().putBoolean("sp$l", v).apply()
    fun full(l: Int) = p.getBoolean("full$l", l < 2)
    fun setFull(l: Int, v: Boolean) = p.edit().putBoolean("full$l", v).apply()
    fun appOn(pkg: String) = p.getBoolean("app_$pkg", true)
    fun setAppOn(pkg: String, v: Boolean) = p.edit().putBoolean("app_$pkg", v).apply()

    var rules: List<Rule>
        get() {
            val raw = p.getString("rules", null) ?: return DEFAULTS
            val a = JSONArray(raw)
            return (0 until a.length()).map {
                val o = a.getJSONObject(it)
                Rule(o.getString("t"), o.getString("v"), o.getInt("l"))
            }
        }
        set(v) {
            val a = JSONArray()
            v.forEach { a.put(JSONObject().put("t", it.type).put("v", it.value).put("l", it.level)) }
            p.edit().putString("rules", a.toString()).apply()
        }

    // Highest priority (lowest index) among matching rules wins; no match = Normal.
    fun levelFor(sender: String, text: String): Int {
        var best: Int? = null
        for (r in rules) {
            val v = r.value.lowercase()
            val hit = if (r.type == "sender") sender.lowercase().contains(v)
                      else "$sender $text".lowercase().contains(v)
            if (hit && (best == null || r.level < best)) best = r.level
        }
        return best ?: 2
    }
}
