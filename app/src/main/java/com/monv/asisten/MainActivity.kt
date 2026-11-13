package com.monv.asisten

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Shader
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.view.animation.OvershootInterpolator
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast

class MainActivity : Activity() {

    private inner class Row(title: String, desc: String, val check: () -> Boolean, val act: () -> Unit) {
        val view = LinearLayout(this@MainActivity)
        private val btn = TextView(this@MainActivity)
        init {
            view.orientation = LinearLayout.HORIZONTAL; view.gravity = Gravity.CENTER_VERTICAL
            view.setPadding(dp(16), dp(14), dp(14), dp(14))
            view.background = rounded(Color.parseColor("#14FFFFFF"), 20f, 1, Color.parseColor("#334DE1FF"))
            val col = LinearLayout(this@MainActivity); col.orientation = LinearLayout.VERTICAL
            col.addView(TextView(this@MainActivity).apply { text = title; textSize = 15f; setTextColor(Color.WHITE); typeface = Typeface.DEFAULT_BOLD })
            col.addView(TextView(this@MainActivity).apply { text = desc; textSize = 12f; setTextColor(Color.parseColor("#9AA8C7")); setPadding(0, dp(2), 0, 0) })
            view.addView(col, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
            btn.textSize = 12f; btn.typeface = Typeface.DEFAULT_BOLD; btn.gravity = Gravity.CENTER
            btn.setPadding(dp(14), dp(8), dp(14), dp(8))
            btn.setOnClickListener { act() }
            view.addView(btn, LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { leftMargin = dp(10) })
        }
        fun update() {
            val ok = check()
            btn.text = if (ok) "✓ Aktif" else "Aktifkan"
            btn.setTextColor(if (ok) Pal.GREEN else Pal.DARK)
            btn.background = if (ok) rounded(Color.parseColor("#223CFFA0"), 14f, 1, Pal.GREEN) else rounded(Pal.CYAN, 14f)
        }
    }

    private val rows = ArrayList<Row>()
    private lateinit var startBtn: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val page = GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, intArrayOf(Color.parseColor("#050816"), Color.parseColor("#0B1030"), Color.parseColor("#1A0B3D")))
        val scroll = ScrollView(this).apply { background = page; isFillViewport = true }
        val col = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(20), dp(44), dp(20), dp(32)) }
        scroll.addView(col)

        val logo = RobotView(this)
        col.addView(logo, LinearLayout.LayoutParams(dp(190), dp(190)).apply { gravity = Gravity.CENTER_HORIZONTAL })

        val title = TextView(this).apply { text = "Monv Asisten"; textSize = 32f; typeface = Typeface.DEFAULT_BOLD; gravity = Gravity.CENTER }
        title.post {
            val w = title.paint.measureText(title.text.toString())
            title.paint.shader = LinearGradient(0f, 0f, w, 0f, intArrayOf(Pal.CYAN, Pal.VIOLET, Pal.PINK), null, Shader.TileMode.CLAMP)
            title.invalidate()
        }
        col.addView(title)
        col.addView(TextView(this).apply {
            text = "Asisten melayang di layar HP kamu ✨\nBuka app, telepon, screenshot, ketik & tap — cukup ketik perintah."
            textSize = 13f; setTextColor(Color.parseColor("#9AA8C7")); gravity = Gravity.CENTER; setPadding(0, dp(6), 0, dp(20))
        })

        col.addView(section("LANGKAH AKTIVASI"))
        addRow(col, Row("1. Aksesibilitas", "Aksesibilitas → Monv Asisten → aktifkan", {
            val s = Settings.Secure.getString(contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES) ?: ""
            (s.contains(packageName) && s.contains("MonvAccessibilityService")) || MonvAccessibilityService.inst != null
        }) { startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) })
        addRow(col, Row("2. Overlay", "Izinkan tampil di atas aplikasi lain", { Settings.canDrawOverlays(this) }) {
            startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName")))
        })
        addRow(col, Row("3. Notifikasi", "Supaya Monv tetap hidup di background", {
            Build.VERSION.SDK_INT < 33 || checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
        }) { if (Build.VERSION.SDK_INT >= 33) requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 1) })
        addRow(col, Row("4. Kontak & Telepon", "Untuk perintah telepon & wa", {
            checkSelfPermission(Manifest.permission.READ_CONTACTS) == PackageManager.PERMISSION_GRANTED &&
                checkSelfPermission(Manifest.permission.CALL_PHONE) == PackageManager.PERMISSION_GRANTED
        }) { requestPermissions(arrayOf(Manifest.permission.READ_CONTACTS, Manifest.permission.CALL_PHONE), 2) })
        addRow(col, Row("5. Anti dimatikan sistem", "Abaikan optimasi baterai (biar tidak lag/mati)", {
            (getSystemService(POWER_SERVICE) as PowerManager).isIgnoringBatteryOptimizations(packageName)
        }) { startActivity(Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:$packageName"))) })

        col.addView(TextView(this).apply {
            text = "Tombol Aksesibilitas abu-abu (Android 13+)? Ketuk di sini → ⋮ → \"Izinkan setelan terbatas\", lalu ulangi langkah 1."
            textSize = 11f; setTextColor(Pal.CYAN); setPadding(dp(4), dp(8), dp(4), dp(16))
            setOnClickListener { startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$packageName"))) }
        })

        startBtn = TextView(this).apply {
            textSize = 17f; typeface = Typeface.DEFAULT_BOLD; gravity = Gravity.CENTER; setTextColor(Pal.DARK)
            setPadding(dp(20), dp(16), dp(20), dp(16))
            setOnClickListener { toggleService() }
        }
        col.addView(startBtn, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT))

        col.addView(section("DAFTAR PERINTAH"))
        col.addView(TextView(this).apply {
            text = Commands.HELP.replace("MENU MONV:\n", "")
            textSize = 13f; setTextColor(Color.parseColor("#D6E2FF")); setLineSpacing(0f, 1.3f)
            setPadding(dp(16), dp(14), dp(16), dp(14))
            background = rounded(Color.parseColor("#14FFFFFF"), 20f, 1, Color.parseColor("#334DE1FF"))
        })

        setContentView(scroll)

        for (i in 0 until col.childCount) {
            val v = col.getChildAt(i)
            v.alpha = 0f; v.translationY = dp(30).toFloat()
            v.animate().alpha(1f).translationY(0f).setStartDelay(i * 70L).setDuration(500).setInterpolator(OvershootInterpolator(1.2f)).start()
        }
    }

    private fun section(t: String) = TextView(this).apply {
        text = t; textSize = 11f; setTextColor(Pal.VIOLET); typeface = Typeface.DEFAULT_BOLD; letterSpacing = 0.2f
        setPadding(dp(4), dp(8), 0, dp(8))
    }

    private fun addRow(parent: LinearLayout, r: Row) {
        rows.add(r)
        parent.addView(r.view, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { bottomMargin = dp(10) })
    }

    private fun toggleService() {
        if (OverlayService.running) {
            startService(Intent(this, OverlayService::class.java).setAction(OverlayService.ACTION_STOP))
        } else {
            if (!Settings.canDrawOverlays(this)) { Toast.makeText(this, "Aktifkan izin Overlay dulu (langkah 2)", Toast.LENGTH_LONG).show(); return }
            if (MonvAccessibilityService.inst == null) Toast.makeText(this, "Aksesibilitas belum aktif: tap/ketik/scroll belum jalan", Toast.LENGTH_LONG).show()
            startForegroundService(Intent(this, OverlayService::class.java))
        }
        startBtn.postDelayed({ refresh() }, 500)
    }

    private fun refresh() {
        rows.forEach { it.update() }
        val on = OverlayService.running
        startBtn.text = if (on) "⏹  Hentikan Monv" else "🚀  Jalankan Monv"
        startBtn.background = GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT,
            if (on) intArrayOf(Pal.PINK, Color.parseColor("#FF8A4D")) else intArrayOf(Pal.CYAN, Pal.VIOLET)).apply { cornerRadius = dpf(24f) }
    }

    override fun onResume() { super.onResume(); refresh() }
    override fun onRequestPermissionsResult(c: Int, p: Array<out String>, r: IntArray) { super.onRequestPermissionsResult(c, p, r); refresh() }
}
