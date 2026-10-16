::: {align="center"}
# 🏍️ VahakVigil

### Your ride. Your safety net.

**A smartphone-based motorcycle crash detection and emergency response
Android application.**

```{=html}
<p>
```
`<img src="https://img.shields.io/badge/Platform-Android-3DDC84?style=for-the-badge&logo=android&logoColor=white" alt="Android"/>`{=html}
`<img src="https://img.shields.io/badge/Language-Kotlin-7F52FF?style=for-the-badge&logo=kotlin&logoColor=white" alt="Kotlin"/>`{=html}
`<img src="https://img.shields.io/badge/UI-Jetpack%20Compose-4285F4?style=for-the-badge&logo=jetpackcompose&logoColor=white" alt="Jetpack Compose"/>`{=html}
`<img src="https://img.shields.io/badge/Status-Working%20Prototype-orange?style=for-the-badge" alt="Working prototype"/>`{=html}
```{=html}
</p>
```
**VahakVigil is being built to help close the gap between a possible
motorcycle crash and letting someone know help may be needed.**
:::

------------------------------------------------------------------------

## ✦ The problem

After a road accident, a rider may be injured or unable to call anyone.
If nobody sees what happened, family members or emergency contacts might
not know about the accident or the rider's location.

VahakVigil explores a practical approach: use sensors already inside an
Android phone to assess possible crashes and support an emergency alert
workflow.

> **The goal:** make it easier for riders to get an alert to their
> chosen contacts when they may not be able to do it themselves.

## 🚨 What VahakVigil is designed to do

  -----------------------------------------------------------------------
  Capability                          What it does
  ----------------------------------- -----------------------------------
  **Motion monitoring**               Reads motion data from the phone's
                                      accelerometer and gyroscope.

  **Crash assessment**                Uses motion analysis, confidence
                                      scoring and event checks to assess
                                      a possible crash.

  **Emergency countdown**             Gives the rider a chance to cancel
                                      an alert if they are safe.

  **Location support**                Uses available GPS information as
                                      part of the emergency workflow.

  **Emergency notifications**         Includes an SMS-based workflow for
                                      notifying configured contacts.
                                      Actual delivery depends on
                                      permissions, device settings and
                                      network availability.

  **Rider and bystander support**     Includes sensor information and a
                                      bystander screen with basic
                                      first-aid guidance.
  -----------------------------------------------------------------------

*VahakVigil is a working prototype under development. It is not a
substitute for emergency services and cannot guarantee that every crash
will be detected or every alert delivered.*

## ⚙️ How it works

``` text
┌────────────────────────────┐
│ Rider starts monitoring    │
└──────────────┬─────────────┘
               ↓
┌────────────────────────────┐
│ Accelerometer + gyroscope  │
│ collect motion signals     │
└──────────────┬─────────────┘
               ↓
┌────────────────────────────┐
│ Motion analysis and        │
│ confidence-based checks    │
└──────────────┬─────────────┘
               ↓
┌────────────────────────────┐
│ Possible crash qualifies?  │
└──────────────┬─────────────┘
               ↓
┌────────────────────────────┐
│ Emergency countdown        │
│ Rider can cancel if safe   │
└──────────────┬─────────────┘
               ↓
┌────────────────────────────┐
│ GPS + configured alert     │
│ workflow for contacts      │
└────────────────────────────┘
```

## 🧠 What makes the approach useful?

-   **Uses a phone the rider already owns** instead of requiring a
    dedicated crash-detection device.
-   **Checks more than one signal** rather than treating every sudden
    movement as a crash.
-   **Includes a cancellation window** to help prevent unnecessary
    alerts.
-   **Considers what happens after detection**, including location
    sharing and basic bystander guidance.
-   **Designed around practical limitations**, including phone
    placement, permissions, GPS availability and network coverage.

Crash-detection and emergency-alert products already exist. VahakVigil's
aim is to bring these parts together in an accessible Android app and
evaluate how well the approach works.

## 🛠️ Built with

