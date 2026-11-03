# Emergency Dispatch Payload & Contact Compilation Layer

## 1. Dispatch Payload Purpose
The `EmergencyDispatchPayload` assembles all vital data required by future emergency dispatch mechanisms (such as SMS notifications and contact alerting). It transforms raw Room persistence entities into a structured, read-only domain snapshot at the time an emergency candidate incident is qualified.

## 2. Medical Profile Source
Medical summary details (`EmergencyMedicalSummary`) are read directly from `MedicalProfileRepository` (backed by Room `medical_profile` single-row persistence).
Included fields:
- `riderName`
- `bloodGroup`
- `allergies`
- `conditions`
- `medications`
- `notes`

If the user has not configured a medical profile, `medicalSummary` safely defaults to `null` without error or synthetic values.

## 3. Emergency Contact Source
Contact recipients (`EmergencyDispatchContact`) are read directly from `ContactRepository` (backed by Room `emergency_contacts` table).
The compiled payload preserves the exact deterministic ordering returned by the repository.
If no emergency contacts are stored, an empty list is compiled. The system does not create synthetic, default, or external contacts (no mock police or ambulance contacts).

## 4. Location Currently Unavailable
Because real GPS integration (via Google Play Services or Android LocationManager) has not yet been introduced into the project, location is explicitly represented as:
```kotlin
EmergencyLocation.Unavailable
```
No fake or simulated coordinates are injected into the payload.

## 5. No Real Dispatch Yet
This layer is purely for data compilation. The system:
- Does NOT invoke Android `SmsManager`
- Does NOT initiate telephony calls
- Does NOT query GPS or Location APIs
- Does NOT notify contacts or dispatch emergency services
- Does NOT make network or Firebase requests

## 6. Future GPS, SMS, and Call Integration
Once platform services are connected:
1. `EmergencyLocation.Available` will hold validated GPS coordinates captured via `FusedLocationProviderClient`.
2. An SMS formatter will serialize the `EmergencyDispatchPayload` into a concise, standards-compliant emergency message.
3. Telephony and SMS managers will transmit alerts to each contact in `emergencyContacts`.

## 7. Privacy Considerations
Medical and contact data are sensitive personal information.
- The compilation process does NOT log personal names, medical notes, or telephone numbers to Android logcat or analytics.
- Data remains strictly in-memory within the domain layer until explicit dispatch is triggered.
