# Manual loan tracking

Status: accepted 2026-10-17

Loans live under Accounts. Home has no loan widget. Each loan retains its own currency, opening principal and tracking start date. Opening means principal at the start of that date; earlier payments are outside tracking. Existing loans create no account movements. Newly received borrowing creates a separate LOAN_RECEIPT credit, included in account balances but excluded from income and spending.

A payment is one ordinary expense linked to one loan. Full account debit counts once as spending; principal alone reduces debt. Different account and loan currencies require both actual amounts. Same-currency payments must match. Stored amounts are never recomputed from later rates. The user enters principal; interest and fees are the remainder. Blank principal is unknown, distinct from zero. Incomplete balances cannot be shown as paid off.

Savings still means all active account balances, including borrowed cash. Goals continue to fill sequentially from savings. Debt is not subtracted from the savings pool. A repayment affects goals through its account debit only.

LoanRepository shares BudgetRepository's EventStore and lock. Transaction and payment events append in one database transaction. IDs remain stable across retries. Unique active links prevent counting one expense against multiple loans. Removed payment links retain tombstones; edits cannot resurrect them. Replay is deterministic and invalid histories are marked for correction rather than crashing startup.

Linking an existing expense never creates another debit. Unlinking preserves its expense; deleting removes the expense too. Deleting a recurring expense makes its bill unpaid again. Split-only corrections remain possible after removing an account or loan tracking. Account/date/amount corrections use removal and re-entry rather than a general transaction editor.

Balance corrections affect principal only, require a reason, and are blocked until all payment splits are complete. Corrections are for recording mistakes, not extra borrowing or interest capitalization. Daily net principal cannot become negative. The supported scope excludes refinancing, additional drawdowns, automatic interest schedules, bank imports, notifications and payment execution. Optional due dates and installment amounts are informational and updated manually.

Removing tracking archives the loan after confirmation. It retains loan history, account transactions and cash balances; it neither settles the debt nor restores money. Archived loans cannot accept new payments. Paid-off status is derived separately and recalculates after corrections.

Schema migration 7 upgrades version 7 to 8 with additive loan projections. Existing events and transactions are untouched. Older apps do not understand LOAN_RECEIPT and cannot safely rebuild new financial history. Rollback requires a forward-compatible build retaining these events and financial rules, with new entry disabled if necessary. Never reset the database or downgrade it to work around a failure.
