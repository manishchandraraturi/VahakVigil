# Google Maps setup

The app uses the existing `com.google.maps.android:maps-compose` integration and Google Maps SDK for Android. The SDK key is injected into the manifest from `MAPS_API_KEY` in the root `local.properties`; do not put the key in Kotlin, XML, Gradle files, or version control.

1. In Google Cloud, enable **Maps SDK for Android** on a project with billing configured.
2. Create an API key restricted to **Android apps**. Add the application ID `com.manish.vahakvigil` and the signing certificate SHA-1 fingerprints used for each build you install.
3. Add this line to the local, untracked root `local.properties` file:

   ```properties
   MAPS_API_KEY=YOUR_RESTRICTED_KEY
   ```

4. Sync Gradle and rebuild/reinstall the application. Check Google Cloud key restrictions, billing, and Android logcat if the map surface still shows an authorization error or blank tiles.

The configured key only proves that the manifest contains a value. Real road tiles require a valid restricted key, enabled API, billing, network access, and a device/emulator with Google Play services. This workspace does not currently have a configured Maps key, so tile loading has not been verified here.
