# Google Drive sync: setup and feasibility evidence

Updated: 2026-10-22. Status: **feasibility gate NOT passed; production sync stays disabled**.
The [one-QR implementation plan](plans/2026-10-22-one-qr-sync.md) defines the finished product
and its later protocol, integration, UI and recovery gates.

## What is known

| Evidence | Result | Boundary |
| --- | --- | --- |
| User-reported two-account folder experiment, recorded 2026-10-22 | Cross-account child listing failed: each account saw only its own file. | Exact device/build/date of execution and HTTP outcomes were not supplied. Do not invent them. Folder selection is not proof of access to peers' child files. |
| Code at `81eb6eb` | Account identity now comes from Drive `about.user.emailAddress` with the exact newly granted token. | Code and MockWebServer coverage, not evidence of completed cross-account pairing. |
| Exact-file picker and direct peer-file reads | Pending device proof. | Knowing a file ID or having a Drive ACL is not a substitute for verifying the app's exact-file grant. |
| Encrypted/authenticated `appProperties` pairing mailbox on owner's file | Proposed, unproven across accounts and concurrent updates. | Private-property visibility, writable roles, capacity and content isolation must pass F3-F5 below. |
| Finished family sync | Not implemented/accepted by this spike. | Synthetic text, fake engine adapters and isolated crypto tests cannot prove the complete product. |

The old folder-first test is historical negative evidence. Do not repeat it as the release
acceptance path or report its empty checklist as if no real experiment occurred.

## Scope and file contract

