package com.eventb.checker

import org.assertj.core.api.Assertions.assertThat
import org.json.JSONObject
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File

class ValidationScriptsTest {

    @TempDir
    lateinit var tempDir: File

    private val repoRoot = File(System.getProperty("user.dir"))

    @Test
    fun `github validation script fails when no models match`() {
        val result = runGitHubScript("missing/*.zip")

        assertThat(result.exitCode).isEqualTo(1)
        assertThat(result.output).contains("No model files matched")
        assertThat(result.sarifFile.exists()).isTrue()
        assertThat(JSONObject(result.sarifFile.readText()).getJSONArray("runs")).isEmpty()
        assertThat(result.githubOutput.readText()).contains("valid=false")
        // Code Scanning rejects an empty runs array, so action.yml skips the upload on this.
        assertThat(result.githubOutput.readText()).contains("sarif-run-count=0")
    }

    @Test
    fun `gitlab validation script fails when no models match`() {
        val result = runGitLabScript("missing/*.zip")

        assertThat(result.exitCode).isEqualTo(1)
        assertThat(result.output).contains("No model files matched")
        assertThat(result.gitLabJUnit.readText()).contains("""tests="1"""").contains("""failures="1"""")
        assertThat(result.gitLabJUnit.readText()).contains("MODEL_GLOB 'missing/*.zip' matched no files")
        assertThat(JSONObject(result.gitLabJson.readText()).getBoolean("valid")).isFalse()
    }

    @Test
    fun `github validation script succeeds for valid models`() {
        File(tempDir, "valid.zip").writeText("placeholder")

        val result = runGitHubScript("*.zip")

        assertThat(result.exitCode).isZero()
        assertThat(result.githubOutput.readText()).contains("valid=true")
        assertThat(result.githubOutput.readText()).contains("error-count=0")
        assertThat(result.githubOutput.readText()).contains("warning-count=0")

        val runs = JSONObject(result.sarifFile.readText()).getJSONArray("runs")
        assertThat(runs).hasSize(1)
        assertThat(runs.getJSONObject(0).getJSONArray("results")).isEmpty()
    }

    @Test
    fun `gitlab validation script fails for invalid models and writes artifacts`() {
        File(tempDir, "invalid.zip").writeText("placeholder")

        val result = runGitLabScript("*.zip")

        assertThat(result.exitCode).isEqualTo(1)
        assertThat(JSONObject(result.gitLabJson.readText()).getBoolean("valid")).isFalse()
        assertThat(JSONObject(result.gitLabJson.readText()).getInt("errorCount")).isEqualTo(1)
        assertThat(result.gitLabJUnit.readText()).contains("invalid.zip")
        assertThat(JSONObject(result.sarifFile.readText()).getJSONArray("runs")).hasSize(1)
    }

    @Test
    fun `github validation script merges several models into a single SARIF run`() {
        // Named so the clean model sorts first: taking tool.driver from the first run
        // alone would lose every rule descriptor the later models reference.
        File(tempDir, "a-clean.zip").writeText("placeholder")
        File(tempDir, "b-invalid.zip").writeText("placeholder")
        File(tempDir, "c-warning.zip").writeText("placeholder")

        val result = runGitHubScript("*.zip")

        assertThat(result.exitCode).isEqualTo(1)
        assertThat(result.githubOutput.readText())
            .contains("error-count=1").contains("warning-count=1").contains("sarif-run-count=3")

        // Code Scanning rejects several runs sharing a category, so there must be exactly one.
        val runs = runsOf(result)
        assertThat(runs).hasSize(1)

        val run = runs.getJSONObject(0)
        assertThat(urisOf(run)).containsExactly("b-invalid.zip", "c-warning.zip")
        assertThat(modelsOf(run)).containsExactly("b-invalid.zip", "c-warning.zip")
        assertThat(ruleIdsOf(run)).containsExactlyInAnyOrder("EB005", "EB011")
        assertThat(run.getJSONObject("tool").getJSONObject("driver").getString("name")).isEqualTo("fake")
    }

    @Test
    fun `github validation script keeps results in model order past nine models`() {
        val names = (1..11).map { "m$it-warning.zip" }
        names.forEach { File(tempDir, it).writeText("placeholder") }

        val result = runGitHubScript("*.zip")

        assertThat(result.exitCode).isZero()
        // Glob order, not the lexicographic run_1, run_10, run_11, run_2, ... of the temp files.
        assertThat(urisOf(runsOf(result).getJSONObject(0))).containsExactlyElementsOf(names.sorted())
    }

    private fun runGitHubScript(modelGlob: String): ScriptRunResult = runScript(
        script = repoRoot.resolve(".github/scripts/validate-models.sh"),
        modelGlob = modelGlob,
        includeGitHubOutput = true,
    )

    private fun runGitLabScript(modelGlob: String): ScriptRunResult = runScript(
        script = repoRoot.resolve(".gitlab/scripts/validate-models.sh"),
        modelGlob = modelGlob,
        includeGitHubOutput = false,
    )

    private fun runScript(script: File, modelGlob: String, includeGitHubOutput: Boolean): ScriptRunResult {
        val fakeChecker = writeFakeChecker()
        val githubOutput = File(tempDir, "github-output.txt")
        val process = ProcessBuilder("bash", script.absolutePath)
            .directory(tempDir)
            .redirectErrorStream(true)
            .apply {
                // Byte-order glob expansion, so the shell's ordering matches the
                // codepoint order the ordering test compares it against. A locale
                // such as en_US.UTF-8 collates "m10-" before "m1-" by ignoring the
                // punctuation, which would fail the test for an unrelated reason.
                environment()["LC_ALL"] = "C"
                environment()["CHECKER_CMD"] = fakeChecker.absolutePath
                environment()["MODEL_GLOB"] = modelGlob
                environment()["SHOW_INFO_FLAG"] = ""
                environment()["PROOFS_FLAG"] = ""
                if (includeGitHubOutput) {
                    environment()["GITHUB_OUTPUT"] = githubOutput.absolutePath
                }
            }
            .start()

        val output = process.inputStream.bufferedReader().use { it.readText() }
        val exitCode = process.waitFor()

        return ScriptRunResult(
            exitCode = exitCode,
            output = output,
            sarifFile = File(tempDir, "eventb-checker-results.sarif"),
            gitLabJUnit = File(tempDir, "eventb-validation-results.xml"),
            gitLabJson = File(tempDir, "eventb-validation-report.json"),
            githubOutput = githubOutput,
        )
    }

    /**
     * A stand-in for the checker. Each arm names its target in the message and the
     * artifact URI, and declares only the rule its own finding references — mirroring
     * [com.eventb.checker.report.SarifReportFormatter], which filters `tool.driver.rules`
     * per model. That per-model filtering is what makes the merge's rule union necessary.
     */
    private fun writeFakeChecker(): File {
        val script = File(tempDir, "fake-checker.sh")
        script.writeText(
            """
            #!/usr/bin/env bash
            set -euo pipefail
            target="${'$'}{!#}"
            name=${'$'}(basename "${'$'}target")
            emit() {
              printf '{"%s":"%s","version":"2.1.0","runs":[{"tool":{"driver":{"name":"fake","version":"0.0","informationUri":"https://example.invalid","rules":%s}},"results":%s}]}\n' \
                '${'$'}schema' \
                'https://docs.oasis-open.org/sarif/sarif/v2.1.0/errata01/os/schemas/sarif-schema-2.1.0.json' \
                "${'$'}1" "${'$'}2"
            }
            if [[ "${'$'}name" == *invalid*.zip ]]; then
              emit '[{"id":"EB005","shortDescription":{"text":"Formula parse error"}}]' \
                   '[{"ruleId":"EB005","level":"error","message":{"text":"broken model '"${'$'}name"'"},"locations":[{"physicalLocation":{"artifactLocation":{"uri":"'"${'$'}name"'"}}}]}]'
              exit 1
            fi
            if [[ "${'$'}name" == *warning*.zip ]]; then
              emit '[{"id":"EB011","shortDescription":{"text":"Dead variable"}}]' \
                   '[{"ruleId":"EB011","level":"warning","message":{"text":"dead variable in '"${'$'}name"'"},"locations":[{"physicalLocation":{"artifactLocation":{"uri":"'"${'$'}name"'"}}}]}]'
              exit 0
            fi
            emit '[]' '[]'
            """.trimIndent(),
        )
        check(script.setExecutable(true))
        return script
    }

    private fun runsOf(result: ScriptRunResult) = JSONObject(result.sarifFile.readText()).getJSONArray("runs")

    private fun ruleIdsOf(run: JSONObject): List<String> {
        val rules = run.getJSONObject("tool").getJSONObject("driver").getJSONArray("rules")
        return (0 until rules.length()).map { rules.getJSONObject(it).getString("id") }
    }

    private fun modelsOf(run: JSONObject): List<String> {
        val results = run.getJSONArray("results")
        return (0 until results.length()).map {
            results.getJSONObject(it).getJSONObject("properties").getString("model")
        }
    }

    private fun urisOf(run: JSONObject): List<String> {
        val results = run.getJSONArray("results")
        return (0 until results.length()).map {
            results.getJSONObject(it).getJSONArray("locations").getJSONObject(0)
                .getJSONObject("physicalLocation").getJSONObject("artifactLocation").getString("uri")
        }
    }

    private data class ScriptRunResult(
        val exitCode: Int,
        val output: String,
        val sarifFile: File,
        val gitLabJUnit: File,
        val gitLabJson: File,
        val githubOutput: File,
    )
}
