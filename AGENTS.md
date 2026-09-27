# Agent instructions

## Project

!BrainRot is a local Android app (`com.ferrerkiwi.shortsguard`). Its Accessibility service watches only the official YouTube and Instagram apps. It backs out of YouTube Shorts and Instagram Reels, and scrolls Instagram Home back toward followed posts at the caught-up and suggested-post boundary.

Read `README.md` before changing behavior. The main code is in `app/src/main/java/com/ferrerkiwi/shortsguard/`; the Accessibility configuration is `app/src/main/res/xml/accessibility_service_config.xml`.

When the current conversation lacks context needed for a task, read the README and inspect the relevant architecture or code. If an earlier project decision matters and Codex chat-history tools are available, inspect only the relevant chats for this project. Do not review every project chat for routine requests.

## Implementation boundaries

- Keep Accessibility event filtering limited to `com.google.android.youtube` and `com.instagram.android` unless the user requests another app.
- Preserve the local privacy model: no network access, screenshots, saved screen content, analytics, or account access unless the user explicitly requests a change. Keep UI inspection in memory and narrow to the signals needed for each feature.
- Prefer specific Accessibility identifiers and visible UI evidence. When evidence is ambiguous, leave ordinary YouTube videos and Instagram posts alone.
- Scroll Instagram's main feed container for the caught-up rule; avoid scrolling nested post carousels. Check behavior after repeated and fast swipes.
- Keep the service description, in-app explanation, README, and tests aligned with changes to behavior or permissions.
- Preserve unrelated uncommitted work. After a prompt results in repository changes, run the relevant checks, commit the completed changes, and push them to the configured GitHub remote before finishing. Include older uncommitted changes only when they belong to the requested work. If a push fails, report why and leave the local commit intact. Read-only prompts do not require a push.

## Build and verification

Use JDK 17 or 21. On this Mac, Java 17 is available through `/usr/libexec/java_home -v 17`; the default Java 24 does not work with this project's Gradle setup.

```sh
JAVA_HOME=$(/usr/libexec/java_home -v 17) ./gradlew testDebugUnitTest assembleDebug
```

For Accessibility changes, unit tests are only a first check. Verify on an Android device when available: Shorts and Reels should back out, normal videos and posts should remain usable, and Instagram Home should return from the caught-up or suggested-post boundary even after repeated swipes. Instagram and YouTube can change their UI identifiers, so calibrate against the current device hierarchy when a rule stops working.
