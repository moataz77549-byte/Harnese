#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/../.."
dir=dist/runtime-bundles/sources
name=codex-aarch64-unknown-linux-musl.tar.gz
checksum=0b99526c185a4425fe96a2214fb7b6a6a7f7c1471883b7afbebe97e15e83e79e
mkdir -p "$dir"
if ! printf '%s  %s\n' "$checksum" "$dir/$name" | sha256sum --check --status; then
    curl --fail --location --silent --show-error --retry 3 \
        https://github.com/openai/codex/releases/download/rust-v0.172.0/codex-aarch64-unknown-linux-musl.tar.gz \
        --output "$dir/$name.part"
    printf '%s  %s\n' "$checksum" "$dir/$name.part" | sha256sum --check
    mv "$dir/$name.part" "$dir/$name"
fi
printf '%s  %s\n' "$checksum" "$dir/$name" | sha256sum --check
