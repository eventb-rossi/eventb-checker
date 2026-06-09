# Changelog

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
