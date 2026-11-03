# VahakVigil — Presentation Asset Pack

Dedicated presentation assets generated directly from the native Kotlin + Jetpack Compose codebase for high-resolution PowerPoint presentations.

---

## 1. Directory Structure

```
presentation-assets/
├── logo/
│   ├── app-logo.png             # 1024x1024 crisp transparent PNG (motorcycle emblem)
│   ├── app-logo-adaptive.png    # 1024x1024 full launcher icon with dual-tone background
│   ├── app-logo.svg             # Faithful scalable vector of adaptive icon (background + foreground)
│   ├── app-logo-mark.svg        # Faithful scalable vector of motorcycle mark (transparent background)
│   └── app-icon-preview.png     # 1024x1024 presentation preview on neutral background with soft shadow
├── screens/                     # Raw, high-resolution app screenshots from running app
│   └── overview-contact-sheet.png # Neatly arranged contact sheet of all screens
├── mockups/                     # Clean Android device mockups (neutral titanium frame)
│   ├── home-mockup.png
│   ├── emergency-mockup.png
│   └── bystander-mockup.png
├── capture_screenshots.ps1      # 1-click automated capture script for connected device (e.g. Samsung A55)
├── generate_mockups.py          # Python script rendering realistic Android device frames
├── generate_contact_sheet.py    # Python script building the multi-screen contact sheet
└── render_logo.py               # Vector rasterizer (supersampled Lanczos) for logo assets
```

---

## 2. Logo Assets (100% Native Vector Grounded)

All logo assets are generated directly from the project's source Android Vector Drawables:
- **Foreground Mark**: [`ic_launcher_foreground.xml`](file:///app/src/main/res/drawable/ic_launcher_foreground.xml) — ground line, rear wheel, front wheel, and chassis contour.
- **Background**: [`ic_launcher_background.xml`](file:///app/src/main/res/drawable/ic_launcher_background.xml) — `#12090F` and `#24131F` dual-tone gradient.
- **Rasterization**: Supersampled at 4× (4096×4096) and downsampled with high-order Lanczos interpolation to 1024×1024 for mathematical vector sharpness in PowerPoint.

---

## 3. Automated Device Screen Capture

To capture all live screens directly from a connected device (e.g., Samsung Galaxy A55):

1. Connect your phone via USB and ensure **USB Debugging** is enabled in Developer Options.
2. Run in PowerShell:
   ```powershell
   .\presentation-assets\capture_screenshots.ps1
   ```
3. The script will:
   - Ensure the latest debug APK is installed
   - Automatically navigate through Home, Live Ride, Medical Profile, Emergency Contacts, Ride History, Settings, and Profile
   - Save uncompressed PNG screenshots into `presentation-assets/screens/`
   - Automatically generate `mockups/` and `overview-contact-sheet.png`
