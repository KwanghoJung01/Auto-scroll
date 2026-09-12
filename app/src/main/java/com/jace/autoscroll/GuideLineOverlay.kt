package com.jace.autoscroll

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.PixelFormat
import android.graphics.Point
import android.os.Build
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import kotlin.math.roundToInt

/**
 * 화면을 가로지르는 기준선. 스크롤이 올라가는 동안 "지금 어디를 보면 되는지"
 * 눈으로 잡아두기 위한 것이다.
 *
 * 줄 하나는 두 개의 창으로 만든다.
 *  - 선 자체: 화면 전체 너비, 터치를 통과시켜(FLAG_NOT_TOUCHABLE) 스와이프를 방해하지 않는다.
 *  - 손잡이: 오른쪽 끝의 작은 원. 이것만 터치를 받아 위아래로 끌 수 있다.
 *    스와이프는 화면 가운데를 지나가므로 손잡이와 부딪히지 않는다.
 */
class GuideLineOverlay(private val context: Context) {

    /** 줄을 끌어 놓았을 때 (몇 번째 줄, 화면 높이 대비 %) 로 알려준다. */
    var onMoved: ((index: Int, percent: Int) -> Unit)? = null

    private val windowManager = context.getSystemService(WindowManager::class.java)
    private val density = context.resources.displayMetrics.density
    private val lines = mutableListOf<Line>()

    private class Line(
        val index: Int,
        val lineView: View,
        val lineParams: WindowManager.LayoutParams,
        val handleView: View,
        val handleParams: WindowManager.LayoutParams,
    )

    private val lineHeight get() = (6 * density).toInt()
    private val handleSize get() = (44 * density).toInt()

    /** 원하는 위치 목록에 맞춰 줄을 다시 그린다. 빈 목록이면 모두 치운다. */
    fun show(percents: List<Int>) {
        if (percents.size != lines.size) {
            clear()
            percents.forEachIndexed { index, percent -> addLine(index, percent) }
            return
        }
        percents.forEachIndexed { index, percent -> moveTo(lines[index], toY(percent)) }
    }

    fun clear() {
        lines.forEach { line ->
            runCatching { windowManager?.removeView(line.lineView) }
            runCatching { windowManager?.removeView(line.handleView) }
        }
        lines.clear()
    }

    private fun screenSize(): Point {
        val wm = windowManager
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && wm != null) {
            val bounds = wm.currentWindowMetrics.bounds
            Point(bounds.width(), bounds.height())
        } else {
            val dm = context.resources.displayMetrics
            Point(dm.widthPixels, dm.heightPixels)
        }
    }

    private fun toY(percent: Int): Int =
        (screenSize().y * percent.coerceIn(2, 98) / 100f).roundToInt()

    private fun toPercent(y: Int): Int =
        ((y.toFloat() / screenSize().y) * 100f).roundToInt().coerceIn(2, 98)

    @SuppressLint("ClickableViewAccessibility")
    private fun addLine(index: Int, percent: Int) {
        val wm = windowManager ?: return
        val inflater = LayoutInflater.from(context)
        val y = toY(percent)
        val size = screenSize()

        val lineView = inflater.inflate(R.layout.guide_line, null)
        val lineParams = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            lineHeight,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = 0
            this.y = y - lineHeight / 2
        }

        val handleView = inflater.inflate(R.layout.guide_handle, null)
        val handleParams = WindowManager.LayoutParams(
            handleSize,
            handleSize,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = size.x - handleSize - (6 * density).toInt()
            this.y = y - handleSize / 2
        }

        if (runCatching { wm.addView(lineView, lineParams) }.isFailure) return
        if (runCatching { wm.addView(handleView, handleParams) }.isFailure) {
            runCatching { wm.removeView(lineView) }
            return
        }

        val line = Line(index, lineView, lineParams, handleView, handleParams)
        handleView.setOnTouchListener(DragListener(line))
        lines.add(line)
    }

    private fun moveTo(line: Line, y: Int) {
        line.lineParams.y = y - lineHeight / 2
        line.handleParams.y = y - handleSize / 2
        runCatching { windowManager?.updateViewLayout(line.lineView, line.lineParams) }
        runCatching { windowManager?.updateViewLayout(line.handleView, line.handleParams) }
    }

    private inner class DragListener(private val line: Line) : View.OnTouchListener {
        private var startY = 0
        private var touchY = 0f

        override fun onTouch(v: View, event: MotionEvent): Boolean {
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    startY = line.handleParams.y + handleSize / 2
                    touchY = event.rawY
                    v.alpha = 1f
                    return true
                }
                MotionEvent.ACTION_MOVE -> {
                    val y = (startY + (event.rawY - touchY)).toInt()
                        .coerceIn(0, screenSize().y)
                    moveTo(line, y)
                    return true
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    val y = line.handleParams.y + handleSize / 2
                    onMoved?.invoke(line.index, toPercent(y))
                    return true
                }
            }
            return false
        }
    }
}
