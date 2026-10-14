# 1. Native Android with Kotlin and Jetpack Compose

Date: 2026-10-13. Status: accepted.

## Context

Android is the only target for the foreseeable future. iOS is a small chance tied to a possible public release. Cross-platform frameworks (Flutter, Kotlin Multiplatform) trade native feel and tooling for portability we do not need yet.

## Decision

Native Android app written in Kotlin with Jetpack Compose and Material 3.

## Consequences

- Full access to CameraX, ML Kit, Keystore, WorkManager and Google sign-in without bridges.
- If iOS ever matters, the `domain` package has no Android imports and can move to Kotlin Multiplatform; UI and data layers would be rewritten.
