package com.monv.asisten

import android.content.Context
import android.graphics.Color
import android.graphics.drawable.GradientDrawable

object Pal {
    val CYAN = Color.parseColor("#4DE1FF")
    val VIOLET = Color.parseColor("#8A5CFF")
    val PINK = Color.parseColor("#FF4D8D")
    val GREEN = Color.parseColor("#7CFFB2")
    val DARK = Color.parseColor("#0B1426")
}

fun Context.dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()
fun Context.dpf(v: Float): Float = v * resources.displayMetrics.density

fun Context.rounded(fill: Int, radiusDp: Float, strokeDp: Int = 0, stroke: Int = 0): GradientDrawable =
    GradientDrawable().apply {
        setColor(fill)
        cornerRadius = dpf(radiusDp)
        if (strokeDp > 0) setStroke(dp(strokeDp), stroke)
    }
