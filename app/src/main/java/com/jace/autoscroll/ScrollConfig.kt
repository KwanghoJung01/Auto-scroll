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
) {
    val isUnlimited: Boolean get() = totalSeconds <= 0
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
            .apply()
    }
}
