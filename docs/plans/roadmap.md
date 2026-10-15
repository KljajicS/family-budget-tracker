# Roadmap

One milestone per screen, in the order the spec's build order dictates. Each milestone ends with an installable app the family can use. Detailed step-by-step plans live next to this file, one per milestone, written when the milestone starts.

| # | Milestone | Delivers | Plan |
|---|---|---|---|
| 1 | Foundation and Home | Done 2026-10-14. Project skeleton, domain core (money, events, projector), local DB, first-run setup (member, accounts), Add transaction sheet, Home screen with safe to spend, income, spent, savings, recent. Other tabs are stubs. | [2026-10-14-m1-foundation-home.md](2026-10-14-m1-foundation-home.md) |
| 2 | Budgets | Done 2026-10-14. Budget per category per month, pace bars, budget section on Home, budget reserve in safe to spend. | [2026-10-14-m2-budgets.md](2026-10-14-m2-budgets.md) |
| 2b | Hardening | Done 2026-10-15. Fixes from the architecture review: currency follows account, deterministic replay with a logical clock (ADR 8), budgets per category. | [2026-10-15-hardening.md](2026-10-15-hardening.md) |
| 3 | Recurring bills | Done 2026-10-15. Recurring rules under Settings, Upcoming list on Budgets with overdue carry, pay through the pre-filled Add sheet or skip, unpaid bills held back in safe to spend (ADR 9). | [2026-10-15-m3-recurring.md](2026-10-15-m3-recurring.md) |
| 4 | Scan receipt | Implemented 2026-10-15. CameraX + ZXing-C++ QR, fiscal lookup, receipt review sheet, issuer-based category suggestion, duplicate protection, and retry. Physical-phone acceptance pending: ten receipts, at least nine decoded within two seconds after focus; rotation and flashlight background/resume checks. | [Receipt scanning decision](../decisions/0010-receipt-scanning.md) |
| 5 | Insights | Done 2026-10-15. Month navigation, category donut, six-month income vs expenses, savings trend, one rule-based tip for the current month. Built before Scan because it needs no camera. | [2026-10-15-m5-insights.md](2026-10-15-m5-insights.md) |
| 6 | Goals | Done 2026-10-15. Goals with their own currency, explicit allocations, progress and arrival month, unassigned savings, needs line, this month's unassigned contributions held back in safe to spend. | [2026-10-15-m6-goals.md](2026-10-15-m6-goals.md) |
| 6b | Reliability audit | Done 2026-10-15. Atomic setup, guarded writes with retry feedback, bounded money inputs, current-date accounting, recurring schedule protection, active account/category validation, duplicate-safe event imports, background initialization/calculations and shared transaction reads. 134 JVM tests pass; lint, debug and release builds pass. Physical-device performance remains unverified. | |
| 7 | Sync | Google sign-in, Drive appDataFolder client, family key setup and QR share, recovery phrase, encrypted per-device event logs, WorkManager sync, "synced X ago" on Home. Moved last on 2026-10-14: the family tests on one phone until the screens exist. | |

Exchange rates remain pending: NBS fetching, a local cache and manual overrides. Current combined totals use RSD only; foreign-currency balances cannot yet be fully included.

Cross-cutting, done inside milestone 1 and kept green after: Serbian and English strings, light and AMOLED themes, JVM tests, `make check`, `make run`, GitHub Actions debug APK.
