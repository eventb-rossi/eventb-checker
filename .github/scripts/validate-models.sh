#!/usr/bin/env bash
# GitHub Actions validation loop — emits GitHub annotations and SARIF.
#
# Required env vars:
#   CHECKER_CMD      – command to invoke the checker
#   MODEL_GLOB       – glob pattern for .zip model files
#
# Optional env vars:
#   SHOW_INFO_FLAG   – "--show-info" or "" (default: "")
#   PROOFS_FLAG      – "--proofs" or "" (default: "")

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
# shellcheck source=../../scripts/validate-models-core.sh
source "$SCRIPT_DIR/../../scripts/validate-models-core.sh"

on_model_start() {
  echo "::group::Validating $1"
}

on_model_crash() {
  echo "::error::Infrastructure error validating $1: $2"
}

on_no_models_matched() {
  echo "::error::No model files matched '$1'"
}

on_model_result() {
  local sarif_output="$2"
  # Emit GitHub annotations from SARIF results
  echo "$sarif_output" | jq -r '
    .runs[0].results[] | select(.level == "error") |
    "::error file=\(.locations[0].physicalLocation.artifactLocation.uri)::\(.message.text | gsub("%"; "%25") | gsub("\n"; "%0A") | gsub("\r"; "%0D"))"'
  echo "$sarif_output" | jq -r '
    .runs[0].results[] | select(.level == "warning") |
    "::warning file=\(.locations[0].physicalLocation.artifactLocation.uri)::\(.message.text | gsub("%"; "%25") | gsub("\n"; "%0A") | gsub("\r"; "%0D"))"'
}

on_model_end() {
  echo "::endgroup::"
}

on_complete() {
  local all_valid="$1"
  local total_errors="$2"
  local total_warnings="$3"
  local failures="$4"
  local merged_runs="$5"
  local matched_models="$6"

  # Set outputs
  echo "valid=$all_valid" >> "$GITHUB_OUTPUT"
  echo "error-count=$total_errors" >> "$GITHUB_OUTPUT"
  echo "warning-count=$total_warnings" >> "$GITHUB_OUTPUT"
  echo "sarif-file=eventb-checker-results.sarif" >> "$GITHUB_OUTPUT"
  echo "sarif-run-count=$merged_runs" >> "$GITHUB_OUTPUT"

  # Read by action.yml to decide whether the report is safe to publish. Only a
  # report covering every matched model is: an empty one is rejected outright,
  # and a partial one makes Code Scanning resolve the alerts of the models
  # missing from it, silently discarding findings that are still valid.
  if [ "$merged_runs" -gt 0 ] && [ "$merged_runs" -eq "$matched_models" ]; then
    echo "sarif-complete=true" >> "$GITHUB_OUTPUT"
  else
    echo "sarif-complete=false" >> "$GITHUB_OUTPUT"
  fi

  if [ "$failures" -gt 0 ]; then
    echo "::error::$failures model(s) failed validation"
  fi
}

validate_models
