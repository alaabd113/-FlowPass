# FlowPass Mobile (Android Studio project)

Android WebView wrapper for the supplied FlowPass userscript, with a compact status dashboard and toggle.

## Build
1. Open this folder in Android Studio (recent version).
2. Allow Gradle sync; use JDK 17.
3. Build > Build APK(s), or run on an Android device.

## Notes
- App opens https://flow.google.com.
- The userscript is bundled at `app/src/main/assets/flowpass.user.js`.
- The supplied script's own diagnostics are logged to the WebView console.
- This is a starter project; test on device. Google Flow may change its response schema and the patch may stop working.
- The toggle currently controls the page flag; a strict injection gate may need additional implementation.
