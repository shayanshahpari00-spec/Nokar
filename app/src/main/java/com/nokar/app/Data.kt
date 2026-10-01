package com.nokar.app

import android.content.Context
import android.graphics.Bitmap
import android.util.Base64
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.util.concurrent.TimeUnit

data class Msg(val role: String, val text: String, val image: Bitmap? = null)

class Prefs(ctx: Context) {
    private val sp = ctx.getSharedPreferences("nokar", Context.MODE_PRIVATE)
    var apiKey: String
        get() = sp.getString("key", "") ?: ""
        set(v) = sp.edit().putString("key", v.trim()).apply()
    var model: String
        get() = sp.getString("model", DEFAULT_MODEL) ?: DEFAULT_MODEL
        set(v) = sp.edit().putString("model", v.trim().ifBlank { DEFAULT_MODEL }).apply()
    var autoSpeak: Boolean
        get() = sp.getBoolean("speak", true)
        set(v) = sp.edit().putBoolean("speak", v).apply()
    var history: String
        get() = sp.getString("history", "[]") ?: "[]"
        set(v) = sp.edit().putString("history", v).apply()

    companion object { const val DEFAULT_MODEL = "claude-sonnet-5-5" }
}

object Claude {
    private val http = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(90, TimeUnit.SECONDS)
        .build()

    private fun system(subject: String) = """
تو «نوکر» هستی؛ یک رفیق درسیِ بامزه، صمیمی و باحال برای یک دانش‌آموز پایه نهم ایران.
- فارسی طبیعی و روان حرف بزن. شوخی کن ولی کم و به‌جا، طوری که درس خراب نشه.
- درس‌ها: ریاضی، علوم، فارسی، عربی، انگلیسی، مطالعات اجتماعی، پیام‌های آسمان، قرآن و هر درس دیگر پایه نهم.
- درس انتخاب‌شده: $subject. اگر «خودکار» بود، خودت از سؤال تشخیص بده.
- جواب دقیق و درست بده، ساده و در حد پایه نهم. اگر مطمئن نیستی صادقانه بگو.
- اگر نفهمید، مرحله‌به‌مرحله و با مثال توضیح بده. گاهی یک سؤال تمرینی کوتاه بپرس و جواب او را بررسی کن.
- جواب درست را تشویق کن. اشتباه را هرگز مسخره نکن؛ با آرامش توضیح بده.
- اگر عکس تار یا ناخوانا بود، مهربانانه بخواه عکس واضح‌تری بفرستد.
- جواب‌ها کوتاه و خوانا باشند. از علامت‌های مارک‌داون مثل * و # استفاده نکن (جواب با صدا هم خوانده می‌شود).
""".trimIndent()

    private fun b64(bmp: Bitmap): String {
        val out = ByteArrayOutputStream()
        bmp.compress(Bitmap.CompressFormat.JPEG, 85, out)
        return Base64.encodeToString(out.toByteArray(), Base64.NO_WRAP)
    }

    fun ask(key: String, model: String, subject: String, history: List<Msg>): String {
        require(key.isNotBlank()) { "اول کلید API را از ⚙️ تنظیمات وارد کن." }
        val msgs = JSONArray()
        history.takeLast(20).dropWhile { it.role != "user" }.forEach { m ->
            val content = JSONArray()
            if (m.image != null) content.put(JSONObject()
                .put("type", "image")
                .put("source", JSONObject().put("type", "base64")
                    .put("media_type", "image/jpeg").put("data", b64(m.image))))
            content.put(JSONObject().put("type", "text").put("text", m.text))
            msgs.put(JSONObject().put("role", m.role).put("content", content))
        }
        val body = JSONObject()
            .put("model", model).put("max_tokens", 1200)
            .put("system", system(subject)).put("messages", msgs)
        val req = Request.Builder()
            .url("https://api.anthropic.com/v1/messages")
            .header("x-api-key", key)
            .header("anthropic-version", "2023-06-01")
            .post(body.toString().toRequestBody("application/json".toMediaType()))
            .build()
        http.newCall(req).execute().use { r ->
            val s = r.body?.string().orEmpty()
            if (!r.isSuccessful) {
                val m = runCatching { JSONObject(s).getJSONObject("error").getString("message") }.getOrDefault(s.take(150))
                error("خطا (${r.code}): $m")
            }
            val c = JSONObject(s).getJSONArray("content")
            return buildString { for (i in 0 until c.length()) append(c.getJSONObject(i).optString("text")) }.trim()
        }
    }
}
