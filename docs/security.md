# Security

## App lock

App lock is optional and off by default. Turn it on in Settings with the App lock switch.

### What it does

- When enabled, the app starts locked after every cold start, including after Android kills the process.
- The app locks again when it goes to the background (Home, Recents, another app, back out of the app) and when the screen turns off.
- Unlocking uses the Android system prompt only. The app never sees, collects or stores fingerprints, PINs, patterns or passwords.
  - Android 11 and newer: a Class 3 (strong) biometric or the phone PIN, pattern or password.
  - Android 8 to 10: a Class 3 biometric prompt, bound to an Android Keystore key so a weaker sensor cannot satisfy it, with a "Use PIN, pattern or password" button that opens the system credential screen. Devices without a strong biometric go straight to the credential screen.
- Turning the lock on and turning it off both require a fresh successful authentication.
- Cancelled, failed or unavailable authentication keeps the app locked. The lock is never turned off automatically. If the phone screen lock is removed while app lock is on, the app stays locked until a screen lock is set again.
- While locked, budget screens are not composed at all, so nothing is exposed to accessibility services. Navigation and unsaved form input are restored after unlocking.
- While enabled, the window uses `FLAG_SECURE`: the Recents thumbnail is blank and screenshots and screen recording are blocked.
- The enabled setting is stored only on this device, in app-private preferences. It is not part of the family event log and does not sync.

### Limits

App lock is an access gate for the user interface. It is not encryption.

- Financial data is stored in a plain, unencrypted SQLite database in the app's private storage. Protection of that file comes from the Android application sandbox and the phone's own storage encryption, not from app lock.
- Anyone who knows the phone PIN, pattern or password can unlock the app.
- Rooted or otherwise compromised devices, debuggable builds, ADB access with root, and forensic extraction of an unlocked phone are outside the scope of app lock.
- In split screen or freeform windows the app stays visible while another window has focus. It locks only when the activity stops.
- The app opts out of Android backups with `allowBackup="false"`. Device-to-device transfer behavior can vary by manufacturer.

### Phone acceptance steps

Run on a real phone, ideally one on Android 11+ and one on Android 8 to 10.

1. Settings, turn App lock on. The system prompt appears. Cancel it: the switch stays off. Turn it on again and authenticate: the switch is on.
2. Press Home, reopen the app: the lock screen shows and the prompt opens once. Cancel it: the app stays locked and the prompt does not reopen until Unlock is tapped.
3. Unlock, open Add, type an amount, press Home, reopen and unlock: the Add screen and the typed amount are still there.
4. While the prompt is open, press Home, then reopen from Recents: the app is locked. The Recents thumbnail is blank.
5. Try a screenshot inside the app: it is blocked.
6. Turn the screen off and on while the app is open: the app is locked.
7. Rotate the phone and switch the language in Settings while unlocked: the app stays unlocked. Rotate while the prompt is open: the prompt stays usable.
8. Android 8 to 10: tap "Use PIN, pattern or password", press Home on the credential screen, reopen and cancel: the app stays locked.
9. Force stop the app (or `adb shell am kill rs.budget` while in the background) and reopen: the app is locked.
10. Settings, turn App lock off: authentication is required, and cancelling keeps it on. On Android 8 to 10, cancelling the separate credential screen also leaves the app locked.
11. With App lock on, remove the phone screen lock in phone settings, reopen the app: it stays locked with a message to set a screen lock.
