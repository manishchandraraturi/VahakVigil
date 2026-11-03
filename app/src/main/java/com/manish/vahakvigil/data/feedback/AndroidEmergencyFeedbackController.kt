package com.manish.vahakvigil.data.feedback

import android.content.Context
import android.hardware.camera2.CameraManager
import android.media.Ringtone
import android.media.RingtoneManager
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.view.Window
import android.view.WindowManager
import com.manish.vahakvigil.domain.feedback.EmergencyFeedbackController

/**
 * Android platform implementation of [EmergencyFeedbackController].
 *
 * Responsibilities & Safety:
 * - Engages a repeating vibration pattern via [Vibrator] without allocating objects repeatedly during recomposition.
 * - Sounds an audible alarm tone via [Ringtone] and stops cleanly on [stopAlert].
 * - Requests maximum screen brightness via [Window.attributes] while active, restoring the original value on dismiss.
 * - Activates the camera torch mode via [CameraManager] with full exception safety (never crashes if flash is missing).
 * - Lifecycle-safe: [stopAlert] ensures all hardware and media components immediately cease alerting.
 */
class AndroidEmergencyFeedbackController(
    private val context: Context,
    private val windowProvider: () -> Window? = { null }
) : EmergencyFeedbackController {

    private var isAlerting = false
    private var ringtone: Ringtone? = null
    private var torchCameraId: String? = null
    private var originalBrightness: Float? = null

    private val vibrator: Vibrator? by lazy {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val vm = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
                vm?.defaultVibrator
            } else {
                @Suppress("DEPRECATION")
                context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
            }
        } catch (e: Exception) {
            null
        }
    }

    private val cameraManager: CameraManager? by lazy {
        try {
            context.getSystemService(Context.CAMERA_SERVICE) as? CameraManager
        } catch (e: Exception) {
            null
        }
    }

    override fun startAlert() {
        if (isAlerting) return
        isAlerting = true

        // 1. Vibration
        try {
            val pattern = longArrayOf(0, 500, 200, 500)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val effect = VibrationEffect.createWaveform(pattern, 0)
                vibrator?.vibrate(effect)
            } else {
                @Suppress("DEPRECATION")
                vibrator?.vibrate(pattern, 0)
            }
        } catch (e: Exception) {
            // Safe fallback if vibration permission or hardware unavailable
        }

        // 2. Audio Alarm
        try {
            val alarmUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
                ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
            ringtone = RingtoneManager.getRingtone(context, alarmUri)?.apply {
                play()
            }
        } catch (e: Exception) {
            // Safe fallback if audio hardware unavailable
        }

        // 3. Screen Brightness
        try {
            windowProvider()?.let { window ->
                val lp = window.attributes
                originalBrightness = lp.screenBrightness
                lp.screenBrightness = WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_FULL
                window.attributes = lp
            }
        } catch (e: Exception) {
            // Safe fallback
        }

        // 4. Flashlight Torch
        try {
            torchCameraId = cameraManager?.cameraIdList?.firstOrNull()
            torchCameraId?.let { id ->
                cameraManager?.setTorchMode(id, true)
            }
        } catch (e: Exception) {
            // Safe fallback if camera/torch is missing or restricted
        }
    }

    override fun stopAlert() {
        if (!isAlerting) return
        isAlerting = false

        // 1. Stop Vibration
        try {
            vibrator?.cancel()
        } catch (e: Exception) {
            // Ignore
        }

        // 2. Stop Audio Alarm
        try {
            ringtone?.let {
                if (it.isPlaying) {
                    it.stop()
                }
            }
            ringtone = null
        } catch (e: Exception) {
            // Ignore
        }

        // 3. Restore Screen Brightness
        try {
            windowProvider()?.let { window ->
                val lp = window.attributes
                lp.screenBrightness = originalBrightness ?: WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE
                window.attributes = lp
            }
        } catch (e: Exception) {
            // Ignore
        }

        // 4. Turn Off Flashlight Torch
        try {
            torchCameraId?.let { id ->
                cameraManager?.setTorchMode(id, false)
            }
            torchCameraId = null
        } catch (e: Exception) {
            // Ignore
        }
    }
}
