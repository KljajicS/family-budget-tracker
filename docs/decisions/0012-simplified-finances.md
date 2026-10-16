# Simplified finances and ordered goals

Status: accepted 2026-10-17. Supersedes ADR 7 and the reserve calculation in ADR 9.

## Decision

Home reports all active account balances as savings, primarily in EUR with an RSD equivalent. Account kind no longer affects calculations. Income, spending and their difference use posted transactions in the current calendar month. Transfers do not count as income or spending. Future-dated entries do not affect today's balances.

Goals consume one savings pool in priority order. Each goal receives up to its target before the next receives anything. Changing order or spending money recalculates coverage; this never creates transactions or moves money. Negative total savings funds no goals. Missing exchange rates must not imply a goal is funded. Monthly contributions, arrival estimates and manual allocations are removed from the product.

Accounts and goals have persisted ordering, represented by events and rebuildable positions. Home uses the same account order as Accounts. Reordering saves on drop, with accessible move actions as an alternative.

Removing an account requires confirmation showing its remaining balance and explaining that savings and goal progress will decrease or otherwise change. Users can transfer money first, but removal is allowed with a nonzero balance. Archived balances no longer contribute to current savings. Transactions remain available for historical income and spending reports.

The last successfully used income and expense accounts are separate device preferences. Scanned receipts use the expense preference when it is an eligible RSD account. Transfers do not change these preferences. An unavailable account falls back to an eligible account in display order; every form allows changing the selection before saving. There is no default-account settings control.

Insights shows a selected month's category breakdown, six months of income and spending, and the largest category changes. Selecting a chart month reveals exact income, spending and difference. A current-month comparison uses the same elapsed period in the previous month. Reports use the latest cached exchange rates, as in ADR 11.

Recurring rules and bill payment remain available under Settings. Budgets, safe-to-spend calculations and budget warnings are removed.

## Compatibility

The schema migration adds ordering positions without deleting records. Legacy budget and goal-allocation events remain decodable for replay, but do not affect current calculations. Legacy account kind and goal contribution fields remain compatible with existing payloads.

Rollback requires a forward-compatible build retaining the upgraded schema. Older binaries cannot downgrade the database; ignoring unknown ordering events would also lose goal priority. No reset or destructive migration is acceptable.

## UI

Keep existing typography, theme colors and cards. Navigation is Home, Insights, Scan, Accounts, Goals. Total savings appears only on Home. Scan's entire visible circle must be inside its measured touch area; a visual translation outside the clickable parent is insufficient.
