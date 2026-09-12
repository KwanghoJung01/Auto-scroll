package com.jace.autoscroll

import android.content.Context

/** 스크롤 방향. [DOWN] 은 손가락을 위로 쓸어 화면을 아래로 넘기는 동작이다. */
enum class ScrollDirection { DOWN, UP }

/**
 * 자동 스크롤 동작을 결정하는 값들.
 *
 * @param swipeDurationMs 한 번 쓸어내리는 데 걸리는 시간. 짧을수록 빠르다.
 * @param distancePercent 한 번에 이동하는 거리. 화면 높이 대비 퍼센트.
 * @param intervalMs 쓸어내린 뒤 다음 동작까지 쉬는 시간.
 * @param totalSeconds 전체 실행 시간(초). 0 이면 무제한.
 * @param startDelaySec 시작 버튼을 누른 뒤 첫 동작까지의 준비 시간.
 * @param randomize 사람 손처럼 보이도록 거리/시간을 매번 ±20% 흔들지 여부.
 */
data class ScrollConfig(
    val direction: ScrollDirection = ScrollDirection.DOWN,
    val swipeDurationMs: Int = 700,
    val distancePercent: Int = 45,
    val intervalMs: Int = 1200,
    val totalSeconds: Int = 60,
    val startDelaySec: Int = 5,
    val randomize: Boolean = true,
    val guideLineCount: Int = 0,
    val guidesVisible: Boolean = true,
    val guide1Percent: Int = 40,
    val guide2Percent: Int = 60,
) {
    val isUnlimited: Boolean get() = totalSeconds <= 0

    /** 화면에 실제로 그릴 가로줄들의 위치(화면 높이 대비 %). */
    fun visibleGuides(): List<Int> = when {
        !guidesVisible -> emptyList()
        guideLineCount >= 2 -> listOf(guide1Percent, guide2Percent)
        guideLineCount == 1 -> listOf(guide1Percent)
        else -> emptyList()
    }
}

object Prefs {
    private const val NAME = "auto_scroll_prefs"

    private const val KEY_DIRECTION = "direction"
    private const val KEY_DURATION = "swipe_duration"
    private const val KEY_DISTANCE = "distance_percent"
    private const val KEY_INTERVAL = "interval"
    private const val KEY_TOTAL = "total_seconds"
    private const val KEY_DELAY = "start_delay"
    private const val KEY_RANDOM = "randomize"
    private const val KEY_GUIDE_COUNT = "guide_count"
    private const val KEY_GUIDES_VISIBLE = "guides_visible"
    private const val KEY_GUIDE1 = "guide1_percent"
    private const val KEY_GUIDE2 = "guide2_percent"

    fun load(context: Context): ScrollConfig {
        val p = context.getSharedPreferences(NAME, Context.MODE_PRIVATE)
        val d = ScrollConfig()
        return ScrollConfig(
            direction = if (p.getString(KEY_DIRECTION, d.direction.name) == ScrollDirection.UP.name) {
                ScrollDirection.UP
            } else {
                ScrollDirection.DOWN
            },
            swipeDurationMs = p.getInt(KEY_DURATION, d.swipeDurationMs),
            distancePercent = p.getInt(KEY_DISTANCE, d.distancePercent),
            intervalMs = p.getInt(KEY_INTERVAL, d.intervalMs),
            totalSeconds = p.getInt(KEY_TOTAL, d.totalSeconds),
            startDelaySec = p.getInt(KEY_DELAY, d.startDelaySec),
            randomize = p.getBoolean(KEY_RANDOM, d.randomize),
            guideLineCount = p.getInt(KEY_GUIDE_COUNT, d.guideLineCount),
            guidesVisible = p.getBoolean(KEY_GUIDES_VISIBLE, d.guidesVisible),
            guide1Percent = p.getInt(KEY_GUIDE1, d.guide1Percent),
            guide2Percent = p.getInt(KEY_GUIDE2, d.guide2Percent),
        )
    }

    fun save(context: Context, config: ScrollConfig) {
        context.getSharedPreferences(NAME, Context.MODE_PRIVATE).edit()
            .putString(KEY_DIRECTION, config.direction.name)
            .putInt(KEY_DURATION, config.swipeDurationMs)
            .putInt(KEY_DISTANCE, config.distancePercent)
            .putInt(KEY_INTERVAL, config.intervalMs)
            .putInt(KEY_TOTAL, config.totalSeconds)
            .putInt(KEY_DELAY, config.startDelaySec)
            .putBoolean(KEY_RANDOM, config.randomize)
            .putInt(KEY_GUIDE_COUNT, config.guideLineCount)
            .putBoolean(KEY_GUIDES_VISIBLE, config.guidesVisible)
            .putInt(KEY_GUIDE1, config.guide1Percent)
            .putInt(KEY_GUIDE2, config.guide2Percent)
            .apply()
    }

    /** 가로줄을 끌어 옮겼을 때처럼, 한 값만 따로 저장한다. */
    fun saveGuidePosition(context: Context, index: Int, percent: Int) {
        val key = if (index == 0) KEY_GUIDE1 else KEY_GUIDE2
        context.getSharedPreferences(NAME, Context.MODE_PRIVATE).edit()
            .putInt(key, percent).apply()
    }

    fun saveGuidesVisible(context: Context, visible: Boolean) {
        context.getSharedPreferences(NAME, Context.MODE_PRIVATE).edit()
            .putBoolean(KEY_GUIDES_VISIBLE, visible).apply()
    }
}
