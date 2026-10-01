package com.nokar.app

import android.app.Application
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

val SUBJECTS = listOf("خودکار", "ریاضی", "علوم", "فارسی", "عربی", "انگلیسی",
    "مطالعات اجتماعی", "پیام‌های آسمان", "قرآن")

class ChatVM(private val app: Application) : AndroidViewModel(app) {
    val prefs = Prefs(app)
    val msgs = mutableStateListOf<Msg>()
    var loading by mutableStateOf(false)
    var subject by mutableStateOf(SUBJECTS[0])
    var error by mutableStateOf<String?>(null)
    var pendingImage by mutableStateOf<Bitmap?>(null)

    init {
        runCatching {
            val a = JSONArray(prefs.history)
            for (i in 0 until a.length()) a.getJSONObject(i).let { msgs.add(Msg(it.getString("r"), it.getString("t"))) }
        }
    }

    private fun save() {
        val a = JSONArray()
        msgs.takeLast(60).forEach { a.put(JSONObject().put("r", it.role).put("t", it.text)) }
        prefs.history = a.toString()
    }

    fun newChat() { msgs.clear(); pendingImage = null; error = null; save() }

    fun setImage(uri: Uri) {
        viewModelScope.launch {
            val bmp = withContext(Dispatchers.IO) {
                runCatching {
                    ImageDecoder.decodeBitmap(ImageDecoder.createSource(app.contentResolver, uri)) { d, info, _ ->
                        d.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
                        val big = maxOf(info.size.width, info.size.height)
                        if (big > 1280) { val k = 1280f / big; d.setTargetSize((info.size.width * k).toInt(), (info.size.height * k).toInt()) }
                    }
                }.getOrNull()
            }
            if (bmp == null) error = "عکس باز نشد، دوباره امتحان کن." else pendingImage = bmp
        }
    }

    fun send(text: String, onReply: (String) -> Unit) {
        val img = pendingImage
        if (loading || (text.isBlank() && img == null)) return
        error = null
        msgs.add(Msg("user", text.ifBlank { "این عکس رو نگاه کن و کمکم کن." }, img))
        pendingImage = null
        loading = true
        val snapshot = msgs.toList()
        viewModelScope.launch {
            val r = withContext(Dispatchers.IO) {
                runCatching { Claude.ask(prefs.apiKey, prefs.model, subject, snapshot) }
            }
            r.onSuccess { msgs.add(Msg("assistant", it)); save(); onReply(it) }
                .onFailure { error = it.message ?: "مشکلی پیش آمد. اینترنتت را چک کن." }
            loading = false
        }
    }
}
