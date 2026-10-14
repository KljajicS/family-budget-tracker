# 2. Single Gradle module with layered packages

Date: 2026-10-14. Status: accepted.

## Context

Two developers, one app. Multi-module Android projects buy faster incremental builds and enforced boundaries at the cost of Gradle wiring and slower onboarding. Google's architecture guidance recommends layers first and modules only when build time or team size demands them.

## Decision

One `app` module with three packages: `ui`, `domain`, `data`. Dependency direction is `ui` → `domain` ← `data`. `domain` contains no Android imports; a lint rule enforces this.

## Consequences

- Fast builds, simple Gradle, one place to look.
- Boundaries are enforced by lint, not by the build system. Splitting a package into a module later is mechanical.
- Revisit if clean builds exceed about two minutes or a third regular contributor joins.
