package com.example.screenhelper

import android.accessibilityservice.AccessibilityService
import android.graphics.*
import android.os.Handler
import android.os.Looper
import android.util.Base64
import android.view.*
import android.view.accessibility.AccessibilityEvent
import android.widget.*
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.net.HttpURLConnection
import java.net.URL

class ScreenHelperService : AccessibilityService() {

    companion object { var instance: ScreenHelperService? = null }

    private lateinit var wm: WindowManager
    private var button: TextView? = null
    private var panel: View? = null
    private var busy = false
    private val ui = Handler(Looper.getMainLooper())

    override fun onServiceConnected() {
        instance = this
        wm = getSystemService(WINDOW_SERVICE) as WindowManager
    }
    override fun onAccessibilityEvent(e: AccessibilityEvent?) {}
    override fun onInterrupt() {}
    override fun onUnbind(i: android.content.Intent?): Boolean { stopHelper(); instance = null; return super.onUnbind(i) }

    private fun params(gravity: Int) = WindowManager.LayoutParams(
        WindowManager.LayoutParams.WRAP_CONTENT, WindowManager.LayoutParams.WRAP_CONTENT,
        WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
        WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE, PixelFormat.TRANSLUCENT
    ).apply { this.gravity = gravity }

    // ВКЛЮЧИТЬ: показывает кнопку. Больше ничего не происходит само.
    fun startHelper() {
        if (button != null) return
        val b = TextView(this).apply {
            text = "?"; textSize = 24f; setTextColor(Color.WHITE); gravity = Gravity.CENTER
            setBackgroundColor(Color.parseColor("#CC1565C0")); width = 140; height = 140
            setOnClickListener { analyze() }
            setOnLongClickListener { stopHelper(); true } // долгое нажатие = выключить
        }
        button = b
        wm.addView(b, params(Gravity.END or Gravity.CENTER_VERTICAL))
    }

    // ВЫКЛЮЧИТЬ: убирает кнопку и окно ответа
    fun stopHelper() {
        button?.let { runCatching { wm.removeView(it) } }; button = null
        closePanel()
    }

    private fun closePanel() { panel?.let { runCatching { wm.removeView(it) } }; panel = null }

    private fun analyze() {
        if (busy) return
        val key = getSharedPreferences("p", MODE_PRIVATE).getString("key", "") ?: ""
        if (key.isBlank()) { showText("Введите ключ Gemini в приложении"); return }
        busy = true
        closePanel()
        button?.visibility = View.INVISIBLE
        ui.postDelayed({
            takeScreenshot(Display.DEFAULT_DISPLAY, mainExecutor, object : TakeScreenshotCallback {
                override fun onSuccess(r: ScreenshotResult) {
                    button?.visibility = View.VISIBLE
                    val bmp = Bitmap.wrapHardwareBuffer(r.hardwareBuffer, r.colorSpace)
                        ?.copy(Bitmap.Config.ARGB_8888, false)
                    r.hardwareBuffer.close()
                    if (bmp == null) { busy = false; showText("Не удалось сделать скриншот"); return }
                    showText("Думаю…")
                    Thread { val ans = ask(key, bmp); ui.post { busy = false; showText(ans) } }.start()
                }
                override fun onFailure(code: Int) {
                    button?.visibility = View.VISIBLE; busy = false; showText("Ошибка скриншота: $code")
                }
            })
        }, 200)
    }

    // Если появится ошибка про модель, замените на "gemini-2.5-flash"
    private val MODEL = "gemini-3.5-flash"

    private fun ask(key: String, src: Bitmap): String = try {
        val scale = 1400f / maxOf(src.width, src.height)
        val bmp = if (scale < 1) Bitmap.createScaledBitmap(src, (src.width * scale).toInt(), (src.height * scale).toInt(), true) else src
        val out = ByteArrayOutputStream(); bmp.compress(Bitmap.CompressFormat.JPEG, 80, out)
        val b64 = Base64.encodeToString(out.toByteArray(), Base64.NO_WRAP)

        val prompt = "Это скриншот экрана телефона. Найди на нём вопрос и дай правильный ответ. " +
            "Если есть варианты ответа, назови правильный (букву/номер и текст). " +
            "Если ответ нужно вводить вручную, напиши, что именно ввести. " +
            "Отвечай кратко, на языке вопроса. " +
            "Если на экране нет вопроса, ответь ровно: Вопрос не найден."
        val body = JSONObject().put("contents", JSONArray().put(JSONObject().put("parts", JSONArray()
            .put(JSONObject().put("inline_data", JSONObject()
                .put("mime_type", "image/jpeg").put("data", b64)))
            .put(JSONObject().put("text", prompt)))))

        val c = URL("https://generativelanguage.googleapis.com/v1beta/models/$MODEL:generateContent")
            .openConnection() as HttpURLConnection
        c.requestMethod = "POST"; c.doOutput = true; c.connectTimeout = 15000; c.readTimeout = 60000
        c.setRequestProperty("x-goog-api-key", key)
        c.setRequestProperty("Content-Type", "application/json")
        c.outputStream.use { it.write(body.toString().toByteArray()) }
        val stream = if (c.responseCode in 200..299) c.inputStream else c.errorStream
        val resp = stream.bufferedReader().readText()
        if (c.responseCode == 429) "Лимит бесплатных запросов исчерпан. Подождите минуту или до завтра."
        else if (c.responseCode !in 200..299) "Ошибка ${c.responseCode}: $resp"
        else JSONObject(resp).getJSONArray("candidates").getJSONObject(0)
            .getJSONObject("content").getJSONArray("parts").getJSONObject(0).getString("text")
    } catch (e: Exception) { "Ошибка: ${e.message}" }

    private fun showText(t: String) {
        closePanel()
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; setPadding(32, 24, 32, 24)
            setBackgroundColor(Color.parseColor("#F2222222"))
        }
        box.addView(TextView(this).apply {
            text = t; setTextColor(Color.WHITE); textSize = 17f; maxWidth = (resources.displayMetrics.widthPixels * 0.85).toInt()
        })
        box.addView(TextView(this).apply {
            text = "✕ закрыть"; setTextColor(Color.parseColor("#90CAF9")); setPadding(0, 20, 0, 0)
            setOnClickListener { closePanel() }
        })
        panel = box
        wm.addView(box, params(Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL).apply { y = 120 })
    }
}
