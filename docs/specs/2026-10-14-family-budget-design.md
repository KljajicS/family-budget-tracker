# Family Budget Tracker: design spec

Status: draft for review. Date: 2026-10-14.

## 1. Purpose

Replace the paper notebook two people use to track household money. The app answers, at a glance:

- How much can we spend this month without hurting our goals or wallets?
- Where did the money go?
- Are we on pace with budgets and goals?

Users: one family, two phones, both entering transactions. Public open-source release possible later. Android only.

## 2. Principles

- Privacy first: data lives on the phones. Google Drive stores only ciphertext.
- Offline first: every screen works without network. Only receipt lookup and exchange rates need it.
- No own backend. Ever.
- Simple: fast entry, few screens, no settings sprawl.
- Localized: Serbian and English from day one.

## 3. Scope (MVP)

All six designed screens ship in the MVP. Built in this order so each step is usable on its own:

1. Accounts, add expense/income/transfer, Home number.
2. Sync between two phones.
3. Budgets and pace.
4. Recurring bills.
5. Receipt scan (online lookup).
6. Insights.
7. Goals.

Out of scope for MVP: bank statement import, automatic categorization, duplicate detection, iOS, more than one family per install.

## 4. Domain model

All amounts are `Money(amount: BigDecimal, currency: Currency)`. No floats anywhere.

| Entity | Fields | Notes |
|---|---|---|
| Account | id, name, currency, kind (spending, savings), archived | Savings accounts are excluded from safe-to-spend. |
| Category | id, name, color, icon, archived | Fixed starter set, user editable. |
| Transaction | id, type (expense, income, transfer), money, accountId, toAccountId (transfer), categoryId, date, note, merchant, paidBy (memberId), source (manual, receipt, recurring) | |
| Budget | id, categoryId, month, money | One per category per month. Copied forward each month unless changed. |
| RecurringRule | id, template transaction, cadence, nextDueDate | Generates upcoming bills; user confirms each. |
| Goal | id, name, target money, monthly contribution, accountId (optional), archived | Progress = allocated money. |
| GoalAllocation | id, goalId, money, date | Explicit "put X toward goal" entries. |
| Member | id, displayName, deviceId | Two for now. |
| Receipt | id, transactionId, fiscalUrl, items JSON | Fetched from suf.purs.gov.rs. |
| ExchangeRate | currency, rate to RSD, date, source (nbs, manual) | Cached; manual override allowed. |

Everything is a family-wide shared record. There is no per-member private data.

## 5. Key formulas

Formulas live in pure Kotlin in `domain` with unit tests.

**Safe to spend (this month)**

```
liquid        = sum of spending-account balances, converted to RSD
bills         = recurring bills due before month end, not yet paid
goals         = sum of monthly goal contributions not yet allocated this month
budgetReserve = sum over categories with a budget of max(0, budget - spentSoFar)
safe          = liquid - bills - goals - budgetReserve
```

Budgeted categories are treated as already committed, so the number is "money beyond what the budgets and goals already claim". Per-day figure = safe / days left in month.

**Budget pace**: expected spend today = budget × dayOfMonth / daysInMonth. Status: ahead, on track, over.

**Goal ETA**: months = (target − allocated) / monthlyContribution, rounded up.

**Currency**: display currency is RSD. Conversion uses the last cached rate. If no rate cached, the UI shows the amount in its own currency and flags it.

## 6. Architecture

Single Gradle module `app`, three packages with a strict dependency direction: `ui` → `domain` ← `data`. `domain` has no Android imports; a lint check enforces this.

### 6.1 Event-sourced core

The source of truth is an append-only log of domain events. Read models (the tables screens query) are derived from events and can be rebuilt at any time.

- Event: `id (UUID v7), deviceId, seq (per device), occurredAt, type, payload (JSON)`.
- Types: `TransactionAdded`, `TransactionEdited`, `TransactionDeleted`, `AccountCreated`, `BudgetSet`, `GoalCreated`, `GoalAllocated`, `RecurringRuleSet`, and so on. One event per user action.
- Projector: applies events in `(occurredAt, deviceId, seq)` order into read tables. Idempotent.
- Edits are new events referencing the original id. Last edit by `occurredAt` wins. Delete is an event too; nothing is physically removed from the log.

Why: sync becomes "exchange event files"; undo, history and "who entered this" come for free; read tables can change shape without migration risk.

### 6.2 Storage

