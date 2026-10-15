# 9. Unpaid bills and the budget reserve are not subtracted twice

Date: 2026-10-15. Status: accepted. Refines ADR 7.

## Context

ADR 7 lists `upcomingBills` and `budgetReserve` as separate terms of safe to spend. A bill usually sits in a budgeted category (rent in Bills), and that category's reserve already holds `max(0, budget - spent)`. Subtracting the bill again would understate safe to spend by the bill amount every month. A budget that is already spent reserves nothing, so dropping the bill entirely would overstate it.

## Decision

- Bills are never generated. The upcoming list is derived from recurring rules: every occurrence from the rule's first due date to the end of the current month that has no transaction with the derived id `billTxId(ruleId, period)` and no skip record. Earlier months stay listed as overdue until paid or skipped.
- Payment identity is the period (`yyyy-MM` for monthly, `yyyy` for yearly), so editing a rule's due day never reopens a paid month.
- Safe to spend holds back: overdue bills in full, plus for each category the current month's unpaid bills minus the category's positive remaining budget, floored at zero. Categories without a budget contribute their bills in full.
- Paying a bill from a spending account lowers liquid and releases the same reserve, so the headline number does not move. Skipping releases the reserve and raises it.

## Consequences

- The headline number is right whether or not the bill's category is budgeted, and whether or not that budget is already spent.
- Two phones paying the same bill write the same transaction id; ADR 8's create-never-overwrites rule keeps one row.
- Deleting the paid transaction makes the bill due again. That is the honest state and is not special-cased.
