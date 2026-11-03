# Emergency Action Orchestration Foundation

## 1. Why the Orchestrator Exists
The `EmergencyActionOrchestrator` provides a clean architectural separation between sensor-driven emergency detection and physical device action dispatch. It guarantees that candidate emergency decisions do not trigger external actions directly; instead, actions are strictly deferred until a user cancellation countdown completes without user intervention.

## 2. Expiry-Only Trigger Rule
Emergency actions are evaluated and triggered **ONLY** when:
```
EmergencyDecision.state == EMERGENCY_CANDIDATE
    AND
EmergencyCountdownState == EXPIRED
```
Actions are **NEVER** triggered from:
- `QualifiedIncident` alone
- Post-impact stillness alone
- `EMERGENCY_CANDIDATE` alone
- Active countdown (`COUNTING_DOWN`)
- Cancelled countdown (`CANCELLED`)
- `NO_EMERGENCY` state or uninitialized/null inputs

## 3. Cancellation Safety
When the user taps "I'm Fine", the countdown enters `CANCELLED`. The orchestrator explicitly records this cancellation for the active incident. Even if later updates or clock progressions reach the orchestrator for that same incident, it is permanently locked out from executing any emergency action.

## 4. Duplicate Prevention
The orchestrator tracks `dispatchedIncidentKey`. Once an expired incident triggers an emergency action, subsequent sensor ticks or repeated evaluations for the same incident return the existing result without re-invoking the action executor. A new incident with a distinct key or a clean session reset is required for subsequent dispatch.

## 5. Action Executor Abstraction
The `EmergencyActionExecutor` interface defines the contract:
```kotlin
fun execute(request: EmergencyActionRequest): EmergencyActionResult
```
Domain logic remains purely in Kotlin, completely independent of Android framework APIs, telephony, SMS, location services, or UI components. A `NoOpEmergencyActionExecutor` is provided for safe default simulation and testing.

## 6. Current Absence of Real Emergency Actions
This milestone is strictly architectural. The system currently:
- Does NOT send SMS messages
- Does NOT initiate phone calls
- Does NOT query GPS or Location APIs
- Does NOT contact emergency contacts or services
- Does NOT connect to Firebase or cloud endpoints

## 7. Future Location, SMS, and Call Integration
In future milestones, platform-specific implementations of `EmergencyActionExecutor` will be created in the data/platform layers to execute real actions in sequence:
1. Capture fresh location coordinates via FusedLocationProviderClient.
2. Formulate emergency dispatch SMS payload including location and medical profile details.
3. Transmit SMS to configured Emergency Contacts via SmsManager.
4. Optionally initiate phone calls via Android Telecom / Intent.
