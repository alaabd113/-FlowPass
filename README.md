# FlowPass Mobile 1.2.0

Android WebView companion for Google Flow.

## 1.2.0 updates
- Removed the browser-status card and the visible page URL from the main screen.
- Kept navigation, refresh, and all controls inside the side drawer.
- Added an independent, persistent Desktop mode switch.
- Added an independent, persistent FlowPass engine switch.
- Removed the redundant "Open Flow in desktop mode" action.
- Retained local email autofill support on Google's account sign-in page.
- Tightened the native email-saving bridge so it accepts values only on the exact HTTPS `accounts.google.com` origin.
- Kept WebView cookies enabled and flushed them when the app pauses or Flow loads.
- No password storage and no automatic access to Gmail inboxes.

## Google sign-in note
Google may reject sign-in attempts made inside embedded WebViews with an "This browser or app may not be secure" message. This is a Google sign-in restriction, not a visual app error. FlowPass does not bypass that restriction. The app can remember an email entered on the supported sign-in page, but it cannot import Google passwords, silently use device accounts, or read Gmail messages without a separate official authorization flow.

## Build
Use JDK 17 and Android Gradle Plugin 8.7.3. Run `gradle assembleDebug` or open the project in Android Studio.

## Compatibility
Desktop mode changes the browser user-agent and viewport request. Google Flow's desktop layout and features may still vary by device, account, region, and Google's current service behavior. The bundled FlowPass userscript depends on Google's response format and may require updates if that format changes. Verify behavior on a physical Android device.
