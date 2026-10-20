# Google Drive sync: setup and feasibility spike

Status: **feasibility gate NOT passed.** Nothing in this document has been run against real Google
accounts yet. The production sync worker must stay disabled until every row in the evidence
checklist below is observed and recorded.

## Scope rule

The app requests only `https://www.googleapis.com/auth/drive.file`. There is no fallback to
`drive`, `drive.readonly`, or any other broad scope. If the spike shows `drive.file` cannot see
peer files, the design changes; the scope does not.

Code lives in `app/src/main/kotlin/app/kinjar/data/sync/drive/`:

- `DriveAuthorizer`: Play services `AuthorizationClient` wrapper (picker request, silent token,
  clear, revoke). Implements `DriveTokenSource`.
- `DriveTransport`: OkHttp client for Drive REST v3 (folder create, paginated list, bounded
  download, multipart create, in-place update, share by email).

Library: `com.google.android.gms:play-services-auth:22.0.0`, the version listed in
<https://developer.android.com/identity/authorization>. Picker integration follows
<https://developers.google.com/workspace/drive/picker/guides/desktop-mobile-picker>.

## Google Cloud setup (user-run, personal account)

1. Create a Google Cloud project dedicated to this app.
2. Enable **Google Drive API** and **Google Picker API**.
3. Google Auth Platform, Branding / Audience:
   - User type **External**.
   - Publishing status **Testing** for the spike. Add both spike accounts as test users.
     Expect to re-consent if the spike runs longer than a week. Follow the consent screen
     shown by Google; warning behaviour depends on the project's verification status.
   - **In production** removes the test-user list and the seven-day expiry. `drive.file` is
     listed as a non-sensitive scope, but check the current verification criteria in the Cloud
     console before publishing; do not publish just for the spike.
4. Data Access: add only the `.../auth/drive.file` scope.
5. Clients: create an **Android** OAuth client for the test APK, package name `app.kinjar.synctest`.
   The test app installs beside the production app and has separate data. The production client
   later uses package name `app.kinjar` and its release signing certificate.
   - Debug SHA-1: `keytool -list -v -keystore ~/.android/debug.keystore -alias androiddebugkey -storepass android`
   - Release SHA-1: `./gradlew :app:signingReport` with the `BUDGET_*` signing variables set
     (see `docs/releasing.md`). If the app is ever distributed through Play App Signing, add the
     Play app-signing certificate SHA-1 as well.

   Android clients are matched by package name and certificate, so no client ID or secret goes
   into the source tree. A mismatch shows up as an `ApiException` from `authorize()`
   (status code in the message, for example 10 `DEVELOPER_ERROR`).

## Current test APK

The 2026-10-20 test build `app/build/outputs/apk/debug/kinjar-debug.apk` is named
**kinjar sync test** and installs separately from the regular app. Use package
`app.kinjar.synctest` and this APK signing certificate SHA-1:

```text
A4:28:2C:74:D6:8D:17:5B:A4:00:08:3B:F5:D6:08:70:C3:F5:3E:7B
```

After Google Cloud setup:

1. Install the test APK on both phones. Complete its initial setup with test names.
2. Open Settings, **Drive connection test**, then **Connect Google account** on each phone.
   Use your own account on the first phone and your wife's account on the second.
3. First phone: **Create test folder (first phone)**, enter the other Google email, then
   **Share test folder** and **Write test file**.
4. Second phone: **Choose shared test folder**, select the `kinjar-sync-test-...` folder, then
   **Read both phones' test files** and **Write test file**.
5. First phone: read both files. Use **Write a new test file** on each phone, then read on the
   other phone without selecting the folder again.
6. **Write test file** updates the current file. Verify the other phone sees the same ID and
   a newer version. Force-stop and reopen the test app, then repeat reading.

Report which step failed and the displayed error code, if any. Never share tokens or recovery keys.
This screen uploads synthetic text only. It does not synchronize financial data. Background-worker
and token-expiry acceptance still require separate checks before the full feature can be enabled.

## Security integration gates

Crypto review completed on 2026-10-18. Positive generations are enforced, and ambiguous
`UnrecoverableKeyException` failures remain retryable instead of authorizing key replacement.
Before production integration:

- Add and device-test directory fsync after atomic key/identity file rename.
- Provide singleton stores; never replace identity automatically after a failed Keystore lookup.
- Authenticate a cloud file before enrolling a scanned/recovered key or replacing local data.
- Protect QR/recovery screens and sensitive clipboard contents.
- Test real Keystore restart, locked-screen background access, invalidation and copied-file rejection.
- Align transport and encrypted-file size limits, and persist generation and recovery retention state.

## Integration contract for the lead

