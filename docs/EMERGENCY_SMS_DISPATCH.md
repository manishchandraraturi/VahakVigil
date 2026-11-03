# Emergency SMS Dispatch Execution Foundation

## 1. SMS Sender Abstraction
The `EmergencySmsSender` interface (`com.manish.vahakvigil.domain.sms.EmergencySmsSender`) is a pure-Kotlin domain abstraction for transmitting emergency SMS alerts. It contains no Android platform dependencies:

```kotlin
interface EmergencySmsSender {
    suspend fun send(
        phoneNumber: String,
        message: String
    ): EmergencyActionResult
}
```

The domain orchestrator interacts with this contract through `SmsEmergencyActionExecutor`, ensuring domain isolation from telephony framework APIs.

## 2. Android Implementation (`AndroidEmergencySmsSender`)
The Android implementation (`com.manish.vahakvigil.data.sms.AndroidEmergencySmsSender`) resides exclusively in the data layer:
- **API Resolution**: Acquires `SmsManager` dynamically via `context.getSystemService(SmsManager::class.java)` on Android 12+ (API 31+), with fallback to `@Suppress("DEPRECATION") SmsManager.getDefault()` on earlier releases.
- **Multipart Message Division**: Invokes `smsManager.divideMessage(message)`. If the message spans multiple parts (`parts.size > 1`), it executes `smsManager.sendMultipartTextMessage()`. Otherwise, it uses `smsManager.sendTextMessage()`.
- **Exception Safety**: Traps `SecurityException` and general exceptions, translating failures into `EmergencyActionResult(status = FAILED)` without crashing.

## 3. `SEND_SMS` Permission Behavior
- The manifest declares only the minimal foreground telephony permission:
  ```xml
  <uses-permission android:name="android.permission.SEND_SMS" />
  ```
- No extraneous permissions (`READ_SMS`, `RECEIVE_SMS`, `READ_PHONE_STATE`, `CALL_PHONE`, etc.) are declared or requested.
- Prior to invoking `SmsManager`, `AndroidEmergencySmsSender` verifies `ContextCompat.checkSelfPermission(context, Manifest.permission.SEND_SMS) == PackageManager.PERMISSION_GRANTED`.
- If the permission is missing or denied, it **safely returns `FAILED`**. It never crashes and never attempts transmission.

## 4. Critical Safety Gates
Actual SMS dispatch is governed strictly by the `EmergencyActionOrchestrator`. A send operation is executed **ONLY** when ALL of the following criteria are met:
1. `EmergencyDecision.state == EMERGENCY_CANDIDATE`
2. `EmergencyCountdownState == EXPIRED`
3. The incident has **NOT** been cancelled (by user interaction during countdown)
4. The incident has **NOT** already been dispatched (duplicate prevention)

> [!CAUTION]
> Neither GPS availability, user interface interactions, nor direct method calls can bypass or weaken these safety gates.

## 5. Duplicate Prevention
The `EmergencyActionOrchestrator` maintains the `dispatchedIncidentKey` (keyed on `incidentTimestampMs`). If an evaluation occurs for an incident that has already triggered an action, it immediately returns the existing result without invoking the executor or sender again.

## 6. Multiple Recipients
- Each contact in `EmergencyDispatchPayload.emergencyContacts` is processed as an **independent recipient**.
- Phone numbers are never grouped into a single combined message body.
- Android Contacts Provider is **never** queried at runtime; only pre-configured Room emergency contacts from the payload are alerted.
- Empty or blank phone numbers are caught and rejected safely.

## 7. Multipart SMS Handling
- The domain layer's `EmergencySmsFormatter` deterministically computes the GSM-7 vs Unicode character encoding and estimated segment count.
- At the execution layer, `SmsManager.divideMessage(message)` splits the message according to telephony specifications.
- `sendMultipartTextMessage` ensures concatenated delivery by telecom carriers.

## 8. "TRIGGERED" vs "Delivered" Semantics
- In this milestone, a successful handoff to Android `SmsManager` produces an `EmergencyActionResult` with `status = TRIGGERED`.
- It indicates that the send request was accepted by the Android telephony subsystem.
- **It does NOT mean "delivered to recipient device".** Carrier delivery reporting requires pending intents and broadcast receivers, which are deferred to future milestones.

## 9. Privacy & Data Protection
- **No Logging**: Phone numbers, recipient names, medical summaries, and SMS text contents are **never** logged to Logcat, analytics, or remote servers.
- All dispatch payload contents reside strictly in-memory during active execution.

## 10. Physical Device Testing Limitations
- No physical SMS was transmitted during testing.
- Unit tests use `FakeEmergencySmsSender` on the JVM without invoking real SIM cards or mobile networks.
