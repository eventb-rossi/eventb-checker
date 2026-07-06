# Changelog

## [Unreleased]

Reworks the semantics of several "validate" warning and error codes.

### Changed

- EB011 (dead variable) and EB012 (unmodified variable) repartitioned so each variable draws at most one, chosen so that acting on the finding yields a correct model. A typing-shaped invariant conjunct (`v ∈ E` / `v ⊆ E` whose bound mentions no machine variable) no longer counts as a use — every variable needs one just to be typed — and an assignment's left-hand side no longer counts as a reference. **EB011** now fires when nothing references a variable outside typing invariants *and* no event assigns it; a write-only variable (assigned but never read) is exempt as an output. As a result a variable that is only initialised and never otherwise used is now reported (previously it was silent). **EB012** now fires when a variable is assigned by INITIALISATION, never modified by any event, and referenced — a constant in disguise — and its severity is raised from INFO to WARNING, so it shows without `--show-info`. A variable that is referenced but never assigned at all is no longer reported by EB012 (its missing initialisation is EB014's concern).
- EB019 (duplicate component definition) raised from WARNING to ERROR: a model that defines the same machine or context more than once is now invalid. The checker still keeps one definition and continues checking the rest.

### Removed

- EB013 (dead constant) removed. A constant that no axiom types is untyped, which is now reported as a type error (EB006), matching Rodin's `UntypedIdentifierError`; a separate dead-constant warning added nothing.

## [1.10] - 2026-07-04

### Fixed

- EB011 (dead variable), EB012 (unmodified variable), and EB014 (incomplete INITIALISATION) no longer judge a machine by its literal file alone: clauses that Event-B materializes into the machine are now counted. An event marked `extended` inherits its abstract event's parameters, guards, and actions (transitively while every link in the REFINES chain stays extended), an extended INITIALISATION inherits the abstract initialisation's actions, and ancestor invariants are inherited unconditionally — so a variable that is referenced or assigned only through such inherited clauses is no longer flagged. On the bundled sample models this removes 34 false positives (EB011/EB012/EB014 across traffic-light, binary-search, and cars-on-bridge). Inherited event parameters are subtracted, so they do not leak in as machine-level references. When an extended INITIALISATION's chain cannot be resolved (missing abstract machine, REFINES cycle, ancestor without an INITIALISATION), EB014 stays silent for that machine rather than guessing; the underlying breakage is already reported by the cross-reference checks (EB008/EB009).

## [1.9] - 2026-07-02

### Added

- New-event and disappeared-variable refinement checks (EB024, EB025). A new event in a refinement — one that refines no abstract event and is not `extended` — implicitly refines `skip`, so it must not modify state inherited and retained from its immediate abstract machine; assigning such a variable is now reported as an error (EB024). A guard or action that references a variable declared in an abstract machine but dropped by this refinement (data-refined away) is reported as EB025, replacing the generic undeclared-identifier error (EB018) that this case produced before.
- Shadowed-name check (EB023, warning). A declared variable, constant, carrier set, or event parameter whose spelling collides with a reserved Event-B operator or keyword token (e.g. `mod`, `card`, `dom`), and so cannot be used as an identifier, is now flagged. Detection uses the Rodin AST factory's own identifier-validity check rather than a hand-maintained operator list.
- Assignment-operator-in-predicate check (EB026). An invariant, guard, witness, or axiom written with an assignment operator (`:=`, `:∈`, `:|`) where a predicate is required is now reported with a precise message instead of a generic formula parse error (EB005).

## [1.8] - 2026-06-25

### Added

- Support for Rodin "Archive File" exports that bundle several top-level project directories into one `.zip` (or directory tree). Each project is now validated independently: `SEES`/`REFINES`/`EXTENDS` references stay project-local, so two sibling projects may reuse component names (both containing `M0`/`C0`, say) without the checker emitting spurious EB019 duplicate-component warnings, dropping one copy, or letting a reference in one project resolve to an identically named component in another. Findings are merged into a single flat list with project-prefixed paths (e.g. `ProjectA/M0.bum`); summary counts and proof totals are summed across projects. Single-project archives, directories, and `.eventb` files are unchanged.
- Installation instructions for the package-manager distributions (Homebrew, Scoop, APT, Copr, Gentoo) in the README.

## [1.7] - 2026-06-17

### Changed

- The Camille (`.eventb`) parser no longer rewrites comma-separated identifier lists in declarations (`sets`, `constants`, `variables`, `any`, `sees`) into whitespace-separated ones before parsing. Such lists are invalid in every real Event-B tool (Camille, CamilleX, and Rodin all separate declared identifiers by whitespace; commas appear only inside formulas), so the checker now reports a Camille parse error instead of silently accepting them. Tolerance for uppercase keywords and label-first `theorem` notation is unchanged.

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
