package com.monv.asisten

import android.animation.TimeInterpolator
import android.animation.ValueAnimator
import android.content.Context
import android.graphics.*
import android.view.View
import android.view.animation.AccelerateDecelerateInterpolator
import android.view.animation.LinearInterpolator
import kotlin.math.PI
import kotlin.math.min
import kotlin.math.sin

/** Logo / maskot Monv: robot melayang + aura berdenyut + kedip mata. Digambar dengan Canvas (tanpa gambar eksternal). */
class RobotView(ctx: Context) : View(ctx) {

    /** 0 = idle (cyan), 1 = bekerja (hijau) */
    var mood = 0
        set(v) { field = v; invalidate() }

    private var pulse = 0f
    private var bob = 0f
    private var blink = 1f
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val aura = Paint(Paint.ANTI_ALIAS_FLAG)
    private val rect = RectF()
    private val anims = ArrayList<ValueAnimator>()

    private val headShader = LinearGradient(0f, 22f, 0f, 52f, Color.WHITE, Color.parseColor("#BFD9FF"), Shader.TileMode.CLAMP)
    private val bodyShader = LinearGradient(0f, 56f, 0f, 84f, Pal.CYAN, Color.parseColor("#3A7BFF"), Shader.TileMode.CLAMP)

    override fun onSizeChanged(w: Int, h: Int, ow: Int, oh: Int) {
        val r = min(w, h) / 2f
        aura.shader = RadialGradient(
            w / 2f, h / 2f, r,
            intArrayOf(Color.parseColor("#994DE1FF"), Color.parseColor("#334DE1FF"), Color.parseColor("#008A5CFF")),
            floatArrayOf(0.3f, 0.65f, 1f), Shader.TileMode.CLAMP
        )
    }

    private fun loop(ms: Long, reverse: Boolean, ip: TimeInterpolator, cb: (Float) -> Unit) {
        val a = ValueAnimator.ofFloat(0f, 1f)
        a.duration = ms
        a.repeatCount = ValueAnimator.INFINITE
        a.repeatMode = if (reverse) ValueAnimator.REVERSE else ValueAnimator.RESTART
        a.interpolator = ip
        a.addUpdateListener { cb(it.animatedValue as Float); postInvalidateOnAnimation() }
        anims.add(a); a.start()
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        loop(1700, true, AccelerateDecelerateInterpolator()) { pulse = it }
        loop(1400, true, AccelerateDecelerateInterpolator()) { bob = it * 2f - 1f }
        loop(3600, false, LinearInterpolator()) { t ->
            blink = if (t > 0.92f) 1f - 0.9f * sin(PI * ((t - 0.92f) / 0.08f)).toFloat() else 1f
        }
    }

    override fun onDetachedFromWindow() {
        anims.forEach { it.cancel() }; anims.clear()
        super.onDetachedFromWindow()
    }

    private fun rr(l: Float, t: Float, r: Float, b: Float, rad: Float, c: Canvas) {
        rect.set(l, t, r, b); c.drawRoundRect(rect, rad, rad, paint)
    }

    override fun onDraw(c: Canvas) {
        val w = width.toFloat(); val h = height.toFloat()
        if (w <= 0f || h <= 0f) return
        val s = 0.88f + 0.14f * pulse
        c.save(); c.scale(s, s, w / 2, h / 2); c.drawRect(0f, 0f, w, h, aura); c.restore()

        val u = min(w, h) / 100f * 0.66f
        c.save()
        c.translate(w / 2 - 50 * u, h / 2 - 50 * u + bob * u * 3f)
        c.scale(u, u)
        val eye = if (mood == 1) Pal.GREEN else Pal.CYAN

        // antena
        paint.shader = null; paint.style = Paint.Style.STROKE; paint.strokeWidth = 2f; paint.color = eye
        c.drawLine(50f, 16f, 50f, 24f, paint)
        paint.style = Paint.Style.FILL; paint.color = Pal.PINK; paint.alpha = (150 + 105 * pulse).toInt()
        c.drawCircle(50f, 13f, 3.4f, paint); paint.alpha = 255
        // telinga
        paint.color = eye
        rr(20f, 33f, 26f, 45f, 3f, c); rr(74f, 33f, 80f, 45f, 3f, c)
        // tangan
        c.drawCircle(28f, 68f, 5f, paint); c.drawCircle(72f, 68f, 5f, paint)
        // badan
        paint.shader = bodyShader; rr(33f, 56f, 67f, 86f, 11f, c); paint.shader = null
        paint.color = Color.WHITE; paint.alpha = (140 + 115 * pulse).toInt(); c.drawCircle(50f, 70f, 4.2f, paint); paint.alpha = 255
        // kepala
        paint.shader = headShader; rr(26f, 22f, 74f, 54f, 13f, c); paint.shader = null
        // visor
        paint.color = Pal.DARK; rr(31f, 29f, 69f, 47f, 8f, c)
        // mata
        paint.color = eye
        rect.set(38.6f, 38f - 3.6f * blink, 45.4f, 38f + 3.6f * blink); c.drawOval(rect, paint)
        rect.set(54.6f, 38f - 3.6f * blink, 61.4f, 38f + 3.6f * blink); c.drawOval(rect, paint)
        c.restore()
    }
}
