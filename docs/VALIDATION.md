# Validation record

Validated on 2026-10-30 with JDK 21, Gradle 9.3.1 and the Android 11 / API 30 Google Play emulator.

## Automated results

- `:app:assembleDebug`: passed. Installable, debug-signed APK: `app/build/outputs/apk/debug/app-debug.apk`.
- `:app:testDebugUnitTest`: 6 tests passed, 0 failures/errors.
- `:app:connectedDebugAndroidTest --max-workers=2`: 6 tests passed, 0 failures/errors/skips.
- `:app:lintDebug`: 0 errors, 12 advisory warnings (dependency/target version updates and Kotlin extension suggestions).
- `:app:assembleRelease`: passed, including R8 optimization. The release APK is unsigned; configure owner signing before distribution.

Device tests cover database persistence after closing/reopening, concurrent duplicate IDs, preservation of read state, mark-all-read, delete/clear persistence, detail-screen recreation, system-notification intent navigation, deletion confirmation and immediate relative timestamps.

Debug APK SHA-256:
`B1C55C64C3041FFE458FE7BBC2377EB7A077B51074F1353E20B3CA871F76CE9A`

## Manual visual checks

Installed and launched the debug APK on the emulator. Reviewed the empty inbox, populated inbox, settings, image placeholder and detail screen in light/dark mode. Created a local test notification through the app and observed the actual Android heads-up alert with icon, title and description. Opening the inbox item displayed its full details and read state.

Visual review found and fixed a stale-clock issue on newly arrived notifications. The final device test suite includes an assertion that a new arrival displays `Just now`.

## Remaining external verification

- Real Firebase delivery requires the owner's `app/google-services.json`. No Firebase project credentials/configuration were supplied, so live foreground/background push delivery and token issuance have not been tested.
- Android 13+ runtime notification permission behavior is implemented but was not device-tested; the available emulator is API 30. Run the README acceptance checklist on a modern device as well.
- Release compilation/optimization passed; the unsigned release APK was not installed. Use the verified debug APK for current evaluation.
- The source has local Git commits. No GitHub remote has been created or published.

The dedicated test emulator was shut down after validation. Its AVD remains available as `FCMKeeper_Test_API30` for future test runs. Build reports are available under `app/build/reports/`.