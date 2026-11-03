# Emergency SMS Message Formatter & Validation Layer

## 1. Formatter Purpose
The `EmergencySmsFormatter` converts an `EmergencyDispatchPayload` into a structured, standards-compliant `EmergencySmsMessage` ready for future SMS dispatch. It operates as a deterministic, pure-Kotlin domain layer component without dependencies on Android platform APIs.

## 2. Tone and Non-Alarmist Phrasing
Per critical safety requirements, the message explicitly avoids sensationalized, speculative, or unverified claims.
- **Header**: `VAHAKVIGIL EMERGENCY ALERT`
- **Notice**: `Possible emergency detected.`
- It never asserts that a crash has been confirmed, nor does it make medical determinations about rider injury.

## 3. Message Structure and Ordering
The generated text follows a clean, predictable line-by-line structure:
```text
VAHAKVIGIL EMERGENCY ALERT
Possible emergency detected.
Rider: <riderName>
Incident: <UTC ISO-8601 timestamp>
Blood Group: <bloodGroup>
Allergies: <allergies>
Conditions: <conditions>
Medications: <medications>
Notes: <notes>
Location: <latitude, longitude or "unavailable">
```

### Omission Rules for Missing Data
- If `riderName` is blank or null, the entire `Rider:` line is omitted.
- If `medicalSummary` is null or individual medical fields are blank, those respective lines are omitted.
- No literal `"null"`, `"unknown"`, `"N/A"`, or synthetic values are ever inserted.
- When `EmergencyLocation.Unavailable` is present, it outputs `Location: unavailable`.
- When `EmergencyLocation.Available` is present, it outputs `Location: <lat>, <lon>`.

## 4. Character Encoding Detection (GSM 7-bit vs Unicode)
The formatter deterministically checks every character in the generated message against the GSM 03.38 basic character set and extension set (with escape characters):
- **GSM 7-bit**: Standard Latin letters, numbers, punctuation, Greek capital letters, and standard whitespace.
- **Unicode (UCS-2 / UTF-16)**: Selected automatically if any character outside the GSM 03.38 charset (such as emojis, accented non-GSM characters, Devanagari, or Asian scripts) is detected.

## 5. Segment Calculation and Limits
SMS transport limits dictate the maximum payload size per physical SMS segment:
- **GSM 7-bit**:
  - Single segment: up to 160 characters
  - Multipart: 153 characters per segment (with UDH header overhead)
- **Unicode**:
  - Single segment: up to 70 characters
  - Multipart: 67 characters per segment (with UDH header overhead)

Formula:
```kotlin
val segmentCount = when (encoding) {
    SmsEncoding.GSM_7BIT -> if (len <= 160) 1 else (len + 152) / 153
    SmsEncoding.UNICODE -> if (len <= 70) 1 else (len + 66) / 67
}
```

## 6. Message Validation
`EmergencySmsFormatter.validate()` inspects an `EmergencySmsMessage` and produces an `EmergencySmsValidationResult`:
- `VALID`: Text is non-blank, non-empty, and fits within a single segment (`segmentCount == 1`).
- `REQUIRES_MULTIPART`: Text is valid and non-empty, but exceeds 1 segment.
- `INVALID`: Text is blank or empty.

## 7. Privacy and Sensitive Data Protection
Medical and rider details constitute sensitive personal health information:
- The formatter is pure Kotlin with no logging side effects.
- Neither rider names, medical diagnoses, allergies, nor phone numbers are ever logged to Android Logcat or external services.
- The resulting message remains purely in-memory.

## 8. Absence of Telephony APIs
This layer only builds and validates message contents:
- Does NOT access `SmsManager` or `TelephonyManager`.
- Does NOT trigger device telephony calls or send SMS.
- Does NOT request `android.permission.SEND_SMS`.
- Does NOT access GPS hardware or network services.
