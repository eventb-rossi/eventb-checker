#!/usr/bin/env bash
# Shared validation core for GitHub Actions and GitLab CI.
#
# Runs the checker once per model with --format sarif, derives all
# outputs (valid, error-count, warning-count) from the SARIF JSON,
# and produces a merged SARIF file.
#
# Required env vars:
#   CHECKER_CMD      – command to invoke the checker (e.g. "java -jar eventb-checker.jar")
#   MODEL_GLOB       – glob pattern for .zip model files
#
# Optional env vars:
#   SHOW_INFO_FLAG   – "--show-info" or "" (default: "")
#   PROOFS_FLAG      – "--proofs" or "" (default: "")
#
# Callers must define two callback functions before sourcing this script:
#   on_model_start  "$zip"                          – called before each model
#   on_model_result "$zip" "$sarif_output" "$model_errors" "$model_warnings"  – called after each model
#   on_model_crash  "$zip" "$stderr_msg"            – called on checker crash
#   on_no_models_matched "$MODEL_GLOB"              – called when the glob matches no files
#   on_model_end                                    – called after each model's output
#   on_complete "$all_valid" "$total_errors" "$total_warnings" "$failures" \
#               "$merged_runs" "$matched_models"
#                                                   – called after the loop finishes.
#                                                     $merged_runs is the number of models
#                                                     that produced a SARIF run, and
#                                                     $matched_models the number the glob
#                                                     matched; they differ when a model
#                                                     crashed, i.e. when the merged report
#                                                     is only a partial picture

set -euo pipefail

: "${CHECKER_CMD:?CHECKER_CMD is required}"
: "${MODEL_GLOB:?MODEL_GLOB is required}"
: "${SHOW_INFO_FLAG:=}"
: "${PROOFS_FLAG:=}"

validate_models() {
  local all_valid=true
  local total_errors=0
  local total_warnings=0
  local failures=0
  local matched_models=0
  local sarif_tmpdir
  sarif_tmpdir=$(mktemp -d)
  local run_index=0

  for zip in $MODEL_GLOB; do
    if [ ! -f "$zip" ]; then
      continue
    fi
    matched_models=$((matched_models + 1))
    on_model_start "$zip"

    # Single run with SARIF output
    local sarif_output
    sarif_output=$($CHECKER_CMD check --format sarif $SHOW_INFO_FLAG $PROOFS_FLAG "$zip" 2>/tmp/checker_stderr) && local checker_rc=0 || local checker_rc=$?

    # Treat anything that did not produce a usable SARIF run as a crash. Exit code 2
    # is the checker's own "input error", but a JVM that dies — OOM-killed, failed VM
    # init — exits with some other code and writes nothing at all, and `jq empty`
    # succeeds on empty input, so the output has to be probed for the run itself.
    if [ "$checker_rc" -eq 2 ] || ! echo "$sarif_output" | jq -e '.runs[0].results | type == "array"' > /dev/null 2>&1; then
      local stderr_msg
      stderr_msg=$(cat /tmp/checker_stderr 2>/dev/null || echo "Unknown error")
      on_model_crash "$zip" "$stderr_msg"
      all_valid=false
      failures=$((failures + 1))
      on_model_end
      continue
    fi

    # Extract both counts from the SARIF results in one pass
    local model_errors
    local model_warnings
    read -r model_errors model_warnings < <(echo "$sarif_output" | jq -r '
      [.runs[0].results[].level] |
      "\([.[] | select(. == "error")] | length) \([.[] | select(. == "warning")] | length)"')

    total_errors=$((total_errors + model_errors))
    total_warnings=$((total_warnings + model_warnings))

    if [ "$model_errors" -gt 0 ]; then
      all_valid=false
      failures=$((failures + 1))
    fi

    on_model_result "$zip" "$sarif_output" "$model_errors" "$model_warnings"

    # Save run to temp file for merging later. Zero-padded so the glob below
    # expands in model order rather than lexicographically (run_10 < run_2).
    #
    # Each result records the archive it came from: a finding's uri is the path
    # *inside* the archive, so once the runs are merged nothing else identifies
    # which model produced it.
    local run_file
    printf -v run_file '%s/run_%04d.json' "$sarif_tmpdir" "$run_index"
    echo "$sarif_output" | jq --arg model "$zip" '
      .runs[0]
      | .results = [.results[]? | .properties = ((.properties // {}) + {model: $model})]
    ' > "$run_file"
    run_index=$((run_index + 1))

    on_model_end
  done

  if [ "$matched_models" -eq 0 ]; then
    on_no_models_matched "$MODEL_GLOB"
    all_valid=false
    failures=$((failures + 1))
  fi

  # Write merged SARIF file from individual run files.
  #
  # The per-model runs are merged by concatenating their *results* into one
  # run, not by concatenating the runs themselves: Code Scanning rejects a
  # file carrying several runs that share a category, which is every run here.
  # tool.driver.rules is unioned rather than taken from the first run, because
  # the checker emits only the rules a given model's findings reference — so
  # the first model's rule set is generally not a superset of the others'.
  # Results carry ruleId (never ruleIndex), so nothing needs re-indexing.
  if compgen -G "$sarif_tmpdir/run_*.json" > /dev/null; then
    jq -s \
      --arg schema "https://docs.oasis-open.org/sarif/sarif/v2.1.0/errata01/os/schemas/sarif-schema-2.1.0.json" \
      '{
         "$schema": $schema,
         "version": "2.1.0",
         "runs": [{
           "tool": {
             "driver": ((first(.[] | .tool.driver? | select(type == "object")) // {})
                        + {"rules": ([.[].tool.driver.rules[]?] | unique_by(.id))})
           },
           "results": [.[].results[]?]
         }]
       }' \
      "$sarif_tmpdir"/run_*.json \
      > eventb-checker-results.sarif
  else
    # No model produced a run: the glob matched nothing, or every model crashed.
    # Code Scanning rejects an empty runs array (HTTP 422, "1 item required; only
    # 0 were supplied"), so callers are expected to skip the upload when the run
    # count reported to on_complete is zero. The cost is that stale alerts are not
    # cleared on this path; there is no way to clear them, and it already fails.
    jq -n \
      --arg schema "https://docs.oasis-open.org/sarif/sarif/v2.1.0/errata01/os/schemas/sarif-schema-2.1.0.json" \
      '{"$schema": $schema, "version": "2.1.0", "runs": []}' \
      > eventb-checker-results.sarif
  fi
  rm -rf "$sarif_tmpdir"

  on_complete "$all_valid" "$total_errors" "$total_warnings" "$failures" \
              "$run_index" "$matched_models"

  if [ "$failures" -gt 0 ]; then
    return 1
  fi
}
