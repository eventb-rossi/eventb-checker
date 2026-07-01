# eventb-checker

A command-line validator for [Event-B](https://www.event-b.org/) models. It reads `.zip` archives or directories containing `.bum` (machine), `.buc` (context), and/or `.eventb` (Camille textual notation) files and checks them for correctness without requiring a Rodin installation.

## Checks Performed

| Check | Severity | Description |
|-------|----------|-------------|
| XML well-formedness | ERROR | Validates `.bum`/`.buc` files are well-formed XML |
| Camille syntax | ERROR | Parses `.eventb` files using the Camille textual notation grammar |
| Formula syntax | ERROR | Parses predicates, expressions, and assignments using the Rodin AST library |
| Assignment in predicate | ERROR | Reports an invariant, guard, witness, or axiom that uses an assignment operator (`:=`, `:∈`, `:|`) where a predicate is required |
| Undeclared identifiers | ERROR | Reports identifiers not declared in the surrounding context, machine, or event scope |
| Type checking | ERROR | Reports definite type conflicts (e.g. mismatched operand types) found by the Rodin AST type checker |
| Unknown types | WARNING | Reports identifiers whose types cannot be inferred (often constructs the checker does not fully model, such as primed witness variables) |
| Cross-reference integrity | ERROR | Verifies SEES, REFINES, and EXTENDS targets exist in the project |
| Refinement checks | ERROR | Reports new events that assign a variable inherited from an abstract machine, and guards or actions that reference a variable dropped by the refinement |
| Well-definedness | INFO | Reports non-trivial well-definedness conditions (e.g., division by zero) |
| Dead identifiers | WARNING | Detects declared variables/constants never referenced in any formula |
| Unmodified variables | INFO | Flags variables that are never assigned by any event action |
| INITIALISATION completeness | WARNING | Checks that INITIALISATION assigns all declared machine variables |
| Duplicate component definitions | WARNING | Warns when the same machine or context is defined multiple times within the parsed input set |
| Duplicate identifiers/labels | ERROR | Reports an identifier (variable, constant, carrier set, parameter) or label (invariant, event, guard, action, axiom, witness) declared more than once within the same scope, matching Rodin's static checker |
| Shadowed names | WARNING | Flags a declared identifier (variable, constant, carrier set, parameter) whose spelling collides with a reserved Event-B operator or keyword token |
| Proof status | WARNING | Reports undischarged/broken proof obligations from `.bpr`/`.bpo`/`.bps` files (with `--proofs`). The replay status in `.bps` takes precedence over the confidence stored with the `.bpr` proof tree, and broken proofs are counted as pending, matching Rodin |

A model is reported as **VALID** when there are no ERROR-severity findings. Warnings and info findings are reported but do not affect validity.

## Installation

Install the `eventb-checker` command from a package manager (these need a Java 21+ runtime):

```sh
# Homebrew (macOS)
brew tap eventb-rossi/tap
brew install eventb-checker

# Scoop (Windows)
scoop bucket add eventb https://github.com/eventb-rossi/scoop-eventb
scoop install eventb/eventb-checker

# APT (Ubuntu / Debian)
curl -fsSL https://eventb-rossi.github.io/apt/KEY.gpg \
  | sudo gpg --dearmor -o /etc/apt/keyrings/eventb.gpg
. /etc/os-release
echo "deb [signed-by=/etc/apt/keyrings/eventb.gpg] https://eventb-rossi.github.io/apt ${VERSION_CODENAME} main" \
  | sudo tee /etc/apt/sources.list.d/eventb.list
sudo apt update
sudo apt install eventb-checker

# Copr (Fedora / RHEL)
sudo dnf copr enable @eventb-rossi/eventb-copr
sudo dnf install eventb-checker

# Gentoo
eselect repository enable eventb-rossi
emaint sync -r eventb-rossi
emerge sci-mathematics/eventb-checker
```

For Windows machines without a JVM, each [release](https://github.com/eventb-rossi/eventb-checker/releases) also ships self-contained `x64`/`arm64` artifacts that bundle their own Java runtime: the `.msi` installer adds `eventb-checker` to the system `PATH`, and the `.zip` is portable (unzip and run `eventb-checker\eventb-checker.exe`).

## Usage

The checker exposes two subcommands, both accepting either a `.zip` archive or a directory of `.bum`/`.buc`/`.eventb` files:

| Command | Description |
|---------|-------------|
| `check` | Validate a model and report findings (`text`/`json`/`sarif`) |
| `info` | Report read-only facts about a model (currently inferred identifier types) |

```bash
eventb-checker check /path/to/model.zip
eventb-checker check /path/to/model-directory
eventb-checker info /path/to/model.zip --types --format json
```

When a project contains any Rodin XML files (`.bum` or `.buc`), the checker parses only those XML inputs and ignores `.eventb` files. Camille parsing is used only for projects that do not contain XML model files.

### `check` options

| Option | Description |
|--------|-------------|
| `--format`, `-f` | Output format: `text` (default), `json`, or `sarif` |
| `--show-info` | Include INFO-severity findings in output (suppressed by default; hidden INFO findings are also removed from summary counts) |
| `--proofs`, `-p` | Check proof status from `.bpr`/`.bpo`/`.bps` files |

### `info` options

| Option | Description |
|--------|-------------|
| `--types` | Include the inferred types of declared identifiers (at least one fact must be selected) |
| `--format`, `-f` | Output format: `text` (default) or `json` |

`--version` is available on the top-level command (`eventb-checker --version`).

### JSON Output Schema

When using `check --format json`, the output has the following structure:

```json
{
  "valid": true,
  "summary": {
    "machineCount": 2,
    "contextCount": 1,
    "formulaCount": 14,
    "errorCount": 0,
    "warningCount": 0,
    "infoCount": 0,
    "proofSummary": {
      "total": 32,
      "discharged": 26,
      "reviewed": 0,
      "pending": 4,
      "unattempted": 2,
      "broken": 0,
      "manualDischarged": 3
    }
  },
  "errors": [
    {
      "file": "project/Counter.bum",
      "severity": "ERROR",
      "message": "Parse error in invariant",
      "element": "inv1",
      "formula": "x ==== y",
      "ruleId": "EB005"
    }
  ]
}
```

`element`, `formula`, and `ruleId` are `null` when not applicable. `severity` is one of `ERROR`, `WARNING`, or `INFO`. `proofSummary` is only present when `--proofs` is used.

### SARIF Output

When using `check --format sarif`, the output follows the [SARIF 2.1.0](https://docs.oasis-open.org/sarif/sarif/v2.1.0/sarif-v2.1.0.html) standard. This enables integration with GitHub Code Scanning, VS Code SARIF Viewer, and other tools that consume SARIF. Each finding includes a rule ID (e.g., `EB005` for formula parse errors) mapped to the `tool.driver.rules` array.

### Type Information (`info --types`)

`info --types` type-checks the model and reports the inferred types of its
declared constants (per context), variables and event parameters (per machine),
keyed by component and identifier. With `--format json` each requested fact is
nested under its own key (here, `types`), so adding more facts later leaves the
existing shape unchanged:

```json
{
  "types": {
    "contexts": { "C0": { "cars_limit": "ℤ" } },
    "machines": {
      "M0": {
        "variables": { "cars_number": "ℤ" },
        "events": { "ML_out": { "p": "ℙ(S×S)" } }
      }
    }
  }
}
```

Types are Rodin's canonical `Type.toString()` (e.g. `ℙ(S×S)`); identifiers Rodin
leaves untyped are omitted. `info` runs only the type-checker (no other
validators) and never reports invalidity — it exits 0 (success) or 2 (input error).

### Exit Codes

| Code | Meaning |
|------|---------|
| 0 | Model is valid |
| 1 | Model is invalid (has ERROR-severity findings) |
| 2 | Input error (file not found, not a zip/directory, etc.) |

## Example Output

```
=== Event-B Model Validation Report ===

Files:    1 machine(s), 1 context(s)
Formulas: 5 checked
Errors:   0
Warnings: 0
Info:     0

RESULT: VALID
```

With `--show-info`:

```
=== Event-B Model Validation Report ===

Files:    1 machine(s), 1 context(s)
Formulas: 5 checked
Errors:   0
Warnings: 0
Info:     1

RESULT: VALID (with warnings)

--- Counter.bum ---
  INFO : [inv2] Well-definedness condition: y ≠ 0
         formula: x / y
```

## GitHub CI Integration

The simplest option — a single `uses:` step that downloads the release JAR and validates your models.

```yaml
steps:
  - uses: actions/checkout@v6
  - uses: eventb-rossi/eventb-checker@v1.9
    with:
      model-path: "models/*.zip"
```

Results are automatically uploaded to [GitHub Code Scanning](https://docs.github.com/en/code-security/code-scanning) via SARIF, showing findings inline on pull requests and in the Security tab.

If `model-path` matches no files, the action fails as a configuration error instead of succeeding with an empty report.

| Input | Required | Default | Description |
|-------|----------|---------|-------------|
| `model-path` | yes | — | Glob pattern for `.zip` files |
| `checker-version` | no | `"latest"` | Release tag or `"latest"` |
| `show-info` | no | `"false"` | Include INFO-severity findings |
| `proofs` | no | `"false"` | Check proof status from `.bpr`/`.bpo`/`.bps` files |

## GitLab CI Integration

External GitLab projects can validate Event-B models by including the reusable template:

```yaml
include:
  - remote: 'https://raw.githubusercontent.com/eventb-rossi/eventb-checker/main/.gitlab/ci/eventb-checker.yml'

variables:
  EVENTB_MODEL_GLOB: "models/*.zip"
```

This creates an `eventb-validate` job that downloads the release JAR, runs validation, and reports results via JUnit XML in the MR widget.

If `EVENTB_MODEL_GLOB` matches no files, the job fails as a configuration error instead of succeeding with an empty report.

### Configuration Variables

| Variable | Required | Default | Description |
|----------|----------|---------|-------------|
| `EVENTB_MODEL_GLOB` | yes | — | Glob pattern for `.zip` files |
| `EVENTB_JAVA_VERSION` | no | `"21"` | Java version |
| `EVENTB_CHECKER_VERSION` | no | `"latest"` | Release tag or `"latest"` |
| `EVENTB_SHOW_INFO` | no | `"false"` | Include INFO-severity findings |
| `EVENTB_PROOFS` | no | `"false"` | Check proof status from `.bpr`/`.bpo`/`.bps` files |
| `EVENTB_CHECKER_REPO` | no | `"eventb-rossi/eventb-checker"` | GitHub repo for JAR download |

## Development

### Git Hooks Setup

The repository includes pre-commit and pre-push hooks in `.githooks/`. To enable them:

```bash
./gradlew setupGitHooks
# or: git config core.hooksPath .githooks
```

**Pre-commit** — When committing `.kt` or `.kts` files, automatically runs `spotlessApply` to format them, re-stages the formatted files, and verifies with `spotlessCheck`.

**Pre-push** — When pushing a `v*` tag, validates that the tag format is `vMAJOR.MINOR` and that `build.gradle.kts` and `README.md` reference the matching version. This catches version inconsistencies before they reach CI.

To bypass hooks for a specific operation, use `--no-verify`.
