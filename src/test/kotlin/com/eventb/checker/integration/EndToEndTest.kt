package com.eventb.checker.integration

import com.eventb.checker.TestZipHelper.createZip
import com.eventb.checker.validation.ProjectValidator
import com.eventb.checker.validation.ValidationRules
import com.eventb.checker.validation.ValidationSeverity
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File

class EndToEndTest {

    @TempDir
    lateinit var tempDir: File

    private val validator = ProjectValidator()

    @Test
    fun `valid model passes all checks`() {
        val zip = createZip(
            tempDir,
            "project/Counter.bum" to """
                <org.eventb.core.machineFile name="Counter">
                    <org.eventb.core.seesContext org.eventb.core.target="Limits"/>
                    <org.eventb.core.variable org.eventb.core.identifier="n" org.eventb.core.label="n"/>
                    <org.eventb.core.invariant org.eventb.core.label="inv1"
                        org.eventb.core.predicate="n ∈ ℕ" org.eventb.core.theorem="false"/>
                    <org.eventb.core.event org.eventb.core.label="INITIALISATION"
                        org.eventb.core.convergence="0" org.eventb.core.extended="false">
                        <org.eventb.core.action org.eventb.core.label="act1"
                            org.eventb.core.assignment="n ≔ 0"/>
                    </org.eventb.core.event>
                    <org.eventb.core.event org.eventb.core.label="increment"
                        org.eventb.core.convergence="0" org.eventb.core.extended="false">
                        <org.eventb.core.guard org.eventb.core.label="grd1"
                            org.eventb.core.predicate="n &lt; lim"/>
                        <org.eventb.core.action org.eventb.core.label="act1"
                            org.eventb.core.assignment="n ≔ n + 1"/>
                    </org.eventb.core.event>
                </org.eventb.core.machineFile>
            """.trimIndent(),
            "project/Limits.buc" to """
                <org.eventb.core.contextFile name="Limits">
                    <org.eventb.core.constant org.eventb.core.identifier="lim"/>
                    <org.eventb.core.axiom org.eventb.core.label="axm1"
                        org.eventb.core.predicate="lim ∈ ℕ" org.eventb.core.theorem="false"/>
                    <org.eventb.core.axiom org.eventb.core.label="axm2"
                        org.eventb.core.predicate="lim &gt; 0" org.eventb.core.theorem="false"/>
                </org.eventb.core.contextFile>
            """.trimIndent(),
        )

        val result = validator.validate(zip.absolutePath)

        assertThat(result.isValid).isTrue()
        assertThat(result.summary.machineCount).isEqualTo(1)
        assertThat(result.summary.contextCount).isEqualTo(1)
        assertThat(result.summary.formulaCount).isGreaterThan(0)
    }

    @Test
    fun `invalid formula is reported`() {
        val zip = createZip(
            tempDir,
            "project/Bad.bum" to """
                <org.eventb.core.machineFile name="Bad">
                    <org.eventb.core.invariant org.eventb.core.label="inv1"
                        org.eventb.core.predicate="x ==== y" org.eventb.core.theorem="false"/>
                </org.eventb.core.machineFile>
            """.trimIndent(),
        )

        val result = validator.validate(zip.absolutePath)

        assertThat(result.isValid).isFalse()
        assertThat(result.errors).anyMatch {
            it.severity == ValidationSeverity.ERROR && it.message.contains("Formula parse error")
        }
    }

    @Test
    fun `missing cross reference is reported`() {
        val zip = createZip(
            tempDir,
            "project/M.bum" to """
                <org.eventb.core.machineFile name="M">
                    <org.eventb.core.seesContext org.eventb.core.target="NonExistent"/>
                </org.eventb.core.machineFile>
            """.trimIndent(),
        )

        val result = validator.validate(zip.absolutePath)

        assertThat(result.isValid).isFalse()
        assertThat(result.errors).anyMatch {
            it.message.contains("SEES") && it.message.contains("NonExistent")
        }
    }

