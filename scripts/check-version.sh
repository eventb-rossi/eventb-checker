#!/usr/bin/env bash
set -euo pipefail

# Shared version-consistency check for pre-push hook and release CI.
# Usage: check-version.sh <tag>   (e.g. check-version.sh v1.0)

if [ $# -ne 1 ]; then
    echo "Usage: $0 <tag>" >&2
    exit 2
fi

tag="$1"
errors=0

# Helper: emit an error in CI or local format.
emit_error() {
    local file="$1"
    local msg="$2"
    if [ "${GITHUB_ACTIONS:-}" = "true" ]; then
        echo "::error file=${file}::${msg}"
    else
        echo "ERROR: ${msg}"
    fi
    errors=$((errors + 1))
}

# 1. Validate tag format: vMAJOR.MINOR
if [[ ! "$tag" =~ ^v[0-9]+\.[0-9]+$ ]]; then
    emit_error "" "Tag '$tag' does not match expected format vMAJOR.MINOR"
fi

version="${tag#v}"

# 2. Check build.gradle.kts version matches
gradle_version=$(sed -nE 's/^[[:space:]]*version[[:space:]]*=[[:space:]]*"([^"]+)".*/\1/p' build.gradle.kts | head -n 1)
if [ "$gradle_version" != "$version" ]; then
    emit_error "build.gradle.kts" "build.gradle.kts version is '$gradle_version', expected '$version'"
fi

# 3. Check README.md contains the JAR filename
if ! grep -q "eventb-checker-${version}-all.jar" README.md; then
    emit_error "README.md" "README.md does not reference 'eventb-checker-${version}-all.jar'"
fi

# 4. Check README.md contains the action tag
if ! grep -q "eventb-rossi/eventb-checker@${tag}" README.md; then
    emit_error "README.md" "README.md does not reference 'eventb-rossi/eventb-checker@${tag}'"
fi

if [ "$errors" -gt 0 ]; then
    echo "Found $errors version inconsistency(ies). Fix and re-tag."
    exit 1
fi

echo "Version checks passed: tag=$tag version=$version"
