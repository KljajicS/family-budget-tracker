# 8. Deterministic replay, logical clock, delete wins

Date: 2026-10-15. Status: accepted. Refines ADR 3.

## Context

An architecture review before milestone 3 found that read tables depended on the order events were applied, that only transaction edits compared timestamps, and that a phone with a fast clock would win every conflict. Two phones would drift apart as soon as sync shipped.

## Decision

- Read tables are the result of replaying the whole log in `(occurredAt, deviceId, seq)` order. Merging remote events inserts them, then rebuilds every read table in the same database transaction. There is no incremental merge.
- `occurredAt` is a logical clock: `max(wall clock, latest occurredAt in the log + 1 ms)`. A phone can never write an event that sorts before one it has already seen.
- Projector rules, so that replay is deterministic: a create never overwrites an existing row, an edit only changes a row that exists, a delete is final. An edit that sorts after a delete is dropped.
- Payloads that this app version cannot decode stay in the log and are skipped during replay, so a newer phone never breaks an older one. A schema version goes in the sync file header (ADR 4), not on every row; the local `event` table changes shape only through migrations and SQLDelight needs schema snapshots for those, which we add when the first one is needed.
- The app stores a projection version and rebuilds once when it changes, so projector fixes reach existing installs.
- Android backup is off. Recovery is Drive sync plus the recovery phrase (ADR 5); a restored backup would reuse a device id and its sequence numbers.

## Consequences

- Convergence is testable: same events, any delivery order, identical tables. One test covers it.
- Replay cost is the whole log per merge. At family volume that is a few thousand events, well under a second. Snapshots come when a rebuild exceeds about one second.
- A simultaneous edit and delete of the same transaction resolves to deleted, whichever happened later.
- Events are whole-entity snapshots; the last snapshot in log order wins for accounts, categories, members and budgets.