```{=html}
<p>
```
`<img src="https://img.shields.io/badge/Kotlin-7F52FF?style=flat-square&logo=kotlin&logoColor=white" alt="Kotlin"/>`{=html}
`<img src="https://img.shields.io/badge/Jetpack%20Compose-4285F4?style=flat-square&logo=jetpackcompose&logoColor=white" alt="Jetpack Compose"/>`{=html}
`<img src="https://img.shields.io/badge/Android%20Sensors-3DDC84?style=flat-square&logo=android&logoColor=white" alt="Android sensors"/>`{=html}
`<img src="https://img.shields.io/badge/GPS%20Location-4285F4?style=flat-square" alt="GPS location"/>`{=html}
`<img src="https://img.shields.io/badge/SMS%20Workflow-555555?style=flat-square" alt="SMS workflow"/>`{=html}
```{=html}
</p>
```
-   **Language:** Kotlin
-   **UI:** Jetpack Compose
-   **Platform:** Android
-   **Motion data:** Accelerometer and gyroscope
-   **Location:** Android location services / GPS
-   **Emergency flow:** Countdown and configured SMS notification
    components

The repository's source code is the reference for the exact
implementation. The stack above describes the currently intended and
implemented project areas; it does not imply integrations that have not
been built.

## 📍 Current project status

**Stage: Working prototype**

Implemented areas include:

-   [x] Android application foundation and Compose UI
-   [x] Sensor-monitoring foundation
-   [x] Crash-detection logic and confidence assessment
-   [x] Emergency countdown workflow
-   [x] GPS location support
-   [x] Emergency notification components
-   [x] Sensor dashboard and bystander guidance
-   [x] Foreground monitoring service tested on a physical Android
    device

### What still needs validation?

-   [ ] Measure false alarms and missed detections through controlled
    tests
-   [ ] Test behaviour across different Android devices and phone
    placements
-   [ ] Verify GPS and message delivery under different network
    conditions
-   [ ] Gather feedback from riders in a safe, supervised evaluation
-   [ ] Document measurable results before making reliability claims

These checklists describe project status at the time this README was
written and should be updated as development progresses.

## 🌍 Who is it for?

VahakVigil is intended for:

-   Motorcycle and scooter riders
-   People who commute alone
-   Families and designated emergency contacts
-   Road-safety communities interested in accessible technology

The initial focus is on a functional prototype and controlled
validation. A formal field pilot or public deployment is not claimed.

## 🚀 Getting started

This is an Android Studio project.

1.  Clone the repository:

    ``` bash
    git clone https://github.com/manishchandraraturi/VahakVigil.git
    ```

2.  Open the project folder in **Android Studio**.

3.  Allow Gradle to sync and resolve the project dependencies.

4.  Connect a compatible Android phone or start an emulator.

5.  Build and run the app from Android Studio.

**Note:** Sensor behaviour and some device-dependent functionality are
best checked on a physical Android phone. Grant only the permissions
required by the app, and test emergency messaging with consent and care.

## 🔐 Safety and privacy notes

-   Location and emergency-contact information should be handled
    carefully.
-   Users should understand when monitoring is active and what
    information may be shared.
-   GPS and SMS functionality can be affected by permissions, signal
    strength, device settings and network availability.
-   Do not rely on this prototype as the only way to request emergency
    assistance.
-   Test alert workflows with consenting contacts before any real-world
    evaluation.

## 🗺️ Roadmap

-   [ ] Improve controlled crash-simulation coverage
-   [ ] Measure false-positive and missed-event rates
-   [ ] Test across more Android devices
-   [ ] Improve reliability and clarity of emergency notifications
-   [ ] Review privacy, permissions and battery behaviour
-   [ ] Run a small, safe user evaluation before considering broader use

## 🤝 Contributing and feedback

This project is under development. Feedback on usability, Android
compatibility, testing methods and road-safety needs is welcome.

Before reporting a bug, include the device model, Android version, steps
to reproduce the issue and relevant logs. Do not include personal
location data, phone numbers or other private information in public
issues.

## 👨‍💻 Project

**VahakVigil**\
Created by **Manish Chandra Raturi**\
Repository:
[github.com/manishchandraraturi/VahakVigil](https://github.com/manishchandraraturi/VahakVigil)

------------------------------------------------------------------------

::: {align="center"}
### Building toward safer rides, one test at a time.

*Made as an engineering project with a road-safety goal.*
:::
