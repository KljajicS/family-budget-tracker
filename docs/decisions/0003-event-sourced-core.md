# 3. Event-sourced core

Date: 2026-10-14. Status: accepted.

## Context

Two phones edit the same family data while offline and must converge without a server. A conventional row-per-record database needs a merge strategy for every table. We also want "who added this" and undo without extra bookkeeping.

## Decision

The source of truth is an append-only log of domain events, one per user action. Read tables are derived by a projector and can be rebuilt from the log at any time. Edits and deletes are events that reference the original record id; the latest `occurredAt` wins.

## Consequences

- Sync reduces to exchanging event files (see ADR 4).
- History, undo and attribution are free.
- Read table schema can change without data migration: rebuild from events.
- Cost: every feature writes an event type and a projector branch. Log grows forever; at family volume this is kilobytes per year, so compaction is deferred.
