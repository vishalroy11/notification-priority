package com.example.prioritynotifier

import android.app.Notification
import android.media.AudioManager
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.speech.tts.TextToSpeech
import java.util.Locale

class ReaderService : NotificationListenerService(), TextToSpeech.OnInitListener {
    private lateinit var tts: TextToSpeech
    private var ready = false
    private val seen = HashMap<String, Long>()

    override fun onCreate() {
        super.onCreate()
        tts = TextToSpeech(this, this)
    }

    override fun onInit(status: Int) {
        ready = status == TextToSpeech.SUCCESS
        if (ready) tts.language = Locale.getDefault()
    }

    override fun onDestroy() {
        tts.shutdown()
        super.onDestroy()
    }

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        val st = Store(this)
        if (!st.enabled || !ready) return
        val label = APPS[sbn.packageName] ?: return
        if (!st.appOn(sbn.packageName)) return

        val n = sbn.notification
        if (n.flags and Notification.FLAG_GROUP_SUMMARY != 0) return
        val ex = n.extras
        val title = ex.getCharSequence(Notification.EXTRA_TITLE)?.toString() ?: return
        val text = (ex.getCharSequence(Notification.EXTRA_BIG_TEXT)
            ?: ex.getCharSequence(Notification.EXTRA_TEXT))?.toString() ?: return
        if (title.equals("WhatsApp", true) || title.equals("WhatsApp Business", true)) return // "3 new messages" summaries

        val audio = getSystemService(AUDIO_SERVICE) as AudioManager
        if (st.quietOnSilent && audio.ringerMode != AudioManager.RINGER_MODE_NORMAL) return

        // Skip the same text shown again within a minute (apps re-post notifications).
        val key = sbn.packageName + title + text.hashCode()
        val now = System.currentTimeMillis()
        seen.entries.removeAll { now - it.value > 60_000 }
        if (seen.containsKey(key)) return
        seen[key] = now

        val level = st.levelFor(title, text)
        if (!st.speak(level)) return
        val pre = if (level == 0) "Important. " else ""
        val msg = if (st.full(level)) "$pre$label from $title: ${text.take(300)}"
                  else "$pre$label from $title"
        tts.setSpeechRate(st.rate)
        tts.speak(msg, if (level == 0) TextToSpeech.QUEUE_FLUSH else TextToSpeech.QUEUE_ADD, null, key)
    }
}
