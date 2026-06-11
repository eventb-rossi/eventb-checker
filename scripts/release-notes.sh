#!/usr/bin/env bash
set -euo pipefail

# Extract the release-notes section for a tag from CHANGELOG.md.
# Prints everything under "## [<version>]" up to the next "## [" heading.
# Usage: release-notes.sh <tag>   (e.g. release-notes.sh v1.4)

if [ $# -ne 1 ]; then
    echo "Usage: $0 <tag>" >&2
    exit 2
fi

version="${1#v}"

notes=$(awk -v ver="$version" '
    $0 ~ "^## \\[" ver "\\]" { found = 1; next }
    /^## \[/ && found { exit }
    found { print }
' CHANGELOG.md)

# Trim leading/trailing blank lines.
notes=$(printf '%s' "$notes" | sed -e '/./,$!d' | sed -e ':a' -e '/^\s*$/{$d;N;ba' -e '}')

if [ -z "$notes" ]; then
    echo "ERROR: CHANGELOG.md has no content under '## [$version]'" >&2
    exit 1
fi

printf '%s\n' "$notes"
