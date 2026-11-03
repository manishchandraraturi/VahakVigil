# Emergency Countdown UI & User Cancellation Screen

## 1. Architectural Overview & State Flow

The Emergency Countdown UI represents the user-facing alert and cancellation layer of the VahakVigil safety pipeline. It is triggered only after the multi-stage sensor pipeline qualifies an incident and confirms post-impact stillness:

```
Sensor Monitoring (Accelerometer + Gyroscope)
        ↓
Temporal Motion Pattern Analysis
        ↓
Confidence Scoring Engine
        ↓
Event Qualification Engine (QualifiedIncident)
        ↓
Post-Impact Motion Analysis (Stillness Confirmed)
        ↓
Emergency Decision Engine (EMERGENCY_CANDIDATE)
        ↓
Emergency Countdown Engine (COUNTING_DOWN)
        ↓
HomeUiState.isEmergencyModeActive == true
        ↓
EmergencyScreen Launched + EmergencyFeedbackController Activated
        ↓
User Choices:
  ├── "I'm Fine" → cancelEmergencyCountdown() → CANCELLED → Alert Ceases → Return Home
  └── "I Need Help" → acknowledgeNeedHelp() → Help Registered → Countdown Proceeds
        ↓
Countdown Expiration (EXPIRED) → EmergencyActionOrchestrator → Automated SMS Dispatch
```

## 2. Countdown Engine as Single Source of Truth

The Compose UI does **not** manage its own independent timer or coroutine delays.
- The UI strictly observes `HomeUiState.emergencyCountdownRemainingMillis` provided by `EmergencyCountdownEngine` via `HomeViewModel.uiState`.
- Remaining display seconds are derived deterministically:
  ```kotlin
  val remainingSeconds = (uiState.emergencyCountdownRemainingMillis / 1000).coerceAtLeast(0)
  ```
- Any update to the engine (from sensor updates or cancellation) propagates immediately and reactively to the Composable.

## 3. Emergency Feedback Controller Abstraction

Multi-sensory alerting is decoupled from Android hardware APIs via `EmergencyFeedbackController` (`com.manish.vahakvigil.domain.feedback.EmergencyFeedbackController`):

```kotlin
interface EmergencyFeedbackController {
    fun startAlert()
    fun stopAlert()
}
```

### Android Platform Implementation (`AndroidEmergencyFeedbackController`)
Resides in `com.manish.vahakvigil.data.feedback.AndroidEmergencyFeedbackController`:
- **Vibration**: Engages a repeating waveform pattern via `Vibrator` / `VibratorManager` (`longArrayOf(0, 500, 200, 500)`).
- **Audible Alarm**: Sounds the system emergency alarm tone via `RingtoneManager.TYPE_ALARM` with fallback to `TYPE_NOTIFICATION`.
- **Screen Brightness**: Overrides window attributes to maximum brightness (`WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_FULL`), and restores the prior value when alerting stops.
- **Beacon Torch**: Commands camera flash torch via `CameraManager.setTorchMode(id, true)`.
- **Fault-Tolerant Cleanup**: `stopAlert()` halts sound, vibration, resets torch, and restores screen brightness with individual try/catch safety boundaries.

### Composable Lifecycle Binding
`EmergencyScreen` binds alerts directly to the Composable lifecycle using `DisposableEffect`:
```kotlin
DisposableEffect(feedbackController) {
    feedbackController.startAlert()
    onDispose {
        feedbackController.stopAlert()
    }
}
```
If the user navigates away, dismisses the alert, or presses back, all feedback immediately ceases.

## 4. User Cancellation ("I'm Fine")

When the user taps the prominent "I'M FINE (CANCEL)" button or presses system back:
1. `HomeViewModel.cancelEmergencyCountdown()` is invoked.
2. `EmergencyCountdownEngine.cancel()` transitions state to `CANCELLED`.
3. `EmergencyActionOrchestrator.evaluate()` evaluates with `CANCELLED` countdown state, ensuring no dispatch request is made.
4. `HomeUiState.isEmergencyModeActive` transitions to `false`.
5. Feedback stops immediately via `DisposableEffect.onDispose`.
6. The screen pops back to `HomeScreen`.

## 5. User Acknowledgment ("I Need Help") & Safety Gates

When the user taps "I NEED HELP":
- Invokes `HomeViewModel.acknowledgeNeedHelp()`.
- Updates `HomeUiState.userAcknowledgedNeedHelp = true`.
- Displays visual confirmation that distress is acknowledged.
- **Safety Gate Guarantee**: Tapping "I Need Help" **never** bypasses or short-circuits the countdown or action orchestrator. The automated dispatch countdown continues running safely towards completion.

## 6. Navigation Integration

- Declared in `Screen.Emergency` (`Screen("emergency")`).
- Integrated in `VahakVigilNavHost` using shared `HomeViewModel` hoisted across routes.
- `HomeScreen` observes `uiState.isEmergencyModeActive` and automatically navigates to `Screen.Emergency.route`.

## 7. SMS Duplicate-Prevention Semantics

The `EmergencyActionOrchestrator` duplicate-lock semantics were corrected:
- The incident key is locked as dispatched **ONLY** when `EmergencyActionResult.status == EmergencyActionResultStatus.TRIGGERED`.
- If an execution fails (e.g., lack of permission or hardware failure resulting in `FAILED`), the orchestrator does **not** permanently lock the incident key, allowing a subsequent attempt to succeed once preconditions are resolved.

## 8. High-Contrast & Accessibility Design

- High-contrast visual palette using deep emergency crimson (`#2A0808` to `#0D0202`) and high-luminance alert red (`#FF5252`).
- Giant 64sp countdown text with a visual progress indicator.
- Minimum 56dp+ tap targets with generous padding for stress-resistant interaction.
- Explicit accessibility semantics on the countdown ring and action buttons (`contentDescription`).
