# One-QR family sync: gated implementation plan

Date: 2026-10-22. Branch: `feature/family-sync`. Status: **planned, feasibility gate open**.

## Outcome and non-negotiable boundaries

Deliver a finished two-phone sync product using both people's existing, separate Google accounts,
Drive `drive.file` only, and no new service. The wife scans one invitation QR displayed by the
owner. Both phones complete Google's exact-file access flow where needed; the owner also gives
explicit in-app approval. One QR does not mean one consent or no Google picker.

Each active phone owns one fixed ordinary Drive file. Only its owning phone publishes that file's
encrypted financial content. The proposed pairing mailbox occupies encrypted, authenticated
`appProperties` on the owner's file. The peer may update only the mailbox metadata through the
app, never the owner's ciphertext. No shared mutable financial file, discovery through folder
listing, `appDataFolder` sharing, public links, second QR, hidden mailbox file, or broader scope.

Drive writer permission can permit more than metadata writes. Application restrictions and
cryptographic verification cannot prevent an authorized writer from deleting or corrupting a
file outside the app. Explicitly review and disclose this availability risk before production;
do not describe metadata-only app behavior as a Google-enforced metadata-only permission.

The read-only alternative cannot notify the owner of an unknown receiver file ID before its
exact-ID picker grant. It is not a replacement for this bootstrap without another agreed discovery
channel. Keep the metadata experiment gated. Production must disable writer resharing with
`writersCanShare=false`, serialize permission updates, and prove downgrade to reader after the
acknowledgment. These controls reduce exposure but do not prevent content corruption during pairing.

Start with two active phones and one active invitation. Third-member support is future work:
every reader needs a directed grant for every publisher file, in both directions. An invitation
from the owner does not transitively grant another member's file. Do not advertise arbitrary
family size until the grant matrix, membership authority, and recovery behavior are implemented.

## Evidence baseline

Baseline inspected at `81eb6eb`; concurrent phase 1 edits are not evidence of completed behavior.
See [setup and evidence ledger](../sync-drive-setup.md) for device gates.

- User-reported folder experiment failed cross-account listing: each account saw only its own
  file. This invalidates folder-child discovery for this design, not all possible exact-file access.
  Device/build identifiers and individual HTTP outcomes were not supplied.
- `81eb6eb` resolves the selected account through Drive `about.user.emailAddress` using the
  exact newly granted token. It does not use deprecated `toGoogleSignInAccount()`, and does not
  silently refresh through a previously saved account during identity resolution.
- Exact peer-file selection, cross-account private metadata visibility/writes, and isolation
  between metadata and content updates remain unproven on devices.
- `PairingPayload` version 1 embeds the family key and a folder ID. It is unsuitable for the
  requested approval flow and must be replaced, not connected to production onboarding.
- Real AES-GCM and local Keystore wrappers exist. `SyncEngineTest` uses fake encryption,
  transport, history, and progress. Passing these tests proves orchestration behavior only.
- No production Drive adapter for `SyncTransport`, durable `SyncProgress`, pairing coordinator,
  device registry, or WorkManager sync integration is present in the inspected baseline.
- ADR 0004 still describes `appDataFolder`, and older design material describes the previous
  pairing model. Treat those as historical where they conflict with this plan. Update affected
  ADRs during implementation review; this documentation task owns only this plan and setup guide.

## Existing code, tests, and dependencies

Paths below are relative to `app/src/main/kotlin/app/kinjar/` unless stated otherwise.
Existing tests live under the corresponding `app/src/test/kotlin/app/kinjar/` packages.

