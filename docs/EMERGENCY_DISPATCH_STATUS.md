# Emergency Dispatch Status & Post-Expiration UI

## 1. Architectural State Machine

The emergency response lifecycle transitions through a strict, deterministic progression:

```
[IDLE]
  │
  │  (Qualified Incident + Stillness Confirmed)
  ▼
[COUNTING_DOWN] (75s Countdown)
  ├── User taps "I'm Fine" ────────────► [CANCELLED] ──► Return to Home
  │
  └── 75s Countdown Reaches Zero
        │
        ▼
     [EXPIRED] (isEmergencyModeActive remains TRUE)
        │
        ▼
     [DISPATCHING]
        │
        ├── All contacts fail ────────► [DISPATCH_FAILED]
        │                                  ├── User taps "RETRY DISPATCH" ──► Re-evaluates via Orchestrator
        │                                  └── User taps "RETURN TO HOME" ──► [IDLE]
        │
        └── ≥1 contact triggered ─────► [DISPATCH_TRIGGERED]
                                           └── User taps "RETURN TO HOME" ──► [IDLE]
```

## 2. Expiration Semantics vs. Cancellation Semantics

### Expiration is NOT Cancellation
- **Prior Flaw**: The initial implementation cleared `isEmergencyModeActive` immediately when `countdownEngine.state == EXPIRED`. This caused `EmergencyScreen` to pop back to `HomeScreen` the instant the countdown hit zero, prematurely tearing down the UI and masking the dispatch process.
- **Corrected Semantics**:
  - `CANCELLED`: Triggered solely by deliberate user action ("I'm Fine" button or system back during the countdown). Disengages emergency mode (`isEmergencyModeActive = false`), halts feedback, and exits the screen.
  - `EXPIRED`: The natural termination of the safety countdown window. Expiration signals that the rider did not cancel and authorizes emergency dispatch. `isEmergencyModeActive` remains `true`, transitioning the UI into post-expiration views (`DISPATCHING`, `DISPATCH_TRIGGERED`, or `DISPATCH_FAILED`).

### Immutability of Expired State
Once a countdown has reached `EXPIRED` and been evaluated:
- `cancelEmergencyCountdown()` is strictly a no-op.
- The UI eliminates the "I'm Fine" button to prevent creating a false expectation that an in-flight or completed dispatch can be recalled or cancelled.

## 3. Explicit Dispatch UI States

The `EmergencyDispatchUiState` enum (`com.manish.vahakvigil.domain.model.EmergencyDispatchUiState`) represents each phase:

| State | Condition | User Interface Description | Action Controls Available |
| :--- | :--- | :--- | :--- |
| `IDLE` | Normal sensor monitoring | Standby / standard Home screen | Monitoring toggle |
| `COUNTING_DOWN` | `EMERGENCY_CANDIDATE` + countdown active | Circular countdown ring, large remaining seconds | "I'M FINE (CANCEL)", "I NEED HELP" |
| `CANCELLED` | User cancelled before expiration | Feedback stops, auto-dismisses to Home | None |
| `EXPIRED` / `DISPATCHING` | Countdown reached zero; compiling payload | "EMERGENCY RESPONSE ACTIVE", progress spinner | None (dispatch in progress) |
| `DISPATCH_TRIGGERED` | ≥1 recipient triggered successfully | "EMERGENCY ALERT INITIATED", status card | "RETURN TO HOME" |
| `DISPATCH_FAILED` | Transmission failed for all recipients | "EMERGENCY ALERT NOT SENT", failure card | "RETRY DISPATCH", "RETURN TO HOME" |

## 4. Multi-Contact Dispatch & Partial Success Semantics

`SmsEmergencyActionExecutor` processes each recipient in `EmergencyDispatchPayload.emergencyContacts` independently:
- **Complete Success**: All contacts accepted by Android telephony (`triggeredCount > 0, failedCount == 0`) → `status = TRIGGERED`.
- **Partial Success**: At least one contact succeeded while others failed (`triggeredCount > 0, failedCount > 0`) → `status = TRIGGERED` with aggregate details: `"Partial SMS dispatch: X triggered, Y failed"`.
- **All-Contact Failure**: No contacts could be dispatched (`triggeredCount == 0`) → `status = FAILED`.
- **Truth in Reporting**: The app never claims complete success if all recipients failed. If any contact was reached, the incident is flagged as `TRIGGERED` while noting any recipient failures.

## 5. Why "Triggered" Does NOT Mean "Delivered"

The UI and domain layers rigorously adhere to honest safety communication:
- **"Triggered"**: Indicates that the emergency payload was successfully formatted, validated, and accepted by Android's `SmsManager` subsystem.
- **"Delivered"**: Requires carrier-level delivery receipts (`SMS_DELIVERED_ACTION` broadcast intents from telecom network base stations). Because carrier latency, network congestion, or out-of-range conditions can delay or prevent actual handset reception, VahakVigil **never** displays:
  - *"SMS delivered"*
  - *"Help is on the way"*
  - *"Emergency services have been contacted"*
- Instead, the UI explicitly displays:
  - *"Emergency alert initiated for your designated emergency contacts."*
  - *"Dispatched to cellular provider subsystem. Message delivery confirmation depends on mobile network carrier."*

## 6. Failure Recovery Behavior

When dispatch fails (due to lack of `SEND_SMS` permission, absent telephony service, or empty contact configuration):
1. Multi-sensory alarm and torch cease immediately.
2. The UI transitions to `DISPATCH_FAILED` ("EMERGENCY ALERT NOT SENT").
3. Advises the rider: *"Please call 112 directly if you require immediate emergency assistance."*
4. Provides two recovery options:
   - **"RETRY DISPATCH"**: Safely re-evaluates through `EmergencyActionOrchestrator.evaluate()` without creating duplicate channels or bypassing safety gates.
   - **"RETURN TO HOME"**: Safely dismisses the emergency screen and returns the rider to the Home dashboard.

## 7. Multi-Sensory Feedback Lifecycle

- **Countdown & Dispatching**: `feedbackController.startAlert()` (vibration, alarm tone, screen brightness, flashlight beacon).
- **Post-Dispatch**: Once outcome is resolved (`DISPATCH_TRIGGERED` or `DISPATCH_FAILED`), `feedbackController.stopAlert()` is called immediately. Feedback is never permitted to run indefinitely.
- **Disposal**: Bound to the Composable lifecycle via `DisposableEffect`, ensuring that hardware alerting terminates cleanly if the screen is dismissed or destroyed.

## 8. Privacy & Zero-Data Leakage

- **Zero PII Exposure**: The emergency dispatch UI and logs never render or print phone numbers, SMS text contents, medical histories, allergies, or GPS latitude/longitude.
- **Generic Status Only**: Only generic status information (such as *"Emergency alert initiated"* or failure summaries) is displayed to protect rider privacy.
