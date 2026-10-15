# 11. Cached NBS rates and manual overrides

Date: 2026-10-15
Status: Accepted

## Decision

RSD is the reporting currency. EUR and USD use the latest cached NBS middle rates, overridden by an explicit manual rate when present. This also revalues historical summaries. Original transaction money is never rewritten. Converted totals are estimates, not bank settlement amounts.

Fetch the fixed HTTPS public current-middle-rate page using OkHttp and parse its HTML using Jsoup. No credentials, backend, analytics or additional dependencies. Redirects are disabled; response size and time are bounded. Validate currency codes, units, decimals and effective date. Reject incomplete, duplicate, future-effective and regressing lists without changing the previous cache.

Store RSD per foreign unit as decimal text. Conversion computes amount times source rate divided by target rate, rounding only the final Money. Rates are positive, at most 1000000, with at most eight decimal places.

Schema 6 adds `exchange_rate` for the per-device public cache and `exchange_rate_override` for projected family settings. `ExchangeRateOverrideSet(currency, rate)` stores a decimal string or null to reset automatic selection. Last event in replay order wins; reset does not prevent a later override. This setting is not a permanently deleted entity under ADR 8. Projector version 5 rebuilds overrides but preserves the public cache.

Foreground lifecycle checks run after local initialization and each minute while visible. A successful fetch after the latest 08:00 Europe/Belgrade boundary suppresses automatic retries until the next boundary. Failed attempts wait one hour; manual retry is available. Concurrent fetches are suppressed. Cached rates remain usable offline. No background scheduler.

One financial snapshot calculates balances, budgets, bills and goals from the same rate snapshot. All four financial tabs react without restart. Missing rates retain ADR 7 partial-number semantics and identify excluded currencies. Rates at least seven days old produce a warning. Settings exposes source, effective date, fetch time, refresh, manual input and reset in English and Serbian.

## Consequences

NBS HTML may change. Fixture tests detect known format regressions; cached rates and manual overrides preserve offline use. Devices may temporarily show different estimates because public caches are device-local. Sync will carry manual override events, not public quotes.

Rollback uses a corrective forward build that can disable fetch or UI while retaining schema 6 data. Do not install an older schema-5 APK over this database or wipe user data.
