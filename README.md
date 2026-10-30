# FCM Keeper

A native Kotlin / Jetpack Compose notification inbox built for the Firebase Notification Inbox assignment. Uses standard Material icons, Android resources, Material 3 components and system fonts. No custom image assets are required.

## App screenshots

Screenshots captured on a physical Android device. The displayed messages are local test notifications; the settings screen shows the Firebase connection status.

<table>
  <tr>
    <th>Inbox and filters</th>
    <th>Inbox actions</th>
  </tr>
  <tr>
    <td><img src="docs/media/screenshots/inbox.jpeg" alt="Notification inbox with search, read filter, sorting and saved messages" width="280" /></td>
    <td><img src="docs/media/screenshots/inbox-actions.jpeg" alt="Inbox menu showing mark all as read and clear inbox actions" width="280" /></td>
  </tr>
  <tr>
    <th>Notification details</th>
    <th>Settings and Firebase connection</th>
  </tr>
  <tr>
    <td><img src="docs/media/screenshots/notification-details.jpeg" alt="Notification details with banner placeholder, read state, timestamp and full description" width="280" /></td>
    <td><img src="docs/media/screenshots/settings.jpeg" alt="Settings showing enabled alerts, Firebase connection and device token controls" width="280" /></td>
  </tr>
</table>

## Workflow video

[Open the app workflow video](docs/media/workflow.mp4) · MP4, approximately 10.8 MB.

If GitHub does not display a video player, use the file page's download button to watch the recording locally.

## Features

- FCM messages are saved in local SQLite before an Android system alert is posted.
- Exact channel: ID `assignment`, name `Assignment`; icon, title and expandable description.
- System notification tap opens the **inbox**, including when details are already open. It does not mark an item read.
- Inbox item tap opens **details** and persists its read state. Banner, full title, description and localized timestamp appear in details.
- Persistent history, primary-key deduplication, unread count, mark all read, individual delete and clear inbox (with confirmations).
- All / Unread / Read filters, case-insensitive title/body search, newest/oldest sorting, friendly timestamps.
- Loading, empty, no-results, missing-detail and storage-error states; image loading/failure placeholders; light/dark themes; scrollable layouts and selectable detail text.
- Android 13+ notification permission request and a settings route after denial. Disabled alerts never prevent local persistence of delivered messages.
- Settings show Firebase connection state and let you copy the device token. Debug builds offer an explicitly labeled **local** test notification.

## Build and run

Requirements: Android Studio with JDK 21 (its bundled JBR works; the checked-in daemon toolchain selects Java 21), Android SDK 36, accepted SDK licenses and internet for the first Gradle sync. Min SDK 24. The checked-in Gradle wrapper is 9.3.1 and AGP is 9.1.1. AGP provides Kotlin 2.2.10; the Compose compiler plugin matches it.

1. Open this directory in Android Studio. Set the Gradle JDK to the bundled JBR and sync.
2. Ensure `local.properties` points to your Android SDK (Android Studio normally creates it).
3. Connect Firebase as described below, or build immediately to explore local inbox behavior.
4. Run `gradlew.bat :app:assembleDebug` on Windows (`./gradlew :app:assembleDebug` elsewhere).
5. Install `app/build/outputs/apk/debug/app-debug.apk`, or run the app from Android Studio.
6. Enable notifications using the inbox prompt. In a debug build, open Settings > Create local test notification to exercise persistence and the system-alert flow.

Release builds omit developer test controls and enable shrinking. Configure your own signing key for a distributable release; the debug APK is installable for evaluation.

## Firebase setup (required for real push delivery)

1. Create/select a project in the Firebase console and register an Android app with package `com.example.fcmkeeper`.
2. Download its `google-services.json` to **`app/google-services.json`**. This file is git-ignored. Never place a service-account private key in the Android app.
3. Rebuild and install. The Google Services Gradle plugin is applied only when this file exists. Without it, the app runs locally and explicitly reports that Firebase is not connected.
4. Use a device/emulator with Google Play services and internet. Open Settings in the app and copy the FCM device token. Tokens can rotate; refresh/copy the current token when needed.
5. From a trusted server or authenticated development environment, send the example in `docs/fcm-message.json` to:
   `POST https://fcm.googleapis.com/v1/projects/YOUR_PROJECT_ID/messages:send`
   with `Authorization: Bearer YOUR_SHORT_LIVED_OAUTH_ACCESS_TOKEN` and `Content-Type: application/json`.
6. Replace the token placeholder and use an actual reachable HTTPS image URL if testing a real banner. The example.com URL intentionally exercises the fallback. All `data` values must be strings.

Use a **data-only** HTTP v1 message (no top-level `notification` block) for this assignment. High priority is appropriate here because each new message immediately produces a user-visible alert when permission allows it. Do not embed server credentials or access tokens in this repository.

### Foreground / background behavior

