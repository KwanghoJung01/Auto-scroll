package com.jace.autoscroll

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.content.Intent
import android.graphics.Path
import android.graphics.Point
import android.os.Build
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import kotlin.random.Random

/**
 * 실제로 화면을 쓸어내리는 쪽. 접근성 권한을 받아 [dispatchGesture] 로
 * 사람이 손가락을 끄는 것과 같은 제스처를 만들어 보낸다.
 */
class AutoScrollService : AccessibilityService() {

    override fun onServiceConnected() {
        super.onServiceConnected()
        ScrollController.service = this
    }

    override fun onUnbind(intent: Intent?): Boolean {
        ScrollController.service = null
        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        ScrollController.service = null
        super.onDestroy()
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) = Unit

    override fun onInterrupt() {
        ScrollController.stop()
    }

    /**
     * 한 번 쓸어내린다.
     *
     * @param direction 이번 스와이프가 향하는 쪽. [ScrollDirection.BOUNCE] 가 아니라
     *   왕복 중이라면 지금 구간의 방향(DOWN/UP)을 넘겨야 한다.
     * @param distancePercent 화면 높이 대비 이동 거리(%)
     * @param durationMs 끄는 데 걸리는 시간. 길수록 천천히 움직인다.
     */
    fun swipe(direction: ScrollDirection, distancePercent: Int, durationMs: Int): Boolean {
        val size = screenSize()
        if (size.x <= 0 || size.y <= 0) return false

        val distance = size.y * distancePercent / 100f
        // 손가락을 화면 한가운데쯤에 두되, 매번 조금씩 다른 위치에서 시작한다.
        val spread = (size.x / 12).coerceAtLeast(1)
        val x = size.x / 2f + Random.nextInt(-spread, spread + 1)

        val margin = size.y * 0.12f
        val startY: Float
        val endY: Float
        if (direction == ScrollDirection.UP) {
            startY = margin
            endY = (startY + distance).coerceAtMost(size.y - margin)
        } else {
            // 손가락을 위로 → 내용이 아래로 넘어간다. (왕복도 여기로 들어오지 않는다)
            startY = (size.y - margin).coerceAtMost(size.y * 0.88f)
            endY = (startY - distance).coerceAtLeast(margin)
        }
        if (kotlin.math.abs(endY - startY) < 10f) return false

        val path = Path().apply {
            moveTo(x, startY)
            // 중간 점을 하나 넣어 직선 대신 살짝 휜 궤적을 만든다.
            quadTo(x + Random.nextInt(-20, 21), (startY + endY) / 2f, x, endY)
        }

        val stroke = GestureDescription.StrokeDescription(path, 0, durationMs.toLong())
        val gesture = GestureDescription.Builder().addStroke(stroke).build()
        return dispatchGesture(gesture, null, null)
    }

    private fun screenSize(): Point {
        val wm = getSystemService(WindowManager::class.java)
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && wm != null) {
            val bounds = wm.currentWindowMetrics.bounds
            Point(bounds.width(), bounds.height())
        } else {
            val dm = resources.displayMetrics
            Point(dm.widthPixels, dm.heightPixels)
        }
    }
}
