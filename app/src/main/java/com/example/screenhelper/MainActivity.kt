package com.example.screenhelper

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.text.InputType
import android.widget.*

class MainActivity : Activity() {
    override fun onCreate(s: Bundle?) {
        super.onCreate(s)
        val prefs = getSharedPreferences("p", MODE_PRIVATE)
        val l = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(48, 96, 48, 48) }
        val key = EditText(this).apply {
            hint = "Ключ Gemini (из aistudio.google.com)"; setText(prefs.getString("key", ""))
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
        }
        fun btn(t: String, f: () -> Unit) = Button(this).apply { text = t; setOnClickListener { f() } }
        l.addView(key)
        l.addView(btn("Сохранить ключ") { prefs.edit().putString("key", key.text.toString().trim()).apply(); toast("Сохранено") })
        l.addView(btn("1. Включить доступность (один раз)") { startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) })
        l.addView(btn("2. ВКЛЮЧИТЬ помощника") {
            ScreenHelperService.instance?.startHelper() ?: toast("Сначала включите службу в Специальных возможностях")
        })
        l.addView(btn("ВЫКЛЮЧИТЬ помощника") { ScreenHelperService.instance?.stopHelper() })
        l.addView(TextView(this).apply { text = "\nКнопка «?» на экране: нажать = найти ответ, удерживать = выключить." })
        setContentView(l)
    }
    private fun toast(t: String) = Toast.makeText(this, t, Toast.LENGTH_LONG).show()
}