| Surface | Reuse and required work | Existing verification |
| --- | --- | --- |
| `data/sync/drive/DriveAuthorizer.kt`, `DriveTransport.kt` | Token-scoped account lookup, bounded REST calls, grant parsing, share/update primitives. Replace folder assumption with exact-file flow; add verified ownership/capabilities and metadata operations. Public method signatures remain provisional until phase 1 review. | `data/sync/drive/DriveTransportTest.kt`: MockWebServer identity, pagination, bounds, retry, IDs, sharing and update tests. Not live grant evidence. |
| `ui/sync/DriveTestScreen.kt`, `DriveTestViewModel.kt` | Synthetic feasibility harness only. Existing account/folder/probe preferences are not a production registry. | `ui/sync/DriveTestErrorTest.kt` |
| `data/sync/crypto/PairingPayload.kt`, `SyncCipher.kt`, `FamilyKeyStore.kt`, `AndroidKeystoreWrappingKey.kt`, `Bytes.kt` | Replace key-bearing QR. Reuse reviewed framing/AEAD and wrapping where compatible. Add signing/encryption protocol identities, domain separation, durable stores and directory fsync. | `PairingPayloadTest`, `SyncCipherTest`, `FamilyKeyStoreTest`, `KeystoreFailureTest` |
| `data/sync/identity/InstallIdentity.kt`, `SelectedMemberStore.kt` | Existing non-exportable signing key binds local installation identity. Does not yet implement remote pairing identity exchange or receiver encryption. Keep member selection distinct from device identity. | `identity/InstallIdentityTest.kt` |
| `data/sync/SyncEngine.kt`, `EventSyncHistory.kt` | Real crypto adapter, trusted registry lookup, persisted generations and observation commits, retention adapter, worker serialization. Remove implicit recreate/discovery behavior that violates fixed IDs. | `SyncEngineTest`, `EventSyncHistoryTest` |
| `data/EventStore.kt`, `SyncEvent.kt`, `Projector.kt` | `importRaw` merges; `replaceRaw(records, confirmedLocal)` replaces atomically and rejects stale confirmation. Coordinate activation and initial replacement across DB and key files. | `data/SyncEventStoreTest.kt`, `EventStoreTest.kt`, `SchemaTest.kt`, projection tests |
| `di/DataModule.kt`, `KinjarApp.kt`, `data/BudgetRepository.kt` | Add singleton production stores/coordinator and durable work trigger. Existing event/repository device ID must be reconciled explicitly with install identity without rewriting historical IDs. | Repository/event tests and new restart integration tests |
| `ui/Nav.kt`, `ui/settings/SettingsScreen.kt`, `ui/setup/`, `ui/theme/Ui.kt`, `ui/security/` | Finished invite/join/status/recovery flows using existing UI primitives and lock behavior. Receipt scanning is a reusable camera dependency, not a pairing implementation. | `ui/UiConsistencyTest.kt`, `SerbianResourcesTest.kt`, setup and settings tests |
| `data/sync/crypto/RecoveryKey.kt` | Existing codec stores only family key with checksum. Add versioned recovery kit with locators, authority, history policy and authenticated recovery. | `crypto/RecoveryKeyTest.kt`; `ui/settings/RecoverySettingsTest.kt` is not proof of cloud recovery |
| `app/src/main/sqldelight/app/kinjar/data/db/` | Add migrations for pairing journal/outbox, registry and progress as needed. Derive next migration number from current tree, not this plan. | SQLDelight migration verification and new crash/transaction tests |

Existing dependencies: Play services auth 22.0.0, OkHttp/MockWebServer, Kotlin serialization,
coroutines, SQLDelight, Hilt, CameraX and ZXing. WorkManager is absent from the inspected version
catalog/build dependencies; add it only in the worker phase. Choose any additional crypto
dependency only after API 26+ support, Keystore behavior, audited implementation and protocol
review. Do not implement custom cryptographic primitives to avoid a dependency.

## Proposed end-to-end protocol

This is a required security contract, not a finalized wire format or Drive API signature.

1. **Prepare owner.** Authenticate the selected Google account with its actual grant token.
   Create or resume the owner's single file and durable local identity. Ask for the expected
   peer Google email privately before invitation creation. Establish a private ACL for that
   exact account on the owner's exact file. Do not create a public link. Labels are hints only.
2. **Issue one invitation.** Durably record a fresh invitation ID, cryptographically random
   invitation secret, owner verification public key, owner file ID, protocol/family identifiers,
   creation time and expiry. Display these bounded, versioned fields in the sole QR. Never include
   the family data key, owner signing private key, Google token, or peer email. Reissuing cancels
   the previous invitation durably. A copied invitation alone must never unlock financial data.