    @Test
    fun `empty project produces valid result with zero counts`() {
        val zip = createZip(
            tempDir,
            "project/.project" to "<projectDescription/>",
        )

        val result = validator.validate(zip.absolutePath)

        assertThat(result.isValid).isTrue()
        assertThat(result.summary.machineCount).isEqualTo(0)
        assertThat(result.summary.contextCount).isEqualTo(0)
    }

    @Test
    fun `malformed XML reports error`() {
        val zip = createZip(
            tempDir,
            "project/Broken.bum" to "this is not xml at all <<<",
        )

        val result = validator.validate(zip.absolutePath)

        assertThat(result.isValid).isFalse()
        assertThat(result.errors).anyMatch { it.message.contains("Failed to parse XML") }
    }

    @Test
    fun `refinement chain validates correctly`() {
        val zip = createZip(
            tempDir,
            "project/Base.bum" to """
                <org.eventb.core.machineFile name="Base">
                    <org.eventb.core.variable org.eventb.core.identifier="x" org.eventb.core.label="x"/>
                    <org.eventb.core.invariant org.eventb.core.label="inv1"
                        org.eventb.core.predicate="x ∈ ℕ" org.eventb.core.theorem="false"/>
                </org.eventb.core.machineFile>
            """.trimIndent(),
            "project/Refined.bum" to """
                <org.eventb.core.machineFile name="Refined">
                    <org.eventb.core.refinesMachine org.eventb.core.target="Base"/>
                    <org.eventb.core.variable org.eventb.core.identifier="y" org.eventb.core.label="y"/>
                    <org.eventb.core.invariant org.eventb.core.label="inv1"
                        org.eventb.core.predicate="y ∈ ℕ" org.eventb.core.theorem="false"/>
                </org.eventb.core.machineFile>
            """.trimIndent(),
        )

        val result = validator.validate(zip.absolutePath)

        assertThat(result.isValid).isTrue()
        assertThat(result.summary.machineCount).isEqualTo(2)
    }

    @Test
    fun `eventb files validate end to end`() {
        val projectDir = File(tempDir, "project")
        projectDir.mkdirs()
        File(projectDir, "Limits.eventb").writeText(
            """
            context Limits
            constants lim
            axioms
              @axm1 lim ∈ ℕ
              @axm2 lim > 0
            end
            """.trimIndent(),
        )
        File(projectDir, "Counter.eventb").writeText(
            """
            machine Counter
            sees Limits
            variables n
            invariants
              @inv1 n ∈ ℕ
            events
              event INITIALISATION
              then
                @act1 n ≔ 0
              end
              event increment
                where
                  @grd1 n < lim
                then
                  @act1 n ≔ n + 1
              end
            end
            """.trimIndent(),
        )

        val result = validator.validate(projectDir.absolutePath)

        assertThat(result.isValid).isTrue()
        assertThat(result.summary.machineCount).isEqualTo(1)
        assertThat(result.summary.contextCount).isEqualTo(1)
        assertThat(result.summary.formulaCount).isGreaterThan(0)
    }

    @Test
    fun `multi-unit eventb file validates end to end`() {
        val projectDir = File(tempDir, "project")
        projectDir.mkdirs()
        File(projectDir, "models.eventb").writeText(
            """
            context Limits
            constants lim
            axioms
              @axm1 lim ∈ ℕ
              @axm2 lim > 0
            end

            machine Counter
            sees Limits
            variables n
            invariants
              @inv1 n ∈ ℕ
            events
              event INITIALISATION
              then
                @act1 n ≔ 0
              end
              event increment
                where
                  @grd1 n < lim
                then
                  @act1 n ≔ n + 1
              end
            end
            """.trimIndent(),
        )

        val result = validator.validate(projectDir.absolutePath)

        assertThat(result.isValid).isTrue()
        assertThat(result.summary.machineCount).isEqualTo(1)
        assertThat(result.summary.contextCount).isEqualTo(1)
        assertThat(result.summary.formulaCount).isGreaterThan(0)
    }

