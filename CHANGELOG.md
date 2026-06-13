# Changelog

## [1.6] - 2026-06-13

### Added

- Duplicate identifier (EB021) and duplicate label (EB022) checks. The checker now reports, as errors, an identifier (variable, constant, carrier set, or event parameter) or a label (invariant, event, guard, action, axiom, or witness) that is declared more than once within the same scope, matching Rodin's static checker. Identifiers and labels are separate namespaces, so a variable and an invariant that share a spelling do not conflict. This complements the existing cross-file EB019 "Duplicate component" warning.

## [1.5] - 2026-06-11

### Changed

- **Breaking:** definite type conflicts (e.g. `a ∈ AUCTIONS ↦ item`, where the right-hand side of `∈` is a pair rather than a set) are now reported as errors (EB006) and make the model invalid, matching Rodin's static checker. Identifiers whose types cannot be inferred remain warnings under the new EB020 "Unknown type" rule, since they usually reflect constructs the checker does not fully model (e.g. primed witness variables).
- Proof status now takes each obligation's confidence from the `.bps` replay status; the confidence stored with the `.bpr` proof tree is used only for obligations that have no status entry. A status entry without a confidence attribute counts as unattempted (Rodin serializes unattempted that way). The obligation list itself now falls back from `.bpo` to `.bps` before `.bpr`. Together these stop stale `.bpr` trees (including proofs for obligations that no longer exist) from inflating the discharged count.
- Proof obligations marked broken (`psBroken="true"`) are counted as pending instead of discharged or reviewed, matching Rodin's treatment of non-replayable proofs, and are reported with a single "Broken proof" warning instead of also being flagged undischarged.

### Added

- `manualDischarged` count in the proof summary (text and JSON output): discharged obligations whose proof was completed manually (`psManual="true"`).

## [1.4] - 2026-06-10

### Fixed

- Parse Rodin proof trees nested deeper than 100 XML elements. On JDK 24+ the parser's default `jdk.xml.maxElementDepth` of 100 caused whole `.bpr` files to be silently skipped, misreporting their discharged proof obligations as unattempted.

### Changed

- Upgrade Kotlin, Shadow, and Spotless build dependencies.

## [1.3] - 2026-06-03

### Added

- `info` subcommand with `--types` to report the inferred types of declared constants, variables, and event parameters — as text or, with `--format json`, as JSON.

### Changed

- **Breaking:** model validation now runs under the `check` subcommand. Use `eventb-checker check <model>` (e.g. `eventb-checker check --format sarif <model>`) instead of passing the model to the top-level command.

## [1.2] - 2026-05-31

### Added

- `--version` flag to print the CLI version and exit.

### Changed

- Upgrade the JSON-java and JUnit dependencies.

## [1.1] - 2026-05-16

### Changed

- Prefer Rodin XML model files over Camille textual input when both are present in a project.
- Harden semantic formula validation and report Camille conversion failures as validation errors.
- Fix filtered validation summaries so hidden INFO findings are removed from summary counts.
- Fail validation scripts when model glob patterns match no files.
- Update repository owner references.
- Upgrade Gradle wrapper, Kotlin, Spotless, Shadow, and GitHub Actions dependencies.

## [1.0]

- Initial release.