3. **Receiver establishes identity and grants.** Generate distinct signing and encryption/key
   agreement identities, prove possession, and bind them to the installation. Select the owner's
   exact file through Google and verify returned ID, selected account and file ownership.
   Create/resume the receiver's one fixed file and privately grant the owner access to it.
   Persist creation intent before network writes so retries do not silently create duplicate files.
   Use `files.generateIds`, durably save the chosen ID before `files.create`, and retry that same
   ID after ambiguous outcomes. Resolve duplicate-ID responses by fetching and verifying the exact
   file; never allocate another ID automatically. Record actual duplicate-create behavior in G1.
   Before approval, this file contains only a bounded signed bootstrap challenge, no independent
   budget or family data. Account/ownership verification must use Google evidence, not that challenge alone.
4. **Receiver submits request.** Write a small encrypted, authenticated mailbox request on the
   owner's file. Bind invitation ID, expiry, both file IDs, both installation IDs, both public
   identities, account/ACL evidence, protocol version, roles and fresh nonces into a canonical
   transcript. Use invitation-derived protection for pre-approval traffic with domain-separated
   keys. The invitation secret is not a substitute for proof of receiver private-key possession.
   Email/account claims supplied by the peer remain untrusted until checked against Drive.
5. **Owner verifies and consents.** Authenticate the request before showing its fields as trusted.
   Compare a short authentication string on both phones, derived from the complete transcript.
   In-app approval must bind the exact transcript and peer identity. The owner also selects
   the receiver's exact file in Google's picker. Verify returned file ID, token account, file
   ownership/ACL and signed receiver challenge. Filename, member name, arbitrary request email,
   and possession of the invitation secret are insufficient.
6. **Commit approval before delivery.** Only after all checks, both required grants, and explicit
   owner confirmation, persist approval and its immutable transcript hash durably. A changed
   account, file, key or request requires fresh verification/consent. Prepare a signed family-key
   envelope encrypted to the approved receiver encryption identity in a durable delivery outbox.
   Never publish a family key encrypted solely with the QR invitation secret. Restart must never
   infer approval from a remote mailbox value or from a successful picker return alone.
7. **Deliver and activate receiver.** Publish/retry the identical approved envelope in the owner's
   mailbox. Receiver verifies owner signature, transcript, invitation and identity binding,
   decrypts the key, then authenticates and validates the owner's snapshot before key enrollment
   or data replacement. Show explicit source-wins replacement confirmation. Stage key/registry
   activation and the snapshot; activate only through the crash-safe replacement procedure below.
8. **Acknowledge before cleanup.** Receiver durably records activation and an authenticated,
   idempotent acknowledgment tied to transcript and envelope digest before sending it. Owner
   retains approval/outbox and delivery ciphertext until acknowledgment is durably recorded.
   Receiver can resend acknowledgment after process death. Cleanup only the completed invitation's
   mailbox entries. Retain a compact local completion/replay tombstone. Timeout alone never
   means delivery succeeded. An approved but unacknowledged delivery blocks a new invitation.

Model explicit states such as prepared, invitation-active, request-verified, awaiting-grant,
awaiting-owner-confirmation, approved, delivery-pending, receiver-activated, acknowledged and
cleaned. Persist terminal cancellation/expiry/rejection. Specify every state transition and
restart action before implementation. Expiry blocks new approval; an already approved delivery
uses its durable record until acknowledged or explicitly resolved. Deleting delivery after a
timeout or cancelling after key release does not revoke a key already received.

Mailbox representation must fit actual Google limits, including base64 expansion, signatures,
nonces, tags and chunk keys. Use bounded message slots, authenticated message IDs/chunk indexes,
lengths and transcript digests; reject incomplete, mixed, replayed and oversized sets. Define
which party writes each slot. No whole-map read/modify/write from a stale metadata snapshot.
Prove safe retries, cleanup, interleaving and content preservation in phase 1. A remote mailbox
is delivery storage, never durable local authorization state.

## Initial replacement and normal sync