- Data-only messages: `FirebaseMessagingService` saves and posts in both foreground and background. A short synchronous database write runs on the service's worker thread. Image loading is deferred to the UI, so it cannot delay receipt handling.
- Mixed `notification` + `data` messages: in the background Firebase displays the alert itself and bypasses `onMessageReceived`. Custom data is recovered on tap. Such messages cannot reliably enter this local inbox before they are tapped; notification-only messages also lack the assignment's custom fields. Firebase Console composer messages are therefore **not** a complete end-to-end test of this assignment. Use the supplied data-only payload.
- Android may delay delivery due to Doze, network conditions, OEM battery restrictions, quotas or force-stop. After force-stop, reopen the app before testing. The app cannot guarantee receipt of messages the OS/FCM never delivers.
- System notifications intentionally lead only to the inbox. An item is marked read only after it is selected in the inbox.

References: [Firebase receive behavior](https://firebase.google.com/docs/cloud-messaging/android/receive-messages), [message priority](https://firebase.google.com/docs/cloud-messaging/android/message-priority), [Android notification permission](https://developer.android.com/develop/ui/views/notifications/notification-permission).

## Architecture and decisions

- `data/InboxNotification.kt`: immutable model, pure payload parser, search/filter/sort logic.
- `data/InboxRepository.kt`: application-scoped SQLiteOpenHelper repository exposing a StateFlow snapshot. Serialized writes, parameterized queries, a primary key and timestamp index enforce consistent state. SQLite avoids adding a code-generation plugin for this small, single-table app.
- `messaging/`: FCM transport and notification-channel / notification publishing responsibilities.
- `ui/InboxViewModel.kt`: lifecycle-aware state, off-main-thread persistence, saved selected ID and user-facing error messages.
- `ui/KeeperApp.kt`: Compose inbox, details, settings, confirmation dialogs and image fallbacks. Compose saved state preserves filter/search/sort; SavedStateHandle preserves detail navigation across recreation.
- `KeeperApplication`: small manual dependency container. No unnecessary DI framework.

Libraries: AndroidX Core/Activity/Lifecycle, Compose UI/Material 3/Material icons, Firebase Messaging (BoM), Coil 2 for bounded/cache-backed asynchronous image loading, Kotlin coroutines/StateFlow, JUnit and Compose instrumentation testing.

## Payload rules and assumptions

- `id` is the deduplication key. **First delivery wins**: repeat IDs do not overwrite the original contents/read state or post another system alert. Delete/clear removes the row, so a future delivery of that ID is treated as new.
- A missing/blank ID falls back to the FCM message ID; if neither exists, a UUID is generated. Identical malformed messages without either ID cannot be deduplicated reliably.
- Missing title/body become `New notification` / `No description provided.` Notification fields provide fallback values in foreground mixed messages.
- Timestamp is epoch **milliseconds**, matching the assignment. Missing/non-numeric/non-positive/out-of-range values use receipt time. Future times display an absolute date, never a negative relative age.
- Only well-formed HTTPS banners are loaded. Missing, unsafe, malformed or failed images show standard Material icon placeholders while text remains available. HTTP images are deliberately not enabled through cleartext exceptions.
- State is local to this installation; app data clearing/uninstall removes the inbox. Backup is disabled. No backend synchronization or infinite retention service is implied.
- Notification tags use the full payload ID rather than its hash, avoiding collisions between unrelated IDs.
- UI labels are English; dates use device locale/time zone. Large text and long bodies can scroll. The source supports RTL layout; full translation is not included.

## Validation

```text
gradlew.bat :app:testDebugUnitTest :app:lintDebug :app:assembleDebug
gradlew.bat :app:connectedDebugAndroidTest
```

The second command needs a connected Android device/emulator. Unit tests cover complete/malformed payloads, fallback values, timestamp and URL validation, search, filters and ordering. Instrumentation tests cover SQLite reopening, concurrent duplicate deliveries, read-state preservation, delete/clear, detail recreation, notification-intent navigation and deletion confirmation.

Manual acceptance checklist:

1. Send a fresh data-only message in foreground, background and after swiping the app from recents; inspect both tray and inbox.
2. Tap the tray alert from a cold start and while viewing another detail; confirm the inbox opens and unread state does not change.
3. Open an inbox item; return, rotate and restart the app; confirm the item remains read.
4. Resend its ID; verify one entry, no extra alert and unchanged read state.
5. Try missing title/description/timestamp, invalid dates and invalid/unreachable image URLs.
6. Deny notification permission on API 33+; receive a message and verify it is stored without a tray alert. Enable alerts in Settings and send a new ID.
7. Check unread filters/counts, search, sort, mark-all-read, delete cancellation/confirmation and clear cancellation/confirmation.
8. Test airplane mode, dark mode, landscape and larger system font sizes.

## Submission

Source is in this folder. The build creates the installable APK at the path above. Connect your Firebase configuration before producing the final push-enabled APK. Publish the source to your chosen GitHub repository after reviewing tracked files; Firebase configuration, local SDK paths, build output and signing keys are excluded. No remote repository is created or published automatically.
Execution results and remaining external checks are recorded in [docs/VALIDATION.md](docs/VALIDATION.md).
