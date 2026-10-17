# Android releases

Tagged releases publish a signed release APK. Every release must use the same retained signing key. CI fails when signing configuration is missing instead of publishing an APK signed with a temporary debug key.

The workflow runs on version-tag pushes only. New tags run workflow validation, lint, JVM tests and the debug build, followed by the signed-release job. Branch pushes and pull requests do not trigger it; updates to existing tags skip both jobs. The workflow creates the GitHub release itself. Use tags such as `v0.3.0`; the release title strips the leading `v`, giving `0.3.0`. Run `make lint-workflows` before changing workflows; it uses installed actionlint or downloads a checksum-pinned Linux x86-64 binary to temporary storage.

Configure repository Actions secrets:

- `BUDGET_KEYSTORE_BASE64`: Base64 of the retained keystore.
- `BUDGET_KEYSTORE_PASSWORD`: Keystore password.
- `BUDGET_KEY_ALIAS`: Signing key alias.
- `BUDGET_KEY_PASSWORD`: Signing key password.

Keep a separate secure backup of the keystore and credentials. Never commit them. For local `make release`, supply the same password/alias environment variables and `BUDGET_KEYSTORE_PATH` pointing to the keystore file.

Before tagging, increment `versionCode` and `versionName`, run `make lint test release`, and test an upgrade on a device containing existing transactions. Confirm that accounts, balances, receipts, goals and loan history survive the upgrade.

Earlier CI releases used temporary debug keys. A new signing key cannot update those installations. Recover the exact original private key before promising an in-place upgrade; an APK contains its certificate, not its private key. Do not uninstall an existing installation containing financial data to bypass a signature mismatch. Data export/recovery is not implemented yet.
