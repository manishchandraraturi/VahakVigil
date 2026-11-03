# GPS Location Provider Foundation

## 1. LocationProvider Abstraction
The `LocationProvider` interface (`com.manish.vahakvigil.domain.location.LocationProvider`) is a pure-Kotlin domain abstraction for retrieving a single location snapshot. It is completely decoupled from Android framework types and third-party SDKs:

```kotlin
interface LocationProvider {
    suspend fun getCurrentLocation(): EmergencyLocation
}
```

Implementations return an immutable `EmergencyLocation`:
- `EmergencyLocation.Available(latitude, longitude, accuracyMeters, timestampMs)`
- `EmergencyLocation.Unavailable`

## 2. Android Implementation (`AndroidLocationProvider`)
The platform implementation (`com.manish.vahakvigil.data.location.AndroidLocationProvider`) resides in the data layer and uses Google Play Services `FusedLocationProviderClient`:
- **Accuracy Priority**: Uses `Priority.PRIORITY_HIGH_ACCURACY` for the immediate current location query (`getCurrentLocation`).
- **Fallback**: If `getCurrentLocation` returns null or fails, it falls back to `getLastLocation`.
- **Cancellation**: Supports coroutine cancellation through `CancellationTokenSource`.

## 3. Permission Behavior
- The manifest declares only foreground location permissions:
  ```xml
  <uses-permission android:name="android.permission.ACCESS_FINE_LOCATION" />
  <uses-permission android:name="android.permission.ACCESS_COARSE_LOCATION" />
  ```
- Before querying `FusedLocationProviderClient`, `AndroidLocationProvider` checks whether either `ACCESS_FINE_LOCATION` or `ACCESS_COARSE_LOCATION` has been granted.
- If permission is denied or missing, it **safely returns `EmergencyLocation.Unavailable`** without crashing or throwing unhandled `SecurityException`s.
- No automatic permission prompt is shown by the provider itself.

## 4. Unavailable Location & Failure Handling
Any operational failure path gracefully returns `EmergencyLocation.Unavailable`:
- Permission denied
- Location hardware / provider disabled
- Location query timed out or returned null
- System `SecurityException` or unexpected platform error

The system never crashes due to absent or failed location services.

## 5. Coordinate Quality & Validation
Coordinates are strictly validated via `EmergencyLocation.isValidCoordinate(lat, lon)`:
- **Latitude**: Must be between `-90.0` and `+90.0` inclusive, non-NaN, and finite.
- **Longitude**: Must be between `-180.0` and `+180.0` inclusive, non-NaN, and finite.
- **Accuracy**: Populated from `Location.accuracy` only if available, non-NaN, and $\ge 0$. Never fabricated.
- **Timestamp**: Populated from `Location.time` only if $> 0$.

If coordinates are out of bounds or invalid (e.g. `NaN`, `Infinity`, or outside geographical ranges), the provider rejects them and returns `EmergencyLocation.Unavailable`.

## 6. Architecture & Dependency Direction
The architecture strictly enforces clean dependency flow:
```
UI Layer (Screens, HomeViewModel)
       ↓
Domain Layer (EmergencyDispatchDataCompiler, LocationProvider, EmergencyLocation)
       ↑ implements
Data / Platform Layer (AndroidLocationProvider, FusedLocationProviderClient)
```
- The domain layer contains zero imports from `android.location.*` or `com.google.android.gms.location.*`.
- Android and Google Play Services classes are quarantined entirely within `data/location`.

## 7. Privacy & Sensitive Data Safeguards
- **Zero Logging**: Latitude, longitude, horizontal accuracy, and timestamps are **never** logged to Android Logcat, analytics, or external services.
- **In-Memory Lifespan**: Coordinates are maintained solely in immutable data models during dispatch payload compilation.

## 8. Why Background Location is Intentionally Excluded
Background location (`ACCESS_BACKGROUND_LOCATION`) is omitted by design:
- VahakVigil does not track riders continuously in the background. Continuous tracking wastes battery, incurs unnecessary background wakeups, and raises severe privacy concerns.
- Location is only needed on-demand as a point-in-time snapshot when preparing emergency dispatch data.

## 9. Why GPS Does NOT Trigger Emergency Actions
GPS is strictly a descriptive data source, not an incident trigger:
- Having or not having GPS coordinates never influences crash detection or the emergency decision state.
- The emergency decision rule remains untouched:
  $$\text{EmergencyDecision} = \text{EMERGENCY\_CANDIDATE} \quad \text{AND} \quad \text{Countdown} = \text{EXPIRED}$$
- GPS availability cannot bypass, advance, or weaken the emergency decision and cancellation countdown safety gates.