Request only `https://www.googleapis.com/auth/drive.file`, with granted-scope accumulation
disabled where supported by the authorization request. Never fall back to `drive`,
`drive.readonly`, metadata-wide access or `appDataFolder` sharing. Use two existing, separate
Google accounts and no new server. Google's recommended per-file scope works with files
created by the app or granted through supported selection flows; it does not establish this
particular two-account design without testing. [Drive scope guide](https://developers.google.com/workspace/drive/api/guides/api-specific-auth)

Each active phone owns one fixed ordinary Drive file. Direct ID lookup replaces folder listing.
The owner's file may also hold the pairing mailbox in `appProperties`; never allocate a hidden
mailbox/snapshot file. Only a file's owner phone uploads its financial ciphertext. Peer app
operations may change approved mailbox metadata only. A future third member requires directed
grants for every peer file, not merely an owner invitation.

A Drive writer may have permission to modify content as well as metadata. The app must restrict
its own operations, authenticate all inputs and tolerate corruption, but cannot promise that
Google enforces metadata-only peer access. Review this risk and temporary-role cleanup at G1.

## Google Cloud and test build setup (user-run)

The user operates Google accounts and the Cloud console. Do not sign into accounts on their
behalf. Use synthetic data only. Never copy tokens, invitation secrets, recovery material,
emails or real financial contents into logs, screenshots or the evidence ledger.

1. Use a dedicated test Google Cloud project. Enable Drive API and Google Picker API.
2. Configure Google Auth Platform for the test audience and add both test accounts when required.
   Configure only `drive.file`. Record re-consent/expiry behavior observed for this project's
   publishing status; do not assume Testing and Production behave identically.
3. Register an Android OAuth client matching package `app.kinjar.synctest` and the actual
   installed APK's signing SHA-1. Production uses `app.kinjar` and its release certificate.
   Android clients match package plus certificate; do not embed client secrets in source.
4. Keep testing and production configurations distinct, and review production consent/branding
   requirements before release. A debug mailbox visibility result must be rechecked for the
   intended production OAuth app identity. [OAuth production guidance](https://developers.google.com/identity/protocols/oauth2/production-readiness/policy-compliance)
5. Lead builds/validates the combined branch. Test APK path is
   `app/build/outputs/apk/debug/kinjar-debug.apk`; current build configuration adds
   `.synctest` and `-sync-test`. Do not infer the installed artifact's date or SHA from its name.
   Record its commit/version and device OS privately before testing.
6. Install beside the regular app and initialize with synthetic names/data. Open Settings,
   Drive connection test. Labels/actions may change in the concurrent phase 1 implementation.

Historical debug certificate recorded for the 2026-10-20 test build:

```text
A4:28:2C:74:D6:8D:17:5B:A4:00:08:3B:F5:D6:08:70:C3:F5:3E:7B
```

Verify the certificate against the actual test artifact before registering it; this document
does not attest a rebuilt APK uses that certificate. See [releasing](releasing.md) for signing.
Do not run a second Gradle build while the implementation worker is building.

## Current code contract and provisional phase 1 work

At inspected baseline `81eb6eb`:

- `DriveAuthorizer` wraps Play services authorization. `Outcome.Granted` carries an access
  token and picked IDs, **not an account**. `DriveTestViewModel.accept` calls
  `DriveTransport.authorizedAccountEmail(granted.accessToken)` and pins the resulting account.
  That request uses `GET drive/v3/about?fields=user(emailAddress)`; it must not silently select
  another saved account. An unavailable identity fails closed.
- `pickerRequest()` currently configures folder selection. Phase 1 must prove exact-file
  selection. Do not copy this folder configuration into production onboarding. Google's
  mobile picker documentation describes the flow; the actual Android parameters and returned
  IDs must be validated on devices. [Mobile Picker guide](https://developers.google.com/workspace/drive/picker/guides/desktop-mobile-picker)
- `DriveTransport` supports metadata, folder/listing, bounded download, create, content update
  and email sharing. Existing metadata lacks the ownership/capability/private-property evidence
  needed by the new protocol. New operation names and models remain provisional until reviewed.
- Silent token acquisition never launches UI. Ordinary transport calls invalidate/retry a 401
  once, then require foreground authorization. The direct grant-identity lookup instead
  invalidates a rejected token and fails without retrying through another account.
- Existing retryable HTTP classification covers 429, 5xx and rate-limit 403s. Worker retry
  policy is not implemented here. Cancellation must propagate.
- Current limits: 1 MiB JSON responses, caller-bounded downloads, 5 MiB upload bound, default
  1000 visible children for the historical listing method. Redirects are disabled and Drive
  IDs are validated. Reconcile limits with 16 MiB engine/history/cipher defaults before integration.
- No refresh token is requested/stored. Keep access tokens in memory. Safe diagnostics contain
  only sanitized error category, HTTP status and Drive reason, never raw response bodies.
  Private local account selection is distinct from logging account data.

Phase 1 is a synthetic exact-file/share/mailbox debug harness only. It does not enroll a family,
show production pairing QR codes, transfer real keys or synchronize budgets. Opaque synthetic
fixtures can exercise the planned mailbox size without implementing the secure protocol early.
Review the concurrent implementation before turning these requirements into concrete API calls.

## Two-account exact-file experiment

### Current diagnostic screen

The new screen replaces folder selection with one synthetic file per phone. Old folder probes
are not migrated or deleted. This harness exchanges file IDs manually to isolate Google behavior;
the finished pairing flow must replace manual IDs with the approved QR and authenticated mailbox.

1. On both phones, connect the intended account and create the phone's test file.
2. On each phone, share its file with the other Google email using the screen's share action.
3. Copy each phone's file ID to the other phone's peer-ID field. Approve that exact file through
   Google. No family keys or budget data are involved.
4. Read the peer file on both phones, update each own file, then read again from the other phone.
   Verify the write counter changes while the file IDs remain unchanged.
5. Send a request from one phone, read the mailbox on the other, remove that request there,
   then read the peer file from the sender to verify its request is gone. Repeat in reverse.
6. Restart both apps and repeat reads. Test revocation/reconnect with the same account: saved
   IDs remain, and missing access must require repair/re-grant rather than silent new files.
   Trashed or unknown-status probes must be rejected before reads or mutations.

This first harness tests one short synthetic mailbox value, not full encrypted chunk framing,
maximum-size messages, role downgrade, ownership-email verification or production consent.
Passing these steps is preliminary evidence only; F1-F7 below retain their full requirements.

Use account A on owner's phone and account B on peer's phone, under the same test app configuration.
Use logical labels A-file/B-file in notes; never record actual account addresses or file contents.
Real IDs may be compared on-device but should not be pasted into shared diagnostics.

1. Connect A and B. Resolve identity with each exact grant token. Verify privately that the
   intended account was selected. Wrong-account and cancel results must not advance state.
2. A creates/resumes one synthetic file A-file. Enter B's expected email privately and create
   a restricted ACL on A-file before showing an invitation-like test flow. B creates/resumes
   B-file and restricts its sharing to A. No public links or folder-child discovery.
3. B selects A-file with Google's exact-file picker; A selects B-file. Validate each returned ID,
   current token account, ownership and required permissions. Try the wrong file, a similarly
   named file, a folder and another account. Labels are not authorization.
4. Read each peer file directly by registered ID. Update each phone's own file repeatedly and
   verify the peer sees new synthetic content while both IDs remain unchanged.
5. B writes opaque synthetic request chunks to A-file's `appProperties`. A reads them, writes
   response chunks, and B reads them and writes an ack. Compare exact bytes locally. Exercise
   maximum-size serialized fixtures for the planned signed/encrypted request, response and ack.
6. Repeat with metadata interleavings, duplicate requests, dropped responses, partial chunk sets,
   deletion and A's content upload. Compare synthetic media checksums locally before/after
   metadata-only operations and confirm other slots/properties survive. Content updates must
   not clear the mailbox. The app must never issue a media upload to its peer's file.
7. Test minimum roles separately: peer read, temporary mailbox write, and owner revision access.
   After simulated durable ack, clean only completed mailbox entries and downgrade temporary
   permission if applicable. Verify continued direct reads; do not assume revision access survives.
8. Kill/restart both apps; renew expired/rejected tokens through silent paths; repeat reads and
   metadata operations. Cancel picker/consent, revoke application access, remove file permission,
   trash a test file and switch accounts. Persisted IDs must never be silently reassigned.
9. Record sanitized results in F1-F7. A device operator may stop at any failure. Do not collect
   secrets to diagnose it or broaden OAuth scope to make it pass.

Google documents private `appProperties` as app-restricted metadata, not as encryption. Limits
include 30 private properties per app per file, 100 total custom properties, and 124 UTF-8 bytes
for each key plus value. Budget actual encoded fixtures, chunk keys and unrelated reserved
properties; reject overflow before network writes. Cross-account visibility under the intended
OAuth configuration still needs device evidence. [Custom properties guide](https://developers.google.com/workspace/drive/api/guides/properties)

## Evidence ledger and stop rules

Record date, test commit/version, device/Android identifiers (non-personal), test configuration,
PASS/FAIL and sanitized HTTP status/reason when available. A checklist instruction is not an
observation. All pending rows below remain unverified until a device operator supplies evidence.

| ID | Required evidence | Status |
| --- | --- | --- |
| H1 | Historical folder experiment: A and B each list only their own child file. | FAILED cross-account discovery, user report recorded 2026-10-22; execution metadata unavailable. |
| C1 | Identity lookup uses exact grant token and rejects missing/rejected identity without changing accounts. | Code/tests present at `81eb6eb`; device rerun pending. |
| F1 | A/B exact-file picker grants and direct reads both directions; exact account/ID/ownership checks; wrong file/account/cancel rejected. | PENDING |
| F2 | Own content updates preserve each fixed ID; peer reads updates without re-picking; no reliance on listing. | PENDING |
| F3 | B reads/writes intended A-file private properties and A/B complete synthetic request/response/ack under actual minimum roles. | PENDING |
| F4 | Metadata/content interleavings, retries, slot deletion and partial messages preserve ciphertext and unrelated metadata; peer never uploads media. | PENDING |
| F5 | Measured encoded size fits documented/observed limits; role downgrade and cleanup preserve needed access; writer-risk review accepted. | PENDING |
| F6 | Silent token renewal and process restart preserve pinned accounts, fixed IDs and required grants without UI. | PENDING |
| F7 | Revoke, unshare, trash, wrong account, malformed result and cancellation fail safely, with no data/identity replacement or broader scope. | PENDING |
| R1 | Retained revision listing/download/pinning/pruning tested for owner and peer, including peer after role downgrade. | PENDING, later G5 recovery gate |
| R2 | Production OAuth configuration and client identity revalidate grants/private-property visibility; testing expiry behavior recorded. | PENDING, release gate |

**G1 requires F1-F7 plus independent review.** Failure of exact-file access, private metadata
visibility, bounded mailbox capacity or safe update isolation stops this proposed pairing design.
No second QR, extra file, peer ciphertext modification, broad scope or new service may be
introduced as an automatic workaround. Preserve synthetic evidence and propose a scoped redesign.

G1 passing authorizes protocol implementation, not release. The owner's durable in-app approval
and exact peer-file grant, authenticated receiver identities, transcript comparison, durable
delivery/ack, real-crypto integration, atomic initial replacement and recovery remain later gates.

## Retention and security integration gates

The earlier setup note recorded a crypto review on 2026-10-18. Treat it as historical component
review, not sign-off for this new protocol. Positive generations and retryable ambiguous Keystore
failures exist; production still needs:

- Replace `PairingPayload` version 1, which contains the family key, with invitation secret,
  owner verification key and expiry. Reject legacy codes in the new onboarding path.
- Persist owner approval bound to verified accounts, both file IDs and receiver cryptographic
  identities before any family-key delivery. A scanned QR alone grants no financial access.
- Authenticate cloud content before enrolling keys or replacing a budget; use confirmed,
  staged source-wins atomic replacement and preserve local data on every failure.
- Persist outbox and acknowledgment before cleanup. Protect QR/recovery screens and clipboard;
  add directory fsync, singleton stores, copied-identity rejection and real Keystore device tests.
- Align sizes/generations/retention with fixed files. Existing `retainDailyHistory` does not
  authorize silently creating additional snapshots.

For same-file recovery, Google documents revision-history access for owner/writer-class roles,
up to 200 blob revisions marked `keepForever` per file, counted against storage, and automatic
removal of unpinned older revisions. Ordinary revisions are typically retained for 30 days but
may be purged earlier. Each daily recovery point must be explicitly retained.
Blob revision download requires retained (`keepForever`) revisions. A reader of the current
head must not be assumed able to recover prior revisions. Test actual roles and quotas before
promising retention; evaluate bounded encrypted inline history if revision rights cannot support
the agreed recovery journey. [Revision guide](https://developers.google.com/workspace/drive/api/guides/manage-revisions)

A recovery key alone lacks file locators, Google grants and membership authority. The full plan
requires a recovery kit, fresh identity on phone replacement, retired-file policy and explicit
recovery acceptance tests. No recovery guarantee is established by this synthetic spike.
