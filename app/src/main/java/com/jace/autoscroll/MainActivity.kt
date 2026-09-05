package com.jace.autoscroll

import android.Manifest
import android.content.ComponentName
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import com.jace.autoscroll.databinding.ActivityMainBinding
import java.io.File

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding

    private val requestNotification =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    private val statusListener: (ScrollStatus) -> Unit = { status ->
        runOnUiThread {
            binding.startButton.setText(
                if (status.isActive) R.string.action_stop else R.string.action_start
            )
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        bindSettings(Prefs.load(this))
        wireSliders()

        binding.accessibilityButton.setOnClickListener {
            openSettings(Settings.ACTION_ACCESSIBILITY_SETTINGS)
        }
        binding.overlayButton.setOnClickListener {
            startActivity(
                Intent(
                    Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:$packageName")
                )
            )
        }
        binding.startButton.setOnClickListener { onStartPressed() }
        binding.shareLinkButton.setOnClickListener { shareLink() }
        binding.shareApkButton.setOnClickListener { shareApk() }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            requestNotification.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    override fun onResume() {
        super.onResume()
        refreshPermissionState()
        ScrollController.addListener(statusListener)
    }

    override fun onPause() {
        ScrollController.removeListener(statusListener)
        super.onPause()
    }

    // ---------------------------------------------------------------- 설정

    private fun bindSettings(config: ScrollConfig) = with(binding) {
        directionToggle.check(
            if (config.direction == ScrollDirection.UP) R.id.directionUp else R.id.directionDown
        )
        speedSlider.value = config.swipeDurationMs.toFloat()
        distanceSlider.value = config.distancePercent.toFloat()
        intervalSlider.value = config.intervalMs.toFloat()
        totalSlider.value = config.totalSeconds.toFloat()
        delaySlider.value = config.startDelaySec.toFloat()
        randomSwitch.isChecked = config.randomize
        renderValues()
    }

    private fun currentConfig(): ScrollConfig = with(binding) {
        ScrollConfig(
            direction = if (directionToggle.checkedButtonId == R.id.directionUp) {
                ScrollDirection.UP
            } else {
                ScrollDirection.DOWN
            },
            swipeDurationMs = speedSlider.value.toInt(),
            distancePercent = distanceSlider.value.toInt(),
            intervalMs = intervalSlider.value.toInt(),
            totalSeconds = totalSlider.value.toInt(),
            startDelaySec = delaySlider.value.toInt(),
            randomize = randomSwitch.isChecked,
        )
    }

    private fun wireSliders() = with(binding) {
        val onChange = { _: Any -> persist() }
        speedSlider.addOnChangeListener { _, _, _ -> onChange(Unit) }
        distanceSlider.addOnChangeListener { _, _, _ -> onChange(Unit) }
        intervalSlider.addOnChangeListener { _, _, _ -> onChange(Unit) }
        totalSlider.addOnChangeListener { _, _, _ -> onChange(Unit) }
        delaySlider.addOnChangeListener { _, _, _ -> onChange(Unit) }
        randomSwitch.setOnCheckedChangeListener { _, _ -> onChange(Unit) }
        directionToggle.addOnButtonCheckedListener { _, _, isChecked ->
            if (isChecked) onChange(Unit)
        }
    }

    private fun persist() {
        Prefs.save(this, currentConfig())
        renderValues()
    }

    private fun renderValues() = with(binding) {
        val config = currentConfig()
        speedValue.text = getString(R.string.value_speed, config.swipeDurationMs / 1000f)
        distanceValue.text = getString(R.string.value_distance, config.distancePercent)
        intervalValue.text = getString(R.string.value_interval, config.intervalMs / 1000f)
        totalValue.text = if (config.isUnlimited) {
            getString(R.string.value_unlimited)
        } else {
            getString(R.string.value_total, config.totalSeconds / 60, config.totalSeconds % 60)
        }
        delayValue.text = getString(R.string.value_delay, config.startDelaySec)
    }

    // ------------------------------------------------------------- 권한/시작

    private fun refreshPermissionState() {
        val accessibilityOn = isAccessibilityEnabled()
        binding.accessibilityStatus.setText(
            if (accessibilityOn) R.string.permission_granted else R.string.permission_missing
        )
        binding.accessibilityButton.setText(
            if (accessibilityOn) R.string.action_open_settings else R.string.action_grant
        )

        val overlayOn = Settings.canDrawOverlays(this)
        binding.overlayStatus.setText(
            if (overlayOn) R.string.permission_granted else R.string.permission_missing
        )
        binding.overlayButton.setText(
            if (overlayOn) R.string.action_open_settings else R.string.action_grant
        )

        binding.startButton.setText(
            if (ScrollController.isActive) R.string.action_stop else R.string.action_start
        )
    }

    private fun onStartPressed() {
        if (ScrollController.isActive) {
            ScrollController.stop()
            OverlayService.stop(this)
            refreshPermissionState()
            return
        }
        if (!isAccessibilityEnabled()) {
            Toast.makeText(this, R.string.toast_need_accessibility, Toast.LENGTH_LONG).show()
            openSettings(Settings.ACTION_ACCESSIBILITY_SETTINGS)
            return
        }
        if (!Settings.canDrawOverlays(this)) {
            Toast.makeText(this, R.string.toast_need_overlay, Toast.LENGTH_LONG).show()
            startActivity(
                Intent(
                    Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:$packageName")
                )
            )
            return
        }

        persist()
        OverlayService.show(this, startImmediately = true)
        Toast.makeText(
            this,
            getString(R.string.toast_started, currentConfig().startDelaySec),
            Toast.LENGTH_LONG
        ).show()
        // 보상 앱으로 바로 넘어갈 수 있도록 이 화면은 뒤로 보낸다.
        moveTaskToBack(true)
    }

    private fun isAccessibilityEnabled(): Boolean {
        if (ScrollController.isServiceReady) return true
        val component = ComponentName(this, AutoScrollService::class.java)
        val enabled = Settings.Secure.getString(
            contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        ) ?: return false
        return enabled.split(':').any {
            it.equals(component.flattenToString(), ignoreCase = true) ||
                it.equals(component.flattenToShortString(), ignoreCase = true)
        }
    }

    private fun openSettings(action: String) {
        runCatching { startActivity(Intent(action)) }.onFailure {
            Toast.makeText(this, R.string.toast_settings_failed, Toast.LENGTH_LONG).show()
        }
    }

    // ---------------------------------------------------------------- 공유

    private fun shareLink() {
        val intent = Intent(Intent.ACTION_SEND)
            .setType("text/plain")
            .putExtra(Intent.EXTRA_SUBJECT, getString(R.string.app_name))
            .putExtra(Intent.EXTRA_TEXT, getString(R.string.share_message))
        startActivity(Intent.createChooser(intent, getString(R.string.action_share)))
    }

    /** 설치된 자기 자신의 APK 를 복사해 카카오톡 등으로 그대로 보낸다. */
    private fun shareApk() {
        val source = File(applicationInfo.sourceDir)
        val target = File(cacheDir, "shared").apply { mkdirs() }
            .let { File(it, "AutoScroll-${BuildConfig.VERSION_NAME}.apk") }

        val ok = runCatching { source.copyTo(target, overwrite = true) }.isSuccess
        if (!ok) {
            Toast.makeText(this, R.string.toast_share_failed, Toast.LENGTH_LONG).show()
            return
        }

        val uri = FileProvider.getUriForFile(this, "$packageName.fileprovider", target)
        val intent = Intent(Intent.ACTION_SEND)
            .setType("application/vnd.android.package-archive")
            .putExtra(Intent.EXTRA_STREAM, uri)
            .putExtra(Intent.EXTRA_TEXT, getString(R.string.share_message))
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        startActivity(Intent.createChooser(intent, getString(R.string.action_share_apk)))
    }
}
