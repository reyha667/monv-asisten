package com.monv.asisten

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.content.Intent
import android.graphics.Path
import android.graphics.Rect
import android.os.Bundle
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo

/** "Tangan" Monv: ketik, tap, scroll, screenshot, back/home/recent, kunci layar. */
class MonvAccessibilityService : AccessibilityService() {

    companion object {
        @Volatile var inst: MonvAccessibilityService? = null
    }

    override fun onServiceConnected() { super.onServiceConnected(); inst = this }
    override fun onUnbind(intent: Intent?): Boolean { inst = null; return super.onUnbind(intent) }
    override fun onDestroy() { inst = null; super.onDestroy() }
    override fun onAccessibilityEvent(event: AccessibilityEvent?) {}
    override fun onInterrupt() {}

    /** Semua window aplikasi lain (bukan overlay/aplikasi Monv sendiri). */
    private fun roots(): List<AccessibilityNodeInfo> {
        val list = ArrayList<AccessibilityNodeInfo>()
        try {
            for (w in windows) {
                val r = w.root ?: continue
                if (r.packageName?.toString() != packageName) list.add(r)
            }
        } catch (_: Exception) {}
        if (list.isEmpty()) {
            rootInActiveWindow?.let { if (it.packageName?.toString() != packageName) list.add(it) }
        }
        return list
    }

    fun global(action: Int): Boolean = performGlobalAction(action)

    fun tapText(t: String): Boolean {
        for (r in roots()) {
            val nodes = r.findAccessibilityNodeInfosByText(t) ?: continue
            for (n in nodes) {
                var cur: AccessibilityNodeInfo? = n
                var depth = 0
                while (cur != null && depth < 8) {
                    if (cur.isClickable && cur.isEnabled && cur.performAction(AccessibilityNodeInfo.ACTION_CLICK)) return true
                    cur = cur.parent; depth++
                }
                val b = Rect(); n.getBoundsInScreen(b)
                if (b.width() > 0 && b.height() > 0) { tap(b.centerX().toFloat(), b.centerY().toFloat()); return true }
            }
        }
        return false
    }

    fun typeText(t: String): Boolean {
        for (r in roots()) {
            val n = r.findFocus(AccessibilityNodeInfo.FOCUS_INPUT) ?: findEditable(r) ?: continue
            val cur = if (n.isShowingHintText) "" else (n.text?.toString() ?: "")
            val b = Bundle()
            b.putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, cur + t)
            if (n.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, b)) return true
        }
        return false
    }

    private fun findEditable(n: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        if (n.isEditable) return n
        for (i in 0 until n.childCount) {
            val c = n.getChild(i) ?: continue
            findEditable(c)?.let { return it }
        }
        return null
    }

    fun tap(x: Float, y: Float) {
        val p = Path().apply { moveTo(x, y) }
        dispatchGesture(GestureDescription.Builder().addStroke(GestureDescription.StrokeDescription(p, 0, 60)).build(), null, null)
    }

    private fun swipe(x1: Float, y1: Float, x2: Float, y2: Float, ms: Long) {
        val p = Path().apply { moveTo(x1, y1); lineTo(x2, y2) }
        dispatchGesture(GestureDescription.Builder().addStroke(GestureDescription.StrokeDescription(p, 0, ms)).build(), null, null)
    }

    fun scroll(down: Boolean) {
        val dm = resources.displayMetrics
        val w = dm.widthPixels.toFloat(); val h = dm.heightPixels.toFloat()
        if (down) swipe(w / 2, h * 0.72f, w / 2, h * 0.28f, 320) else swipe(w / 2, h * 0.28f, w / 2, h * 0.72f, 320)
    }
}
