package com.example.prioritynotifier

import android.app.Activity
import android.content.Intent
import android.graphics.Typeface
import android.os.Bundle
import android.provider.Settings
import android.speech.tts.TextToSpeech
import android.view.View
import android.widget.*
import android.widget.LinearLayout.HORIZONTAL
import android.widget.LinearLayout.VERTICAL

class MainActivity : Activity() {
    private lateinit var st: Store
    private lateinit var root: LinearLayout
    private var tts: TextToSpeech? = null
    private var ttsReady = false

    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        st = Store(this)
        tts = TextToSpeech(this) { ttsReady = it == TextToSpeech.SUCCESS }
        root = LinearLayout(this).apply { orientation = VERTICAL; setPadding(dp(16), dp(24), dp(16), dp(32)) }
        setContentView(ScrollView(this).apply { addView(root) })
        build()
    }

    override fun onResume() { super.onResume(); if (::root.isInitialized) build() }
    override fun onDestroy() { tts?.shutdown(); super.onDestroy() }

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()
    private fun <T : View> add(v: T): T { root.addView(v); return v }
    private fun heading(t: String) = add(TextView(this).apply {
        text = t; textSize = 18f; setTypeface(null, Typeface.BOLD); setPadding(0, dp(22), 0, dp(6))
    })
    private fun note(t: String) = add(TextView(this).apply { text = t; alpha = 0.7f })
    private fun button(t: String, f: () -> Unit) = add(Button(this).apply { text = t; setOnClickListener { f() } })
    private fun sw(t: String, on: Boolean, f: (Boolean) -> Unit) = add(Switch(this).apply {
        text = t; isChecked = on; setPadding(0, dp(8), 0, dp(8)); setOnCheckedChangeListener { _, c -> f(c) }
    })
    private fun spinner(items: List<String>, sel: Int = 0, f: (Int) -> Unit = {}) = Spinner(this).apply {
        adapter = ArrayAdapter(this@MainActivity, android.R.layout.simple_spinner_dropdown_item, items)
        setSelection(sel)
        onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(p: AdapterView<*>?, v: View?, pos: Int, id: Long) = f(pos)
            override fun onNothingSelected(p: AdapterView<*>?) {}
        }
    }

    private fun build() {
        root.removeAllViews()
        add(TextView(this).apply { text = "Priority Notifier"; textSize = 26f; setTypeface(null, Typeface.BOLD) })
        note("Reads your WhatsApp and email notifications aloud, based on the priority you choose.")

        val on = Settings.Secure.getString(contentResolver, "enabled_notification_listeners")
            ?.contains(packageName) == true
        heading(if (on) "Notification access: on" else "Notification access: off")
        if (!on) note("Allow access so the app can see and read your notifications.")
        button("Open notification access settings") {
            startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
        }
        button("Stop Android from pausing this app") {
            startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
        }

        heading("Reading")
        sw("Read notifications aloud", st.enabled) { st.enabled = it }
        sw("Stay quiet when phone is on silent or vibrate", st.quietOnSilent) { st.quietOnSilent = it }
        note("Speed")
        add(SeekBar(this).apply {
            max = 10; progress = (st.rate * 10).toInt() - 6
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(s: SeekBar?, p: Int, u: Boolean) { st.rate = (p + 6) / 10f }
                override fun onStartTrackingTouch(s: SeekBar?) {}
                override fun onStopTrackingTouch(s: SeekBar?) {}
            })
        })
        button("Test voice") {
            if (ttsReady) {
                tts?.setSpeechRate(st.rate)
                tts?.speak("Important. WhatsApp from Mom: please call me.", TextToSpeech.QUEUE_FLUSH, null, "t")
            }
        }

        heading("Apps to read")
        APPS.forEach { (pkg, name) -> sw(name, st.appOn(pkg)) { st.setAppOn(pkg, it) } }

        heading("What each priority does")
        note("Critical messages interrupt anything being read.")
        LEVELS.forEachIndexed { i, name ->
            add(LinearLayout(this).apply {
                orientation = HORIZONTAL
                addView(Switch(this@MainActivity).apply {
                    text = name; isChecked = st.speak(i)
                    setOnCheckedChangeListener { _, c -> st.setSpeak(i, c) }
                }, LinearLayout.LayoutParams(0, -2, 1f))
                addView(spinner(listOf("Whole message", "Sender only"), if (st.full(i)) 0 else 1) { st.setFull(i, it == 0) })
            })
        }

        heading("Your priority rules")
        note("The highest matching priority wins. No match means Normal.")
        val type = add(spinner(listOf("Sender is", "Message has word")))
        val value = add(EditText(this).apply { hint = "For example: Mom, boss, urgent"; setSingleLine() })
        val level = add(spinner(LEVELS, 1))
        button("Add rule") {
            val v = value.text.toString().trim()
            if (v.isNotEmpty()) {
                st.rules = st.rules + Rule(if (type.selectedItemPosition == 0) "sender" else "word", v, level.selectedItemPosition)
                build()
            }
        }
        st.rules.forEachIndexed { i, r ->
            add(LinearLayout(this).apply {
                orientation = HORIZONTAL
                addView(TextView(this@MainActivity).apply {
                    text = (if (r.type == "sender") "Sender is " else "Has word ") + r.value + "  →  " + LEVELS[r.level]
                }, LinearLayout.LayoutParams(0, -2, 1f))
                addView(Button(this@MainActivity).apply {
                    text = "Remove"
                    setOnClickListener { st.rules = st.rules.filterIndexed { j, _ -> j != i }; build() }
                })
            })
        }
    }
}
