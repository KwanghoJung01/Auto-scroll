package com.jace.autoscroll

import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.PixelFormat
import android.os.Build
import android.os.IBinder
import android.provider.Settings
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.ImageButton
import android.widget.TextView
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import kotlin.math.abs

/**
 * 다른 앱 위에 떠 있는 작은 조작 바. 보상 앱을 보는 동안에도
 * 시작/정지를 누르고 남은 시간을 확인할 수 있게 한다.
 */
class OverlayService : Service() {

    companion object {
        const val ACTION_SHOW = "com.jace.autoscroll.SHOW"
        const val ACTION_STOP = "com.jace.autoscroll.STOP"
        /** 조작 바를 띄우면서 곧바로 자동 스크롤을 시작한다. */
        const val ACTION_START_SCROLL = "com.jace.autoscroll.START_SCROLL"
        /** 설정 화면에서 가로줄 설정을 바꿨을 때 다시 그리게 한다. */
        const val ACTION_REFRESH_GUIDES = "com.jace.autoscroll.REFRESH_GUIDES"

        private const val CHANNEL_ID = "auto_scroll_overlay"
        private const val NOTIFICATION_ID = 1001

        /** 조작 바가 떠 있는지. 설정 화면에서 갱신을 보낼지 판단하는 데 쓴다. */
        @Volatile
        var isRunning: Boolean = false
            private set

        fun show(context: Context, startImmediately: Boolean) {
            val intent = Intent(context, OverlayService::class.java).setAction(
                if (startImmediately) ACTION_START_SCROLL else ACTION_SHOW
            )
            context.startForegroundService(intent)
        }

        fun refreshGuides(context: Context) {
            if (!isRunning) return
            context.startForegroundService(
                Intent(context, OverlayService::class.java).setAction(ACTION_REFRESH_GUIDES)
            )
        }

        fun stop(context: Context) {
            context.startForegroundService(
                Intent(context, OverlayService::class.java).setAction(ACTION_STOP)
            )
        }
    }

    private var windowManager: WindowManager? = null
    private var overlay: View? = null
    private lateinit var params: WindowManager.LayoutParams

    private var statusText: TextView? = null
    private var toggleButton: ImageButton? = null
    private var guideButton: ImageButton? = null
    private var guides: GuideLineOverlay? = null

