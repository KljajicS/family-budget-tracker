# 7. Safe-to-spend formula

Date: 2026-10-14. Status: accepted.

## Context

The Home screen's headline number must answer "how much can we spend this month without hurting our goals or our wallets". It is not the same as remaining budget.

## Decision

```
safe = liquid - upcomingBills - unallocatedGoalContributions - budgetReserve
```

- `liquid`: balances of spending accounts in RSD. Savings accounts excluded.
- `upcomingBills`: recurring bills due before month end and not yet paid.
- `unallocatedGoalContributions`: this month's planned goal contributions not yet allocated.
- `budgetReserve`: for each budgeted category, `max(0, budget - spentSoFar)`.

Per-day figure is `safe / daysLeftInMonth`.

## Consequences

- Budgeted money counts as already committed; the number is spending room beyond budgets and goals.
- Can go negative, which is the honest signal.
- Depends on cached exchange rates for non-RSD accounts; a missing rate flags the number as partial.
