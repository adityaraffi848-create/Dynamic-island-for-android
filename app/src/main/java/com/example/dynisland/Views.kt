package com.example.dynisland

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.view.MotionEvent
import android.view.View

/** Cincin progres (dipakai untuk baterai dan timer). */
class RingView(c: Context) : View(c) {
    var progress = 0f
    var color = 0xFF30D158.toInt()
    var stroke = 3f * resources.displayMetrics.density
    private val p = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
    }
    private val r = RectF()

    fun set(pr: Float, col: Int) { progress = pr; color = col; invalidate() }

    override fun onDraw(cv: Canvas) {
        val h = stroke / 2f
        r.set(h, h, width - h, height - h)
        p.strokeWidth = stroke
        p.color = 0x33FFFFFF
        cv.drawOval(r, p)
        p.color = color
        cv.drawArc(r, -90f, 360f * progress.coerceIn(0f, 1f), false, p)
    }
}

/** Bar progres / slider (musik, kecerahan, volume, HUD). */
class BarView(c: Context, var fill: Int = Color.WHITE) : View(c) {
    var value = 0f
        set(v) { field = v.coerceIn(0f, 1f); invalidate() }
    var touching = false
    var interactive = false
    var onChange: ((Float, Boolean) -> Unit)? = null
    private val p = Paint(Paint.ANTI_ALIAS_FLAG)
    private val th = 6f * resources.displayMetrics.density

    override fun onDraw(cv: Canvas) {
        val t = minOf(th, height.toFloat())
        val top = (height - t) / 2f
        val r = t / 2f
        p.color = 0x33FFFFFF
        cv.drawRoundRect(0f, top, width.toFloat(), top + t, r, r, p)
        p.color = fill
        cv.drawRoundRect(0f, top, width * value, top + t, r, r, p)
    }

    override fun onTouchEvent(e: MotionEvent): Boolean {
        if (!interactive) return false
        when (e.action) {
            MotionEvent.ACTION_DOWN -> {
                touching = true
                parent?.requestDisallowInterceptTouchEvent(true)
                upd(e, false)
            }
            MotionEvent.ACTION_MOVE -> upd(e, false)
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> { upd(e, true); touching = false }
        }
        return true
    }

    private fun upd(e: MotionEvent, fin: Boolean) {
        if (width <= 0) return
        value = e.x / width
        onChange?.invoke(value, fin)
    }
}
