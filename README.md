# !BrainRot

!BrainRot is a private, local-only Android accessibility helper. It observes only the
official YouTube app's visible accessibility UI and returns to the prior screen when a
conservatively detected Shorts player opens.

## Privacy boundary

- No `INTERNET`, account, storage, notification, VPN, root, screenshot, or installer permission.
- The accessibility service is package-filtered to `com.google.android.youtube` and checks that
  package again at runtime.
- UI text and identifiers are held only in memory during a single callback. The app has no logs,
  analytics, database, or preferences.

## Build locally

1. Install Android Studio and its current stable Android SDK (the project compiles against API 35).
   Use Android Studio's bundled JDK 17 or 21; the Mac's separately installed Java 24 is not
   supported by this Android Gradle Plugin version.
2. Open this folder in Android Studio and allow Gradle sync to fetch the declared build tools.
3. On the phone, enable Developer options and USB debugging, connect it by USB, then run the
   `app` debug configuration from Android Studio.
4. Open !BrainRot and use **Turn on !BrainRot** to enable the service in Android Settings.

The app uses Android's normal debug signing key when launched from Android Studio. It does not
need ReVanced, GmsCore, a modified YouTube APK, or a Google sign-in.

## Calibration and manual verification

The detector intentionally fails open when it cannot identify a Shorts player. On the target
phone, compare the accessibility hierarchy for a normal watch page and a Shorts player with
Android Studio's Layout Inspector or `uiautomator dump`, then update the narrow player-route
markers in `ShortsDetector` if YouTube exposes different identifiers.

Verify that Shorts entered from Home, search, subscriptions, and a deep link return immediately;
normal videos and a Home Shorts shelf must not trigger the guard.