The owner's authenticated snapshot is the initial source of truth. The receiver does not merge
its previous independent budget into the family. Before confirmation, show what will be replaced
and offer a deliberate local export/save option without automatically creating cloud files.
Keep the old database and active key usable through download, validation, consent cancellation,
permission loss, insufficient storage and staging failures.

Reuse `replaceRaw` and its `confirmedLocal` stale-data guard. Authenticate source publisher,
family, purpose, schema and generation; validate complete event history and successful projection
in staging before destructive work. Tie user confirmation to exact staged snapshot digest and
local-history version. A changed local history or changed staged source requires renewed
confirmation. Gate unsupported required event semantics instead of installing partial projections.

Database replacement, registry/member selection, key enrollment and worker activation need a
recoverable local commit protocol: stage files durably, record activation intent and history
replacement atomically in the DB where possible, finish file activation idempotently, and block
sync/read paths that could combine old keys with new data. Inject crashes between every boundary.
After failure/restart the phone must expose either the complete old state or the complete approved
new state, never an empty budget or mixed identity. Keep any local rollback copy until activation
is verified; document its encryption, expiry and deletion before shipping.

Normal sync merges authenticated event history, preserves unknown events and existing conflict
semantics, and uses a durable registry of exact file IDs rather than listing a shared folder.
Bind each file to family, publisher signing key, installation ID, owning account and membership
epoch. A family-shared AES key alone cannot prove which family member authored a publisher header:
add verified publisher signatures or an equivalently reviewed authentication scheme.

Persist reserved upload generation before sealing/network calls; reuse a durable prepared upload
safely after uncertain outcomes. Persist observation digest/generation together with successful
import or through an idempotent recovery journal. Never advance progress past unapplied data.
A missing own file must prompt explicit repair/re-pairing with new directed grants, not invisible
creation under a new ID. Never overwrite rejected remote history or a useful recovery version.

## Phases and exit gates

Phases are sequential for production integration. Pure specifications and fixtures may be prepared
early, but no failed gate may be bypassed by wiring production code. Each exit needs automated
checks, recorded device evidence where specified, and an independent cross-family review.
The lead assigns a reviewer from a different model family than the implementation author,
records findings and resolutions, and commissions a fresh review after security-relevant fixes.
No unresolved critical/high security or data-loss finding passes a gate.

### Phase 1: synthetic exact-file and mailbox feasibility

Concurrent implementation owns only the synthetic debug UI, minimal Drive operations and their
tests. Keep production workers disabled. API shape, MIME filters, account metadata fields and
mailbox representation remain provisional until this implementation is reviewed.

- Replace folder discovery in the experiment with fixed files A and B, direct sharing and
  exact-file Google picker grants in both directions.
- Test token-derived account identity, reject wrong account/file/folder/multiple selection,
  and validate peer ownership and permissions. Do not use names as security identifiers.
- Exercise opaque synthetic fixtures sized for the proposed encrypted/authenticated mailbox on
  A's `appProperties` from B, responses from A, and chunk cleanup. This does not require production
  pairing crypto in the spike. Hash synthetic content locally before and after.
- Race metadata writes, duplicate retries, deletions and A's content upload. Verify unchanged
  peer ciphertext and no lost unrelated/other-party properties. Determine whether conditional
  writes are supported and necessary; do not assume compare-and-swap from a version field.
- Measure actual metadata capacity and permissions. Separate ordinary read grants from temporary
  writer needs. Test downgrading/removing temporary writer permission after pairing.
- Repeat direct reads after process death, token renewal, account change, revoke and unshare.
  Record every result using the setup guide's evidence IDs.

**G1:** All F1-F7 device checks in setup guide pass on separate accounts with the same test app
configuration; request/response/ack byte budget fits; independent review approves implementation
and writer-risk treatment. A fake HTTP test, one-account run or folder selection is not a pass.
If any required capability fails, stop this design. Bring a concrete redesign within fixed scope
for review; no broad OAuth scope, peer-content write, extra file or new service fallback.

### Phase 2: secure pairing and atomic joining