```kotlin
val authorizer = DriveAuthorizer(context) { savedAccountOrNull }
val launcher = rememberLauncherForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { result ->
    runCatching { authorizer.resultFromActivity(result.resultCode, result.data) }
        .onSuccess { granted -> /* granted.pickedFileIds, granted.account; use granted.accessToken immediately, never store it */ }
        .onFailure { /* DriveException.Cancelled or DriveException.AuthRequired */ }
}
scope.launch {
    when (val outcome = authorizer.authorize(authorizer.pickerRequest())) {
        is DriveAuthorizer.Outcome.NeedsResolution -> launcher.launch(outcome.request)
        is DriveAuthorizer.Outcome.Granted -> { /* already granted */ }
    }
}
val drive = DriveTransport(authorizer)
```

- `pickerRequest()` sets `PICKER_OAUTH_TRIGGER`, folder MIME filter, folder selection, `CONSENT |
  SELECT_ACCOUNT`, and `setOptOutIncludingGrantedScopes(true)`. The value passed to the Android
  resource parameters is not documented; `"true"` mirrors the desktop `trigger_onepick=true`
  parameter and must be confirmed by the spike.
- `accessToken()` (used by `DriveTransport`) never shows UI. Pending consent throws
  `DriveException.AuthRequired`; the caller must route the user back to an interactive request.
- On HTTP 401 the transport clears the cached token and retries once, then throws `AuthRequired`.
- `DriveException.Http.retryable` is true for 429, 5xx and Drive rate-limit 403s. Retry policy
  belongs to the worker.
- Limits: 1 MiB JSON responses, caller-supplied download limit, 5 MiB uploads (Drive's simple
  upload limit), 1000 listed children by default. Redirects are never followed. Drive IDs are
  validated as `[A-Za-z0-9_-]{1,256}` before use and in every response.
- No refresh token is requested or stored. No token, email, or response body is logged or put in
  exception messages.
- The account comes from `AuthorizationResult.toGoogleSignInAccount()`, which is deprecated in
  22.0.0. Whether it is populated after a picker grant is part of the spike.

## Two-account spike

Use two consumer Google accounts, A (folder owner) and B (peer), on two devices or two Android
user profiles. Use only synthetic placeholder files such as `spike-A-001.bin` containing
`synthetic spike payload A-001`. Never upload budget data, exports, or recovery keys.

1. A: interactive picker grant, then `createFolder("Budget sync spike")`.
2. A: `createFile("spike-A-001.bin", folderId, ...)`, then `shareWithEmail(folderId, B)`.
3. B: accept the share, run the picker, select the shared folder. Record whether
   `pickedFileIds` contains the folder ID and whether `account` is non-null.
4. B: `listChildren(folderId)` and `download` of `spike-A-001.bin`.
5. B: `createFile("spike-B-001.bin", folderId, ...)`.
6. A: `listChildren(folderId)`. Does A see `spike-B-001.bin` without picking it?
7. A: `createFile("spike-A-002.bin", folderId, ...)` after B's pick. B: list and download it
   without a new pick.
8. A: `updateFile` on `spike-A-001.bin`. B: same ID, new content, new `version`.
9. Token expiry: wait more than 60 minutes (or force a 401), then call the transport from a
    background path with no UI. Then `adb shell am force-stop app.kinjar.synctest`, relaunch, repeat.
10. Revoke: B removes the app at <https://myaccount.google.com/connections>, then calls the
    transport. Also test `DriveAuthorizer.revoke(account)` on A.
11. Cancel: start the picker and press Back; separately cancel on the consent screen.
12. Unshare: A removes B from the folder; B lists and downloads.

## Evidence checklist

Record HTTP status and Drive `reason` only. No tokens, emails, or file contents in notes.

| # | Question | Observed (date, device, app version, result) |
|---|----------|----------------------------------------------|
| 1 | Picker opens with `PICKER_OAUTH_TRIGGER="true"` and shows shared folders | |
| 2 | B's grant returns the folder ID in `picked_file_ids` | |
| 3 | `account` is populated after a picker grant | |
| 4 | B lists and downloads A's file created before B's pick | |
| 5 | A sees B's newly created file without picking it | |
| 6 | B sees A's file created after B's pick, without re-picking | |
| 7 | In-place update keeps the file ID and is visible to the peer | |
| 8 | Silent `accessToken()` works after token expiry with no UI | |
| 9 | Silent `accessToken()` works after process death | |
| 10 | Revoke from Google account settings yields `AuthRequired` (record HTTP code) | |
| 11 | `revoke(account)` yields `AuthRequired` on next call | |
| 12 | Back on picker and consent yields `DriveException.Cancelled` | |
| 13 | Unshared peer gets HTTP 403/404 (record reason), no data leak | |
| 14 | Testing-mode seven-day expiry behaviour, if observed | |

Rows 4 to 9 are the gate. Any failure there stops sync integration until the plan is revised.