SQLDelight over SQLite. Two groups of tables: `event` (the log) and read tables (`transaction_view`, `account_balance`, `budget_month`, and so on). Projector is the only writer of read tables.

Database file is encrypted at rest only by Android's file-based encryption; no SQLCipher. Threat model: Drive and Google, not a stolen unlocked phone.

### 6.3 Sync (Drive, per-device event logs)

- Each device owns one file in Drive `appDataFolder`: `events-<deviceId>.jsonl.enc`. Only its owner writes it; everyone reads all.
- Upload: append new local events, encrypt whole file, overwrite. Download: fetch every other device's file, decrypt, apply unseen events (tracked by `deviceId + seq`).
- Trigger: WorkManager periodic job (every 15 min when online, charged or not) plus an immediate attempt after each local write and on app open.
- No conflicts by construction: a device never writes another device's file. Concurrent edits of one record resolve by `occurredAt`.
- Ceiling: files grow forever. At family volume (a few thousand events a year) this is kilobytes per year; compaction is a later concern.

Why Drive: both users already have accounts, Android sign-in is native, free, no server. `appDataFolder` scope hides files from the user's Drive UI and from other apps.

### 6.4 Encryption

- One family key: 256-bit AES-GCM via Tink. Generated on the first device.
- Stored in Android Keystore-backed EncryptedSharedPreferences on each device.
- Sharing with the second device: QR code shown on device 1, scanned on device 2, in person. Contains the family key and the family id.
- Recovery: the same key encoded as a 24-word phrase (BIP-39 word list) the user stores in a password manager. Lose both phones and the phrase, lose the data.
- Drive sees only ciphertext and file names. File names carry a random device id, not a name.

### 6.5 Receipt scan

- CameraX plus ML Kit barcode scanning. Serbian fiscal QR codes encode a `suf.purs.gov.rs/v/?vl=...` URL.
- The app fetches that URL and parses total, date, merchant, and items from the response. Online only; offline shows "connect to scan".
- Result pre-fills the Add expense sheet: amount, date, merchant, category guess from merchant history. User confirms. Receipt items are stored for later features.

### 6.6 Exchange rates

National Bank of Serbia publishes a daily middle rate. Fetch once a day when online, cache in `exchange_rate`. Manual override per currency in settings.

### 6.7 UI

- Jetpack Compose, Material 3, single `Activity`, Navigation Compose with five top-level destinations as designed: Home, Insights, Scan, Budgets, Goals. Add expense and Scan are modal sheets.
- One ViewModel per screen exposing a single `StateFlow<UiState>`. Screens are stateless composables; events go up, state comes down.
- Theme: light and AMOLED dark as designed. Monospace numerals for amounts.
- Strings in `strings.xml` with `values-sr`. Number and date formatting via `java.text` with the current locale.

### 6.8 Dependency list

Kotlin, Compose BOM, Material 3, Navigation Compose, Hilt, SQLDelight, kotlinx-serialization, kotlinx-coroutines, WorkManager, Google Play Services Auth and Drive REST, Tink, CameraX, ML Kit barcode, OkHttp. Nothing else without a reason written into `docs/decisions`.

## 7. Error handling

- Local writes never fail on network state. Sync failures are logged and retried by WorkManager with backoff; Home shows "last synced X ago".
- Decrypt failure of a remote file (wrong key, corruption) skips that file and surfaces a persistent warning. It never deletes local data.
- Receipt fetch failure keeps the scanned URL so the user can retry.
- Rate fetch failure keeps the cached rate and shows its date.

## 8. Testing

- `domain`: plain JUnit on the JVM. Formulas, projector, event ordering, money arithmetic. Fast, no device.
- `data`: SQLDelight queries and projector on the JVM via the SQLite JDBC driver. Sync tested with a fake Drive backed by a local folder; two fake devices in one test.
- `ui`: Compose previews during development; Roborazzi screenshot tests for the six screens in both themes.
- One instrumented smoke test on the emulator: install, add expense, see it on Home.
- Dev loop: `make check` (JVM tests + lint), `make run` (install on emulator, screenshot), GitHub Actions builds a debug APK on every push to main for installing on phones that are not on the local network.

## 9. Open questions (decide when the step is reached)

- Starter category set and icons.
- Whether a transfer between a spending and a savings account should auto-allocate to a goal.
- Compaction of event logs once they matter.
- Member display: name per device or a picker on each transaction.