Depends on G1. Replace legacy key-bearing QR format and reject it in the production join route.
Finalize threat model, canonical transcript, crypto algorithms, mailbox framing, expiry and
account ownership verification. Implement durable pairing journal, approval, outbox and ack.
Add source authentication, staged replacement, selected-member handling and recovery-safe
activation. Singleton stores must prevent duplicate concurrent invitations/approvals.

Define family recovery authority in G2 before freezing membership signatures. Use a reviewed
family-root/delegated-device hierarchy or equivalent recoverable authority, not an irreplaceable
owner-device key as the sole root. Specify secure root custody and rotation now; G5 implements
the recovery kit and drills against this same authority.

**G2:** Protocol tests below pass with real cryptography and SQL persistence. Two phones complete
one-QR pairing with explicit in-app owner approval and exact-file grants. A photographed QR,
wrong Google file, stale consent or substituted key never releases the family key. Independent
crypto/protocol and persistence review approves all crash paths before background sync is wired.

### Phase 3: production transport, registry, progress and scheduling

Depends on G2. Implement a Drive-backed `SyncTransport` using registered IDs, a real
`SyncEncryption` adapter, durable `SyncProgress` and registry migrations. Adapt
`retainDailyHistory` to the chosen same-file retention policy; its name does not authorize
creating snapshot files. Resolve size limits end-to-end: current engine/history/cipher defaults
are 16 MiB, while Drive transport currently limits uploads to 5 MiB.

Add WorkManager via version catalog/build configuration, singleton DI, network constraints,
unique periodic work and coalesced one-shot work after committed local changes. All manual,
foreground and background triggers share a persisted-safe coordinator. Handle cancellation,
bounded retries/backoff, offline work, 401 reauthorization, rate limits, permission revocation,
unsupported data and corrupt heads distinctly. Workers never launch account or picker UI.
Notify users of required action without exposing secret content on the lock screen.

**G3:** Real-crypto/real-DB integration tests and two-account convergence tests pass. Offline edits,
concurrent edits, retries, process death and device restart preserve events and fixed file IDs.
Generation/rollback checks survive restart. Revoked access leaves local budgeting usable and
never recreates or publishes under an unverified identity. Independent integration review passes.

### Phase 4: finished settings, onboarding and localization

Depends on G2; release integration depends on G3. Provide clear owner/create, join, verification,
approval, initial replacement, status, reconnect, cancellation, retry and recovery flows.
Separate account permission problems from invitation expiry, corrupt data, upgrade required
and ordinary offline waiting. Show last successful sync and pending local changes accurately;
do not show connected/complete merely because a token or key exists.

Reuse existing buttons, fields, spacing/theme tokens, navigation and app-lock behavior.
Keep Google/Drive implementation jargon out of ordinary product decisions. Protect invitation,
verification and recovery screens even when general app lock is off. Never persist secrets in
navigation routes, `SavedStateHandle`, analytics, screenshots or logs. Reuse camera handling with
a distinct bounded pairing decoder; unrelated receipt QR codes must not join a family.

Localize every new string, error and accessibility label for English and Serbian Latin
(`values` and `values-b+sr+Latn`), consistent with `locales_config.xml`. Use placeholders/plurals,
test long text, large fonts, TalkBack, light/dark/AMOLED themes and rotation/process recreation.

**G4:** Both users can finish the device journey without developer terminology or manual file IDs.
Cancellation at each Google/app prompt preserves data and resumable state. UI consistency and
localization tests pass; independent UX and error-state review passes. Synthetic debug controls
are excluded from the production user journey.

### Phase 5: recovery, retention and security hardening

Depends on G3 and G4. Replace key-only recovery text with a versioned recovery kit including
family identity, exact file locators and owner/account hints, required decryption key epochs,
trusted publisher/owner verification keys, membership/retirement state and recovery authority.
Define which material authorizes adding a new phone or replacing the owner. A checksum detects
mistyping, not malicious replacement; verify authority against authenticated cloud state.
Do not export old device signing private keys or Google tokens. Require save-and-read-back
verification using the agreed Bitwarden store. Use one bounded, versioned text suitable for a
Bitwarden secure note. Mark copied secrets sensitive and clear them after a timeout; support
secure sharing and read-back verification. A compromised recovery kit grants family recovery authority.