    @Test
    fun `xml input suppresses eventb parsing`() {
        val projectDir = File(tempDir, "project")
        projectDir.mkdirs()
        File(projectDir, "Limits.buc").writeText(
            """
                <org.eventb.core.contextFile name="Limits">
                    <org.eventb.core.constant org.eventb.core.identifier="lim"/>
                    <org.eventb.core.axiom org.eventb.core.label="axm1"
                        org.eventb.core.predicate="lim ∈ ℕ" org.eventb.core.theorem="false"/>
                    <org.eventb.core.axiom org.eventb.core.label="axm2"
                        org.eventb.core.predicate="lim &gt; 0" org.eventb.core.theorem="false"/>
                </org.eventb.core.contextFile>
            """.trimIndent(),
        )
        File(projectDir, "Counter.eventb").writeText(
            """
            machine Counter
            sees Limits
            variables n
            invariants
              @inv1 n ∈ ℕ
            events
              event INITIALISATION
              then
                @act1 n ≔ 0
              end
              event increment
                where
                  @grd1 n < lim
                then
                  @act1 n ≔ n + 1
              end
            end
            """.trimIndent(),
        )

        val result = validator.validate(projectDir.absolutePath)

        assertThat(result.isValid).isTrue()
        assertThat(result.summary.machineCount).isEqualTo(0)
        assertThat(result.summary.contextCount).isEqualTo(1)
    }

    @Test
    fun `xml input ignores malformed eventb files`() {
        val projectDir = File(tempDir, "project")
        projectDir.mkdirs()
        File(projectDir, "M.bum").writeText(
            """
                <org.eventb.core.machineFile name="M">
                    <org.eventb.core.variable org.eventb.core.identifier="x" org.eventb.core.label="x"/>
                    <org.eventb.core.invariant org.eventb.core.label="inv1"
                        org.eventb.core.predicate="x ∈ ℕ" org.eventb.core.theorem="false"/>
                </org.eventb.core.machineFile>
            """.trimIndent(),
        )
        File(projectDir, "M.eventb").writeText(
            """
            machine M
              invariants
                @inv1 x ====
            end
            """.trimIndent(),
        )

        val result = validator.validate(projectDir.absolutePath)

        assertThat(result.isValid).isTrue()
        assertThat(result.summary.machineCount).isEqualTo(1)
        assertThat(result.errors).noneMatch { it.filePath.endsWith(".eventb") }
        assertThat(result.errors).noneMatch { it.ruleId == ValidationRules.DUPLICATE_COMPONENT.id }
    }

    @Test
    fun `sorted path winner is used for duplicate eventb components`() {
        val projectDir = File(tempDir, "project")
        projectDir.mkdirs()
        File(projectDir, "B.eventb").writeText(
            """
            machine M
            sees Missing
            end
            """.trimIndent(),
        )
        File(projectDir, "A.eventb").writeText("machine M end")

        val result = validator.validate(projectDir.absolutePath)

        assertThat(result.isValid).isTrue()
        assertThat(result.summary.machineCount).isEqualTo(1)
        assertThat(result.errors).anyMatch {
            it.severity == ValidationSeverity.WARNING &&
                it.ruleId == ValidationRules.DUPLICATE_COMPONENT.id &&
                it.filePath.endsWith("B.eventb")
        }
        assertThat(result.errors).noneMatch { it.message.contains("Missing") }
    }

