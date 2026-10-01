package com.nokar.app

import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import java.util.Locale

class MainActivity : ComponentActivity() {
    private lateinit var tts: TextToSpeech
    private var ttsReady = false
    private var ttsFa = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        tts = TextToSpeech(this) { st ->
            if (st == TextToSpeech.SUCCESS) {
                ttsReady = true
                val r = tts.setLanguage(Locale("fa", "IR"))
                ttsFa = r != TextToSpeech.LANG_MISSING_DATA && r != TextToSpeech.LANG_NOT_SUPPORTED
            }
        }
        setContent {
            NokarApp(speak = { text ->
                if (ttsReady && ttsFa) {
                    val clean = text.replace(Regex("[*#`_]"), "")
                    tts.speak(clean, TextToSpeech.QUEUE_FLUSH, null, "nokar")
                } else Toast.makeText(this,
                    "صدای فارسی روی گوشی نصب نیست. در تنظیمات گوشی › تبدیل متن به گفتار، موتور Google را با زبان فارسی نصب کن.",
                    Toast.LENGTH_LONG).show()
            }, stopSpeak = { if (ttsReady) tts.stop() })
        }
    }

    overrid
