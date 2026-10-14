# Roadmap

One milestone per screen, in the order the spec's build order dictates. Each milestone ends with an installable app the family can use. Detailed step-by-step plans live next to this file, one per milestone, written when the milestone starts.

| # | Milestone | Delivers | Plan |
|---|---|---|---|
| 1 | Foundation and Home | Done 2026-10-14. Project skeleton, domain core (money, events, projector), local DB, first-run setup (member, accounts), Add transaction sheet, Home screen with safe to spend, income, spent, savings, recent. Other tabs are stubs. | [2026-10-14-m1-foundation-home.md](2026-10-14-m1-foundation-home.md) |
| 2 | Budgets | Done 2026-10-14. Budget per category per month, pace bars, budget section on Home, budget reserve in safe to spend. | [2026-10-14-m2-budgets.md](2026-10-14-m2-budgets.md) |
| 3 | Recurring bills | Recurring rules, upcoming list on Budgets, confirm-as-paid, bills in safe to spend. | |
| 4 | Scan receipt | CameraX + ML Kit QR, fiscal receipt fetch and parse, pre-filled Add sheet, merchant memory for category guess. | |
| 5 | Insights | Category donut, income vs expenses bars, savings trend, one rule-based tip. | |
| 6 | Goals | Goals, allocations, ETA, unassigned savings, contributions in safe to spend. | |
| 7 | Sync | Google sign-in, Drive appDataFolder client, family key setup and QR share, recovery phrase, encrypted per-device event logs, WorkManager sync, "synced X ago" on Home. Moved last on 2026-10-14: the family tests on one phone until the screens exist. | |

Cross-cutting, done inside milestone 1 and kept green after: Serbian and English strings, light and AMOLED themes, JVM tests, `make check`, `make run`, GitHub Actions debug APK.
