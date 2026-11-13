package com.monv.asisten

import android.animation.ArgbEvaluator
import android.animation.ValueAnimator
import android.app.*
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.Icon
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.provider.Settings
import android.text.InputType
import android.transition.AutoTransition
import android.transition.TransitionManager
import android.view.*
import android.view.animation.DecelerateInterpolator
import android.view.animation.OvershootInterpolator
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import kotlin.math.hypot

/** Asisten melayang ala "dynamic island": bubble kecil -> tap -> mengembang jadi panel perintah. */
class OverlayService : Service() {

    companion object {
        @Volatile var running = false
        val handler = Handler(Looper.getMainLooper())
        const val ACTION_STOP = "com.monv.asisten.STOP"
        private const val CH = "monv_channel"
    }

    private lateinit var wm: WindowManager
    private lateinit var lp: WindowManager.LayoutParams
    private lateinit var root: LinearLayout
    private lateinit var card: LinearLayout
    private lateinit var robot: RobotView
    private lateinit var status: TextView
    private lateinit var input: EditText
    private lateinit var inputRow: LinearLayout
    private lateinit var hideBtn: TextView
    private lateinit var island: TextView
    private val bg = GradientDrawable()
    private var strokeAnim: ValueAnimator? = null
    private var snapAnim: ValueAnimator? = null
    private var expanded = false
    private var savedY = -1
    private var built = false
    private val sw get() = resources.displayMetrics.widthPixels
    private val sh get() = resources.displayMetrics.heightPixels

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) { stopSelf(); return START_NOT_STICKY }
        if (!Settings.canDrawOverlays(this)) { stopSelf(); return START_NOT_STICKY }
        startFg()
        if (!built) { build(); built = true }
        running = true
        return START_STICKY
    }

    private fun startFg() {
        val nm = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        nm.createNotificationChannel(NotificationChannel(CH, "Monv Asisten", NotificationManager.IMPORTANCE_LOW))
        val stop = PendingIntent.getService(this, 1, Intent(this, OverlayService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val open = PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE)
        val n = Notification.Builder(this, CH)
            .setSmallIcon(R.drawable.ic_stat)
            .setContentTitle("Monv Asisten aktif")
            .setContentText("Asisten melayang siap dipakai")
            .setContentIntent(open).setOngoing(true)
            .addAction(Notification.Action.Builder(Icon.createWithResource(this, R.drawable.ic_stat), "Matikan", stop).build())
            .build()
        if (Build.VERSION.SDK_INT >= 29) startForeground(1, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        else startForeground(1, n)
    }

    // ---------------------------------------------------------------- UI
    private fun build() {
        wm = getSystemService(WINDOW_SERVICE) as WindowManager
        lp = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT, WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
            PixelFormat.TRANSLUCENT
        ).apply { gravity = Gravity.TOP or Gravity.START; x = sw - dp(84); y = dp(220) }

        root = object : LinearLayout(this@OverlayService) {
            override fun dispatchKeyEvent(e: KeyEvent): Boolean {
                if (e.keyCode == KeyEvent.KEYCODE_BACK && e.action == KeyEvent.ACTION_UP) { releaseFocus(); return true }
                return super.dispatchKeyEvent(e)
            }
        }.apply { orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER_HORIZONTAL }

        card = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER_HORIZONTAL }

        hideBtn = TextView(this).apply {
            text = "HIDE"; textSize = 10f; setTextColor(Color.parseColor("#FF6B8A")); typeface = android.graphics.Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER; setPadding(dp(14), dp(3), dp(14), dp(3)); letterSpacing = 0.15f
            background = rounded(Color.parseColor("#33FF4D6D"), 12f, 1, Color.parseColor("#FF4D6D"))
            visibility = View.GONE
            setOnClickListener { collapse() }
        }
        robot = RobotView(this).apply { layoutParams = LinearLayout.LayoutParams(dp(70), dp(70)) }
        status = TextView(this).apply {
            text = "Halo, saya Monv"; textSize = 12f; setTextColor(Color.WHITE); gravity = Gravity.CENTER
            maxWidth = dp(230); setPadding(dp(8), dp(2), dp(8), dp(8)); visibility = View.GONE
        }
        input = EditText(this).apply {
            hint = "Perintah..."; textSize = 14f; setTextColor(Color.WHITE); setHintTextColor(Color.parseColor("#8896B3"))
            setSingleLine(true); imeOptions = EditorInfo.IME_ACTION_GO; inputType = InputType.TYPE_CLASS_TEXT
            background = rounded(Color.parseColor("#1A2547"), 14f, 1, Color.parseColor("#4DE1FF"))
            setPadding(dp(12), dp(8), dp(12), dp(8))
            setOnClickListener { focusInput() }
            setOnEditorActionListener { _, _, _ -> submit(); true }
        }
        val go = TextView(this).apply {
            text = "GO"; textSize = 13f; setTextColor(Pal.DARK); typeface = android.graphics.Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER; setPadding(dp(16), dp(8), dp(16), dp(8))
            background = rounded(Pal.CYAN, 14f)
            setOnClickListener { submit() }
        }
        inputRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL; visibility = View.GONE
            addView(input, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
            addView(go, LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { leftMargin = dp(8) })
        }
        island = TextView(this).apply {
            textSize = 12f; setTextColor(Color.WHITE); gravity = Gravity.CENTER; maxWidth = dp(230)
            setPadding(dp(14), dp(8), dp(14), dp(8)); visibility = View.GONE
            background = rounded(Color.parseColor("#F00B1426"), 18f, 1, Pal.CYAN)
        }

        card.addView(hideBtn, LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT))
        card.addView(robot)
        card.addView(status, LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT))
        card.addView(inputRow, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT))
        root.addView(card)
        root.addView(island, LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { topMargin = dp(4) })

        robot.setOnTouchListener(Drag { if (!expanded) expand() else bounce() })
        card.setOnTouchListener(Drag { })

        bg.orientation = GradientDrawable.Orientation.TL_BR
        bg.colors = intArrayOf(Color.parseColor("#F00B1426"), Color.parseColor("#F01A1B4A"))
        bg.cornerRadius = dpf(26f)

        wm.addView(root, lp)
        root.alpha = 0f; root.scaleX = 0.5f; root.scaleY = 0.5f
        root.animate().alpha(1f).scaleX(1f).scaleY(1f).setDuration(450).setInterpolator(OvershootInterpolator(1.6f)).start()
        say("Halo, saya Monv 👋")
    }

    private inner class Drag(val onTap: () -> Unit) : View.OnTouchListener {
        var sx = 0f; var sy = 0f; var ox = 0; var oy = 0; var moved = false
        override fun onTouch(v: View, e: MotionEvent): Boolean {
            when (e.actionMasked) {
                MotionEvent.ACTION_DOWN -> { sx = e.rawX; sy = e.rawY; ox = lp.x; oy = lp.y; moved = false; snapAnim?.cancel() }
                MotionEvent.ACTION_MOVE -> {
                    val dx = e.rawX - sx; val dy = e.rawY - sy
                    if (!moved && hypot(dx, dy) > dp(6)) moved = true
                    if (moved) { lp.x = (ox + dx).toInt(); lp.y = (oy + dy).toInt(); safeUpdate() }
                }
                MotionEvent.ACTION_UP -> { if (!moved) onTap() else if (!expanded) snapToEdge() }
            }
            return true
        }
    }

    private fun safeUpdate() { try { wm.updateViewLayout(root, lp) } catch (_: Exception) {} }

    private fun bounce() {
        robot.animate().scaleX(1.15f).scaleY(1.15f).setDuration(120).withEndAction {
            robot.animate().scaleX(1f).scaleY(1f).setDuration(220).setInterpolator(OvershootInterpolator(3f)).start()
        }.start()
    }

    private fun morph() {
        TransitionManager.beginDelayedTransition(card, AutoTransition().setDuration(340).setInterpolator(OvershootInterpolator(1.0f)))
    }

    private fun expand() {
        expanded = true
        island.visibility = View.GONE
        morph()
        hideBtn.visibility = View.VISIBLE; status.visibility = View.VISIBLE; inputRow.visibility = View.VISIBLE
        robot.layoutParams = LinearLayout.LayoutParams(dp(86), dp(86))
        card.background = bg
        card.setPadding(dp(14), dp(12), dp(14), dp(14))
        card.minimumWidth = dp(250)
        val maxX = sw - dp(250) - dp(8)
        if (lp.x > maxX) { lp.x = maxX; }
        if (lp.x < dp(4)) lp.x = dp(4)
        safeUpdate()
        strokeAnim?.cancel()
        strokeAnim = ValueAnimator.ofObject(ArgbEvaluator(), Pal.CYAN, Pal.VIOLET).apply {
            duration = 1800; repeatCount = ValueAnimator.INFINITE; repeatMode = ValueAnimator.REVERSE
            addUpdateListener { bg.setStroke(dp(2), it.animatedValue as Int) }
            start()
        }
        card.scaleX = 0.9f; card.scaleY = 0.9f
        card.animate().scaleX(1f).scaleY(1f).setDuration(380).setInterpolator(OvershootInterpolator(1.5f)).start()
        say("Halo, saya Monv. Ketik 'menu' untuk daftar perintah.")
    }

    private fun collapse() {
        releaseFocus()
        expanded = false
        strokeAnim?.cancel()
        morph()
        hideBtn.visibility = View.GONE; status.visibility = View.GONE; inputRow.visibility = View.GONE
        robot.layoutParams = LinearLayout.LayoutParams(dp(70), dp(70))
        card.background = null
        card.setPadding(0, 0, 0, 0)
        card.minimumWidth = 0
        handler.postDelayed({ snapToEdge() }, 380)
    }

    private fun snapToEdge() {
        val w = root.width.takeIf { it > 0 } ?: dp(70)
        val target = if (lp.x + w / 2 < sw / 2) dp(4) else sw - w - dp(4)
        val maxY = (sh - root.height - dp(24)).coerceAtLeast(dp(24))
        lp.y = lp.y.coerceIn(dp(24), maxY)
        snapAnim?.cancel()
        snapAnim = ValueAnimator.ofInt(lp.x, target).apply {
            duration = 240; interpolator = DecelerateInterpolator()
            addUpdateListener { lp.x = it.animatedValue as Int; safeUpdate() }
            start()
        }
    }

    // ---------------------------------------------------------------- input / fokus keyboard
    private fun focusInput() {
        if (lp.flags and WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE != 0) {
            lp.flags = lp.flags and WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE.inv()
            savedY = lp.y; lp.y = dp(90); safeUpdate()
        }
        handler.postDelayed({
            input.requestFocus()
            (getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager).showSoftInput(input, InputMethodManager.SHOW_IMPLICIT)
        }, 120)
    }

    private fun releaseFocus() {
        if (!built) return
        (getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager).hideSoftInputFromWindow(input.windowToken, 0)
        input.clearFocus()
        lp.flags = lp.flags or WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
        if (savedY >= 0) { lp.y = savedY; savedY = -1 }
        safeUpdate()
    }

    private fun submit() {
        val t = input.text.toString().trim()
        if (t.isEmpty()) return
        input.setText("")
        releaseFocus()
        say("⏳ $t", 1)
        handler.postDelayed({ Commands.run(this, t) { r -> say(r, 0) } }, 400)
    }

    /** Dipanggil Commands sebelum screenshot supaya overlay tidak ikut terfoto. */
    fun hideForShot() {
        root.visibility = View.INVISIBLE
        handler.postDelayed({ root.visibility = View.VISIBLE }, 1600)
    }

    // ---------------------------------------------------------------- status / island
    fun say(t: String, mood: Int = 0) {
        handler.post {
            if (!built) return@post
            status.text = t
            robot.mood = mood
            status.alpha = 0f; status.animate().alpha(1f).setDuration(220).start()
            if (!expanded) showIsland(t)
        }
    }

    private val hideIsland = Runnable {
        island.animate().alpha(0f).scaleX(0.7f).scaleY(0.7f).setDuration(220).withEndAction {
            island.visibility = View.GONE
            if (!expanded) snapToEdge()
        }.start()
    }

    private fun showIsland(t: String) {
        island.text = t
        handler.removeCallbacks(hideIsland)
        if (island.visibility != View.VISIBLE) {
            island.alpha = 0f; island.scaleX = 0.6f; island.scaleY = 0.6f; island.visibility = View.VISIBLE
        }
        island.animate().alpha(1f).scaleX(1f).scaleY(1f).setDuration(300).setInterpolator(OvershootInterpolator(1.8f)).start()
        root.post {
            if (lp.x + root.width > sw) { lp.x = sw - root.width - dp(4); safeUpdate() }
        }
        handler.postDelayed(hideIsland, 3200)
    }

    override fun onDestroy() {
        running = false
        handler.removeCallbacksAndMessages(null)
        strokeAnim?.cancel(); snapAnim?.cancel()
        if (built) { try { wm.removeView(root) } catch (_: Exception) {} }
        built = false
        super.onDestroy()
    }
}
