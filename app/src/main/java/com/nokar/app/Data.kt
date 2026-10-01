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
        get() {
            val m = sp.getString("model", DEFAULT_MODEL) ?: DEFAULT_MODEL
            return if (m.contains("claude")) DEFAULT_MODEL else m
        }
        set(v) = sp.edit().putString("model", v.trim().ifBlank { DEFAULT_MODEL }).apply()
    var autoSpeak: Boolean
        get() = sp.getBoolean("speak", true)
        set(v) = sp.edit().putBoolean("speak", v).apply()
    var history: String
        get() = sp.getString("history", "[]") ?: "[]"
        set(v) = sp.edit().putString("history", v).apply()

    companion object { const val DEFAULT_MODEL = "gemini-2.5-flash" }
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
        val contents = JSONArray()
        history.takeLast(20).dropWhile { it.role != "user" }.forEach { m ->
            val parts = JSONArray()
            if (m.image != null) parts.put(JSONObject().put("inline_data",
                JSONObject().put("mime_type", "image/jpeg").put("data", b64(m.image))))
            parts.put(JSONObject().put("text", m.text))
            contents.put(JSONObject()
                .put("role", if (m.role == "user") "user" else "model")
                .put("parts", parts))
        }
        val body = JSONObject()
            .put("system_instruction", JSONObject().put("parts",
                JSONArray().put(JSONObject().put("text", system(subject)))))
            .put("contents", contents)
        val req = Request.Builder()
            .url("https://generativelanguage.googleapis.com/v1beta/models/$model:generateContent")
            .header("x-goog-api-key", key)
            .post(body.toString().toRequestBody("application/json".toMediaType()))
            .build()
        http.newCall(req).execute().use { r ->
            val s = r.body?.string().orEmpty()
            if (!r.isSuccessful) {
                val m = runCatching { JSONObject(s).getJSONObject("error").getString("message") }.getOrDefault(s.take(150))
                error("خطا (${r.code}): $m")
            }
            val parts = JSONObject(s).optJSONArray("candidates")
                ?.optJSONObject(0)?.optJSONObject("content")?.optJSONArray("parts")
                ?: error("جواب خالی بود. یه جور دیگه بپرس.")
            return buildString { for (i in 0 until parts.length()) append(parts.getJSONObject(i).optString("text")) }.trim()
        }
    }
}
