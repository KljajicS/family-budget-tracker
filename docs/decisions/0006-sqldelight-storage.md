# 6. SQLDelight for local storage

Date: 2026-10-14. Status: accepted.

## Context

Room is the default Android choice. SQLDelight generates typed Kotlin from plain SQL, runs on the JVM without Android for tests, and keeps the `data` layer free of annotation processing.

## Decision

SQLDelight over SQLite. Two table groups: the `event` log and projector-owned read tables. No SQLCipher; Android file-based encryption and the Drive-side encryption cover the threat model (cloud, not an unlocked stolen phone).

## Consequences

- Queries are reviewable SQL, tests run on the JVM in seconds.
- Read tables can be dropped and rebuilt from events, so schema churn is cheap.
- Contributors unfamiliar with SQLDelight need a short orientation; it is well documented.