    @Test
    fun `proof status reports undischarged POs as warnings`() {
        val zip = createZip(
            tempDir,
            "project/M0.bum" to """
                <org.eventb.core.machineFile name="M0">
                    <org.eventb.core.variable org.eventb.core.identifier="x" org.eventb.core.label="x"/>
                    <org.eventb.core.invariant org.eventb.core.label="inv1"
                        org.eventb.core.predicate="x ∈ ℕ" org.eventb.core.theorem="false"/>
                    <org.eventb.core.event org.eventb.core.label="INITIALISATION"
                        org.eventb.core.convergence="0" org.eventb.core.extended="false">
                        <org.eventb.core.action org.eventb.core.label="act1"
                            org.eventb.core.assignment="x ≔ 0"/>
                    </org.eventb.core.event>
                </org.eventb.core.machineFile>
            """.trimIndent(),
            "project/M0.bpr" to """
                <?xml version="1.0" encoding="UTF-8"?>
                <org.eventb.core.prFile version="1">
                    <org.eventb.core.prProof name="INITIALISATION/inv1/INV" org.eventb.core.confidence="1000"/>
                    <org.eventb.core.prProof name="evt/inv1/INV" org.eventb.core.confidence="0"/>
                </org.eventb.core.prFile>
            """.trimIndent(),
        )

        val validator = ProjectValidator(checkProofs = true)
        val result = validator.validate(zip.absolutePath)

        assertThat(result.isValid).isTrue()
        assertThat(result.summary.proofSummary).isNotNull
        assertThat(result.summary.proofSummary!!.total).isEqualTo(2)
        assertThat(result.summary.proofSummary.discharged).isEqualTo(1)
        assertThat(result.summary.proofSummary.pending).isEqualTo(1)
        assertThat(result.errors).anyMatch {
            it.severity == ValidationSeverity.WARNING && it.message.contains("not discharged")
        }
    }

    @Test
    fun `proof status not checked without flag`() {
        val zip = createZip(
            tempDir,
            "project/M0.bum" to """
                <org.eventb.core.machineFile name="M0">
                    <org.eventb.core.variable org.eventb.core.identifier="x" org.eventb.core.label="x"/>
                    <org.eventb.core.invariant org.eventb.core.label="inv1"
                        org.eventb.core.predicate="x ∈ ℕ" org.eventb.core.theorem="false"/>
                    <org.eventb.core.event org.eventb.core.label="INITIALISATION"
                        org.eventb.core.convergence="0" org.eventb.core.extended="false">
                        <org.eventb.core.action org.eventb.core.label="act1"
                            org.eventb.core.assignment="x ≔ 0"/>
                    </org.eventb.core.event>
                </org.eventb.core.machineFile>
            """.trimIndent(),
            "project/M0.bpr" to """
                <?xml version="1.0" encoding="UTF-8"?>
                <org.eventb.core.prFile version="1">
                    <org.eventb.core.prProof name="evt/inv1/INV" org.eventb.core.confidence="0"/>
                </org.eventb.core.prFile>
            """.trimIndent(),
        )

        val result = validator.validate(zip.absolutePath)

        assertThat(result.summary.proofSummary).isNull()
        assertThat(result.errors).noneMatch { it.message.contains("not discharged") }
    }

    @Test
    fun `context extension validates correctly`() {
        val zip = createZip(
            tempDir,
            "project/BaseCtx.buc" to """
                <org.eventb.core.contextFile name="BaseCtx">
                    <org.eventb.core.carrierSet org.eventb.core.identifier="S"/>
                </org.eventb.core.contextFile>
            """.trimIndent(),
            "project/ExtCtx.buc" to """
                <org.eventb.core.contextFile name="ExtCtx">
                    <org.eventb.core.extendsContext org.eventb.core.target="BaseCtx"/>
                    <org.eventb.core.constant org.eventb.core.identifier="c"/>
                    <org.eventb.core.axiom org.eventb.core.label="axm1"
                        org.eventb.core.predicate="c ∈ S" org.eventb.core.theorem="false"/>
                </org.eventb.core.contextFile>
            """.trimIndent(),
        )

        val result = validator.validate(zip.absolutePath)

        assertThat(result.isValid).isTrue()
        assertThat(result.summary.contextCount).isEqualTo(2)
    }
}
