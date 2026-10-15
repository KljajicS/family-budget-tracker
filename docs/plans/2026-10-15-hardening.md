# Hardening pass, between milestones 2 and 3

**Goal:** Fix what the 2026-10-15 architecture review found before recurring bills build on top of it: a crash reachable today, a projector that would not converge once two phones sync, and a budget carry-forward trap.

**Source:** Review by Claude Fable 5.1 with an independent Claude Opus 5.5 pass (Codex Astra was out of quota). Decisions recorded in ADR 8.

## Steps

- [x] **Currency follows the account.** The Add sheet no longer offers currency pills; the amount is in the selected account's currency. An account's currency is locked once it has transactions. `HomeCalculator.balances` skips a transaction whose currency differs from its account instead of throwing, so an old bad event cannot crash the app on launch. Test: `HomeSummaryTest`.
- [x] **Deterministic replay.** Merging remote events inserts them and rebuilds all read tables in log order inside one transaction. `occurredAt` is a logical clock, never earlier than the latest event in the log. Creates never overwrite, edits only touch existing rows, delete wins. Undecodable payloads are skipped during replay. The app rebuilds read tables once when `Projector.VERSION` changes. Android backup is off. Tests: `EventStoreTest` (convergence under shuffled delivery, delete wins, later create keeps a rename, clock never goes back, unknown events skipped).
- [x] **Budgets per category.** A category's budget for a month is its latest `BudgetSet` or `BudgetRemoved` dated at or before that month. `BudgetRemoved` projects as a zero row, so removing the last budget of a month no longer brings the previous month back, and the `materialize` step is gone. Home and Budgets re-read the date once a minute, so a screen open over midnight on the 1st moves to the new month. Tests: `BudgetRepositoryTest`.

## Left for later, on purpose

- Event schema version: goes in the sync file header at milestone 7. A per-row column needs SQLDelight schema snapshots for migrations; add them with the first real migration.
- "Join family" onboarding that imports the log without re-seeding starter categories: milestone 7.
- Unconvertible currencies in budgets count as zero without a partial flag: revisit with the rate fetch.
- `ui` imports `rs.budget.data` for the repository and clock; ADR 2's direction holds for `domain`, which is the part that matters for a future multiplatform move.
