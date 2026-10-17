#!/usr/bin/env bash
set -euo pipefail

if [[ -n "${ACTIONLINT:-}" ]]; then
    exec "$ACTIONLINT" -shellcheck= .github/workflows/*.yml
fi
if command -v actionlint >/dev/null 2>&1; then
    exec actionlint -shellcheck= .github/workflows/*.yml
fi

if [[ "$(uname -s)" != Linux || "$(uname -m)" != x86_64 ]]; then
    echo 'Install actionlint or set ACTIONLINT to its executable path.' >&2
    exit 1
fi

workdir=$(mktemp -d)
trap 'rm -rf "$workdir"' EXIT
curl --fail --silent --show-error --location \
    https://github.com/rhysd/actionlint/releases/download/v1.7.12/actionlint_1.7.12_linux_amd64.tar.gz \
    --output "$workdir/actionlint.tar.gz"
printf '%s  %s\n' '8aca8db96f1b94770f1b0d72b6dddcb1ebb8123cb3712530b08cc387b349a3d8' "$workdir/actionlint.tar.gz" | sha256sum --check --status
tar -xzf "$workdir/actionlint.tar.gz" -C "$workdir" actionlint
"$workdir/actionlint" -shellcheck= .github/workflows/*.yml
