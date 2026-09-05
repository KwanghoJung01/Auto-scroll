package com.jace.autoscroll

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.math.roundToInt
import kotlin.random.Random

/** 화면에 보여줄 현재 상태. */
data class ScrollStatus(
    val phase: Phase,
    /** 남은 시간(초). 무제한이거나 멈춰 있으면 -1. */
    val secondsLeft: Int,
    /** 시작 대기 중 남은 초. */
    val countdownLeft: Int,
    /** 지금까지 수행한 스와이프 횟수. */
    val swipes: Int,
) {
    enum class Phase { IDLE, COUNTDOWN, RUNNING }

    val isActive: Boolean get() = phase != Phase.IDLE
}

/**
 * 자동 스크롤의 두뇌. 접근성 서비스(손 역할)와 화면(버튼 역할) 사이에서
 * 언제 얼마나 쓸어내릴지를 혼자 관리한다.
 */
object ScrollController {

    private val handler = Handler(Looper.getMainLooper())
    private val listeners = CopyOnWriteArrayList<(ScrollStatus) -> Unit>()

    var service: AutoScrollService? = null
        set(value) {
            field = value
            if (value == null) stop() else notifyListeners()
        }

    val isServiceReady: Boolean get() = service != null

    private var phase = ScrollStatus.Phase.IDLE
    private var countdownLeft = 0
    private var endAtElapsed = 0L
    private var swipes = 0
    private var config = ScrollConfig()

    val isActive: Boolean get() = phase != ScrollStatus.Phase.IDLE

    fun addListener(listener: (ScrollStatus) -> Unit) {
        listeners.add(listener)
        listener(status())
    }

    fun removeListener(listener: (ScrollStatus) -> Unit) {
        listeners.remove(listener)
    }

    fun status(): ScrollStatus {
        val left = when {
            phase != ScrollStatus.Phase.RUNNING || endAtElapsed == 0L -> -1
            else -> ((endAtElapsed - SystemClock.elapsedRealtime()) / 1000L)
                .coerceAtLeast(0L).toInt()
        }
        return ScrollStatus(phase, left, countdownLeft, swipes)
    }

    /** 설정을 읽어 카운트다운부터 시작한다. 접근성 서비스가 꺼져 있으면 false. */
    fun start(context: Context): Boolean {
        if (service == null) return false
        if (isActive) return true

        config = Prefs.load(context)
        swipes = 0
        endAtElapsed = 0L
        countdownLeft = config.startDelaySec

        if (countdownLeft > 0) {
            phase = ScrollStatus.Phase.COUNTDOWN
            notifyListeners()
            handler.postDelayed(countdownTick, 1000L)
        } else {
            beginRunning()
        }
        return true
    }

    fun stop() {
        handler.removeCallbacksAndMessages(null)
        phase = ScrollStatus.Phase.IDLE
        countdownLeft = 0
        endAtElapsed = 0L
        notifyListeners()
    }

    fun toggle(context: Context): Boolean = if (isActive) {
        stop()
        true
    } else {
        start(context)
    }

    private val countdownTick = object : Runnable {
        override fun run() {
            if (phase != ScrollStatus.Phase.COUNTDOWN) return
            countdownLeft -= 1
            if (countdownLeft <= 0) {
                beginRunning()
            } else {
                notifyListeners()
                handler.postDelayed(this, 1000L)
            }
        }
    }

    private fun beginRunning() {
        phase = ScrollStatus.Phase.RUNNING
        countdownLeft = 0
        endAtElapsed = if (config.isUnlimited) {
            0L
        } else {
            SystemClock.elapsedRealtime() + config.totalSeconds * 1000L
        }
        notifyListeners()
        handler.post(swipeLoop)
        handler.postDelayed(clockTick, 1000L)
    }

    /** 쉬는 시간이 길어도 남은 시간 표시가 멈춰 보이지 않도록 1초마다 갱신한다. */
    private val clockTick = object : Runnable {
        override fun run() {
            if (phase != ScrollStatus.Phase.RUNNING) return
            if (endAtElapsed != 0L && SystemClock.elapsedRealtime() >= endAtElapsed) {
                stop()
                return
            }
            notifyListeners()
            handler.postDelayed(this, 1000L)
        }
    }

    private val swipeLoop = object : Runnable {
        override fun run() {
            if (phase != ScrollStatus.Phase.RUNNING) return

            if (endAtElapsed != 0L && SystemClock.elapsedRealtime() >= endAtElapsed) {
                stop()
                return
            }

            val target = service
            if (target == null) {
                stop()
                return
            }

            val duration = jitter(config.swipeDurationMs).coerceAtLeast(50)
            val distance = jitter(config.distancePercent).coerceIn(5, 95)
            target.swipe(config.direction, distance, duration)

            swipes += 1
            notifyListeners()

            val gap = jitter(config.intervalMs).coerceAtLeast(0)
            handler.postDelayed(this, (duration + gap).toLong())
        }
    }

    /** [randomize] 가 켜져 있으면 값을 ±20% 흔들어 기계적인 반복을 줄인다. */
    private fun jitter(value: Int): Int {
        if (!config.randomize) return value
        val delta = value * 0.2
        return (value + Random.nextDouble(-delta, delta)).roundToInt()
    }

    private fun notifyListeners() {
        val snapshot = status()
        listeners.forEach { it(snapshot) }
    }
}