Prefer retained revisions of the existing file when rights and retrieval are proven; otherwise
evaluate bounded encrypted inline history inside that same file. Neither choice authorizes
silent extra Drive files. Document retention guarantees, storage cost, bounds and pruning.
A peer with read access to a current head may lack revision-history access. Validate recovery
from that peer's own full merged-history file when owner revisions cannot be read. Each retained
daily revision must be marked `keepForever`; Google allows up to 200 such revisions per file,
counted against storage. Thirty full daily snapshots require roughly thirty snapshots of storage.
Ordinary revisions are typically retained for 30 days but may be purged earlier.
Validate recovery
for owner-present, owner-phone-lost, peer-only surviving and all-phones-lost scenarios separately.
Do not claim recovery from a family key alone when locators, Google access or authority are absent.
After all phones and their observation state are lost, an old but authentic snapshot can be
indistinguishable from the latest state unless the recovery kit or another trusted source retains
a newer checkpoint. Document this rollback limit; do not claim freshness from authentication alone.

Recover onto a fresh install identity with a fresh fixed file. Authenticate restored data and
confirm source-wins replacement before activation. Persist retirement of the lost identity/file;
deny new writes from it even if the device returns. Re-establish directed grants explicitly.
Keep old files as read-only recovery sources only when the reviewed retirement policy allows;
do not silently delete the last usable history. Handle permanently lost keys separately from
transient Keystore errors. Review key rotation and membership authority before promising that
revocation stops an old key holder reading future data. Revocation cannot erase prior copies.

Finish directory fsync/atomic rename durability, singleton stores, backup exclusions, device
transfer/copy rejection, real Keystore locked-screen/restart/invalidation behavior, size limits,
redaction and parser fuzz/bounds tests. Reconcile stale ADRs and production setup instructions.

**G5:** Recovery drills succeed under the documented grant/authority matrix, or unsupported paths
are explicitly rejected without data loss before release. Retention retrieval and pruning are
proven on devices. Independent cross-family security and recovery reviews close all blockers.

### Release gate

Lead runs repository `make lint`, `make test` and `make build` for combined changes; installation
and release signing follow `docs/releasing.md`. Record commit/build/device identities and signed-off
G1-G5 results. Test OAuth/release-client behavior with the intended production configuration,
including `appProperties` app identity, rather than assuming debug grants transfer. No automatic
migration of historical spike files or key-bearing QR invitations.

Do not call this product finished until two-account pairing, sync, reconnect and agreed recovery
journeys pass on real phones. Green unit tests, debug UI or a successful single exchange are
insufficient. Keep production feature disabled at any unresolved gate; local budgeting remains
available. Main stays untouched; branch commit/push decisions belong to the lead.

## Acceptance matrix

