<img src="assets/icon.svg" width="64" height="64" alt="">

# Family Budget Tracker

Privacy-first, offline-first family budgeting for Android. Financial data currently stays on your device. Planned Google Drive synchronization will use encrypted files.

## Status

Home shows combined active-account savings in EUR and RSD, plus this month's income and spending. Accounts and goals support drag ordering; goals fill sequentially from savings. Budgets and safe-to-spend estimates have been removed.

Transaction entry remembers income and expense accounts separately. Recurring bill payment remains under Settings. Insights includes category breakdowns, interactive monthly comparisons and spending changes. Removing an account warns before excluding its balance; past transactions remain in reports.

Exchange rates include cached NBS EUR/USD quotes and manual overrides under Settings. RSD summaries, including older months, are estimates using the latest available rates. Original transaction amounts remain unchanged.

Remaining MVP work: encrypted family sync and recovery, and physical-phone acceptance testing for receipt scanning and performance. Bank statement imports are outside the MVP. See [the roadmap](docs/plans/roadmap.md).

## Build

Requires JDK 17 and the Android SDK (platform 37).

```bash
make check   # JVM tests and lint
make run     # install on the connected device or emulator and screenshot
```

## Docs

- `docs/specs/` product and technical design
- `docs/decisions/` architecture decision records
- `docs/design/` UI mockups
