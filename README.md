# FlowPass Mobile 1.1.0

Android WebView companion for Google Flow.

## 1.1.0 updates
- Three-line menu opens a side drawer.
- FlowPass engine switch is inside the drawer and its state persists.
- Desktop Chrome user-agent and wide viewport request the desktop Flow experience.
- Google sign-in email is remembered locally and can be filled again on the Google sign-in page.
- WebView cookies are retained and flushed so a valid Google sign-in session can survive app restarts.
- Passwords are not stored by FlowPass.

## Build
Use JDK 17 and Android Gradle Plugin 8.7.3. Run `gradle assembleDebug` or open the project in Android Studio.

## Limitations
The desktop user-agent requests the desktop website but cannot guarantee every Flow feature works on a phone-sized display. Google may require a fresh sign-in, CAPTCHA, subscription, or supported-region check. The bundled userscript depends on Google's current response format and may need maintenance if Flow changes it. Test on a physical Android device before relying on it.