| ID | Automated proof to add or extend | Device proof / rejection criterion |
| --- | --- | --- |
| A1 Scope and identity | Only `drive.file`; exact grant token used for account lookup; wrong account/file/owner, folder, empty or multiple selections rejected. Error redaction. | Separate A/B accounts; wrong selections do not advance state or release keys. |
| A2 Mailbox integrity | Canonical framing, bounded chunking, AEAD/signatures, replay/expiry, cross-invite/chunk substitution, partial reads and conflicting writes. Metadata request never carries media. | Both accounts see intended private properties; race tests preserve media and unrelated metadata (G1). |
| A3 Consent | QR contains no family key; receiver proves signing/encryption possession; transcript mutation, SAS mismatch, stale/cancelled consent and repeated invitation fail closed. | One QR; owner's in-app approval plus Google's peer-file grant required; no reverse QR or hidden service. |
| A4 Durable delivery | Crash before/after approval, envelope persistence, publish timeout, receiver activation, ack send/record and cleanup. Same approved envelope can retry; forged ack cannot clear it. | Kill/restart both apps at each prompt/delivery boundary; unacknowledged delivery remains recoverable. |
| A5 Initial replacement | Real DB/crypto staging; stale local history; corrupt/wrong-family/wrong-publisher/unsupported snapshot; disk-full and commit failures; transaction/projection rollback. | Cancel, offline, expired grant or restart leaves complete old or approved new budget; no merge of receiver's old budget. |
| A6 Sync correctness | Real adapter plus DB, idempotent imports, divergent events/conflicts, tombstones/loans, unknown required semantics, rollback/equivocation, observation atomicity. | Both phones edit offline and converge after reconnect; no duplicated events or missing balances. |
| A7 Fixed-file durability | Crash around create/register/upload; ambiguous network response; missing/trashed file; account switch; reservation overflow/bounds. | Repeated sync updates same two IDs; missing file requires explicit repair and new grants. |
| A8 Workers | Unique/coalesced execution, cancellation, retry caps, backoff, permission failures, no UI from background, persisted pending work. | Restart/Doze/network transitions and token expiry recover; permission loss prompts foreground action only. |
| A9 Recovery/retention | Kit parsing/versioning/authority, wrong key, stale kit, retired publisher, retained-revision/inline bounds, pruning safety, fresh identity. | Owner loss, peer loss, both phones lost and unavailable Google account have tested, honest outcomes. |
| A10 UI and privacy | English/Serbian parity, state restoration without secrets, sensitive-screen flags, safe error mapping, production debug separation. | Large font/TalkBack/themes, screenshots/Recents, picker cancellation and app lock work across both phones. |

There is no `app/src/androidTest` suite in the inspected baseline. Add focused instrumentation
where needed for lifecycle, database and Keystore behavior; a manual record alone cannot replace
crash regression tests. Host tests cannot establish Android Keystore or Google grant semantics.

## Recommended defaults and decisions still requiring closure

Defaults below are proposals, not claims about current code. Security-sensitive choices must be
ratified at their gate; do not silently pick a weaker alternative.

| Decision | Recommended default | Required closure |
| --- | --- | --- |
| Invitation lifetime | 15 minutes; one active invite; fail closed on ambiguous clock/expiry state; fresh 256-bit invitation secret. | G2: clock skew/restart policy and replay tombstone lifetime. |
| Human verification | Six decimal digits derived from full transcript; both users explicitly compare and confirm; bounded attempts. | G2: review SAS protocol, attempt limit and resistance to request replacement/grinding. |
| Mailbox | One bounded request/response/ack exchange; local outbox authoritative; authenticated chunk framing. | G1: exact budget, private-property namespace across accounts/clients, merge/delete isolation, role/capability matrix. |
| Grants | Private expected-email ACL before QR; least privilege after pairing; downgrade temporary writer when protocol no longer needs it. | G1/G2: ownership identity fields and aliases, cleanup order, revision-access consequences and unavoidable writer risk. |
| Crypto | Existing AES-GCM for payloads plus reviewed signature/key-agreement construction with domain-separated keys. | G2: algorithms, canonical encoding, key storage, owner authority and rotation across supported Android versions. |
| Scheduling | Connected-network periodic work at 15 minutes plus coalesced local-change/manual work; foreground invitation polling only, bounded by state/expiry. | G3: battery/retry policy and realistic Android timing; never promise real-time delivery. |
| File size | Start with transport-compatible bounded ciphertext, accounting for all envelope/history overhead; block cleanly before upload. | G3: exact plaintext/history budget or reviewed resumable upload implementation. No silent truncation. |
| History | Target 30 daily recovery points within existing files, preserving a validated previous point until a new one is secured. | G5: revision rights/retention evidence, quotas, size growth and selected inline fallback if necessary. |
| Recovery authority | Independent, securely stored recovery authority verified against authenticated membership state; fresh device identity on recovery. | G5: exact kit contents, owner succession, all-phones-lost bootstrapping and stale-kit/rollback limits. |
| Retirement | Never reuse a lost publisher identity; retain authenticated old history until replacement and recovery are verified. | G5: read-only retired-file lifetime, deletion ownership, future-key access and returned-device rejection. |

Stop rather than weaken scope, consent, authentication or atomicity. If a gate requires changing
the agreed one-file/one-QR/no-service product, present that decision explicitly before proceeding.