    private val listener: (ScrollStatus) -> Unit = { render(it) }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        isRunning = true
        startInForeground()
        addOverlay()
        addGuides()
        ScrollController.addListener(listener)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                ScrollController.stop()
                stopSelf()
                return START_NOT_STICKY
            }
            ACTION_START_SCROLL -> ScrollController.start(this)
            ACTION_REFRESH_GUIDES -> renderGuides()
        }
        return START_STICKY
    }

    override fun onDestroy() {
        isRunning = false
        guides?.clear()
        guides = null
        ScrollController.removeListener(listener)
        ScrollController.stop()
        overlay?.let { runCatching { windowManager?.removeView(it) } }
        overlay = null
        super.onDestroy()
    }

    private fun startInForeground() {
        val manager = getSystemService(NotificationManager::class.java)
        if (manager.getNotificationChannel(CHANNEL_ID) == null) {
            manager.createNotificationChannel(
                NotificationChannel(
                    CHANNEL_ID,
                    getString(R.string.notification_channel),
                    NotificationManager.IMPORTANCE_LOW
                )
            )
        }

        val stopIntent = PendingIntent.getService(
            this,
            0,
            Intent(this, OverlayService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val openIntent = PendingIntent.getActivity(
            this,
            1,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val notification: Notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_scroll)
            .setContentTitle(getString(R.string.app_name))
            .setContentText(getString(R.string.notification_text))
            .setContentIntent(openIntent)
            .setOngoing(true)
            .addAction(0, getString(R.string.action_quit), stopIntent)
            .build()

        ServiceCompat.startForeground(
            this,
            NOTIFICATION_ID,
            notification,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
            } else {
                0
            }
        )
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun addOverlay() {
        if (overlay != null) return
        if (!Settings.canDrawOverlays(this)) {
            // 권한이 취소된 채로 서비스가 되살아난 경우. 조용히 물러난다.
            stopSelf()
            return
        }
        windowManager = getSystemService(WindowManager::class.java)

        val view = LayoutInflater.from(this).inflate(R.layout.overlay_control, null)
        statusText = view.findViewById(R.id.overlay_status)
        toggleButton = view.findViewById(R.id.overlay_toggle)

        params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        ).apply {
            // 스와이프 경로(화면 가운데)와 겹치지 않도록 오른쪽 위에 둔다.
            gravity = Gravity.TOP or Gravity.START
            val metrics = resources.displayMetrics
            x = metrics.widthPixels - (210 * metrics.density).toInt()
            y = (metrics.heightPixels * 0.06f).toInt()
        }

        toggleButton?.setOnClickListener {
            if (!ScrollController.isServiceReady) {
                stopSelf()
                return@setOnClickListener
            }
            ScrollController.toggle(this)
        }
        guideButton = view.findViewById(R.id.overlay_guide)
        guideButton?.setOnClickListener { toggleGuides() }
        view.findViewById<View>(R.id.overlay_close).setOnClickListener { stopSelf() }

        view.findViewById<View>(R.id.overlay_handle).setOnTouchListener(DragListener())

        val added = runCatching { windowManager?.addView(view, params) }.isSuccess
        if (!added) {
            stopSelf()
            return
        }
        overlay = view
        render(ScrollController.status())
    }

    /** 조작 바를 손가락으로 끌어 원하는 자리에 둘 수 있게 한다. */
    @SuppressLint("ClickableViewAccessibility")
    private inner class DragListener : View.OnTouchListener {
        private var startX = 0
        private var startY = 0
        private var touchX = 0f
        private var touchY = 0f

        override fun onTouch(v: View, event: MotionEvent): Boolean {
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    startX = params.x
                    startY = params.y
                    touchX = event.rawX
                    touchY = event.rawY
                    return true
                }
                MotionEvent.ACTION_MOVE -> {
                    params.x = startX + (event.rawX - touchX).toInt()
                    params.y = startY + (event.rawY - touchY).toInt()
                    overlay?.let { runCatching { windowManager?.updateViewLayout(it, params) } }
                    return true
                }
                MotionEvent.ACTION_UP -> {
                    val moved = abs(event.rawX - touchX) > 8 || abs(event.rawY - touchY) > 8
                    if (!moved) v.performClick()
                    return true
                }
            }
            return false
        }
    }

    // ------------------------------------------------------------- 가로 기준선

    private fun addGuides() {
        if (guides != null) return
        guides = GuideLineOverlay(this).apply {
            onMoved = { index, percent -> Prefs.saveGuidePosition(this@OverlayService, index, percent) }
        }
        renderGuides()
    }

    private fun renderGuides() {
        val config = Prefs.load(this)
        guides?.show(config.visibleGuides())
        guideButton?.setImageResource(
            if (config.guidesVisible && config.guideLineCount > 0) {
                R.drawable.ic_guide
            } else {
                R.drawable.ic_guide_off
            }
        )
        guideButton?.alpha = if (config.guideLineCount > 0) 1f else 0.4f
    }

    /** 조작 바의 줄 버튼: 줄이 없으면 한 줄부터 켜고, 있으면 보였다 감췄다 한다. */
    private fun toggleGuides() {
        val config = Prefs.load(this)
        if (config.guideLineCount == 0) {
            Prefs.save(this, config.copy(guideLineCount = 1, guidesVisible = true))
        } else {
            Prefs.saveGuidesVisible(this, !config.guidesVisible)
        }
        renderGuides()
    }

    private fun render(status: ScrollStatus) {
        val label = when (status.phase) {
            ScrollStatus.Phase.IDLE -> getString(R.string.state_idle)
            ScrollStatus.Phase.COUNTDOWN -> getString(R.string.state_countdown, status.countdownLeft)
            ScrollStatus.Phase.RUNNING ->
                if (status.secondsLeft < 0) {
                    getString(R.string.state_running_unlimited, status.swipes)
                } else {
                    formatClock(status.secondsLeft)
                }
        }
        statusText?.text = label
        toggleButton?.setImageResource(
            if (status.isActive) R.drawable.ic_stop else R.drawable.ic_play
        )
        toggleButton?.contentDescription = getString(
            if (status.isActive) R.string.action_stop else R.string.action_start
        )
    }

    private fun formatClock(seconds: Int): String =
        "%d:%02d".format(seconds / 60, seconds % 60)
}
