package com.eventb.checker.integration

import com.eventb.checker.TestZipHelper.createZip
import com.eventb.checker.validation.ProjectValidator
import com.eventb.checker.validation.ValidationRules
import com.eventb.checker.validation.ValidationSeverity
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File

/**
 * A Rodin "Archive File" export can bundle several top-level project directories into one
 * archive. References (SEES/REFINES/EXTENDS) are project-local, so each directory must be
 * validated as an independent project rather than flattened into a single namespace — two
 * sibling projects may legitimately both contain components named M0/C0.
 */
class MultiProjectTest {

    @TempDir
    lateinit var tempDir: File

    /** A self-contained, valid project: machine M0 sees context C0 and uses its constant. */
    private fun contextC0(prefix: String) = "$prefix/C0.buc" to """
        <org.eventb.core.contextFile name="C0">
            <org.eventb.core.constant org.eventb.core.identifier="lim"/>
            <org.eventb.core.axiom org.eventb.core.label="axm1"
                org.eventb.core.predicate="lim ∈ ℕ" org.eventb.core.theorem="false"/>
            <org.eventb.core.axiom org.eventb.core.label="axm2"
                org.eventb.core.predicate="lim &gt; 0" org.eventb.core.theorem="false"/>
        </org.eventb.core.contextFile>
    """.trimIndent()

    private fun machineM0(prefix: String) = "$prefix/M0.bum" to """
        <org.eventb.core.machineFile name="M0">
            <org.eventb.core.seesContext org.eventb.core.target="C0"/>
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
    """.trimIndent()

    @Test
    fun `sibling projects with colliding component names are not duplicates`() {
        val zip = createZip(
            tempDir,
            contextC0("ProjectA"),
            machineM0("ProjectA"),
            contextC0("ProjectB"),
            machineM0("ProjectB"),
        )

        val result = ProjectValidator().validate(zip.absolutePath)

        assertThat(result.isValid)
            .describedAs("Validation errors: %s", result.errors)
            .isTrue()
        // Both projects' components are counted; neither is dropped as a "duplicate".
        assertThat(result.summary.machineCount).isEqualTo(2)
        assertThat(result.summary.contextCount).isEqualTo(2)
        assertThat(result.errors).noneMatch { it.ruleId == ValidationRules.DUPLICATE_COMPONENT.id }
        assertThat(result.errors).noneMatch { it.ruleId == ValidationRules.CROSS_REFERENCE_NOT_FOUND.id }
    }

    @Test
    fun `SEES does not resolve to a sibling project's context`() {
        val zip = createZip(
            tempDir,
            // ProjectA's machine sees C0, but C0 exists only in ProjectB.
            "ProjectA/M.bum" to """
                <org.eventb.core.machineFile name="M">
                    <org.eventb.core.seesContext org.eventb.core.target="C0"/>
                </org.eventb.core.machineFile>
            """.trimIndent(),
            contextC0("ProjectB"),
        )

        val result = ProjectValidator().validate(zip.absolutePath)

        assertThat(result.isValid).isFalse()
        assertThat(result.errors).anyMatch {
            it.severity == ValidationSeverity.ERROR &&
                it.ruleId == ValidationRules.CROSS_REFERENCE_NOT_FOUND.id &&
                it.filePath == "ProjectA/M.bum" &&
                it.message.contains("SEES") &&
                it.message.contains("C0")
        }
    }

    @Test
    fun `seen context resolves within its own project, not an identically named sibling`() {
        val zip = createZip(
            tempDir,
            // ProjectA's C0 declares constant c.
            "ProjectA/C0.buc" to """
                <org.eventb.core.contextFile name="C0">
                    <org.eventb.core.constant org.eventb.core.identifier="c"/>
                    <org.eventb.core.axiom org.eventb.core.label="axm1"
                        org.eventb.core.predicate="c ∈ ℕ" org.eventb.core.theorem="false"/>
                </org.eventb.core.contextFile>
            """.trimIndent(),
            // ProjectB's C0 shares the name but does NOT declare c.
            "ProjectB/C0.buc" to """
                <org.eventb.core.contextFile name="C0">
                    <org.eventb.core.carrierSet org.eventb.core.identifier="S"/>
                </org.eventb.core.contextFile>
            """.trimIndent(),
            // ProjectB's machine sees its own C0 and references c, which exists only in ProjectA.
            "ProjectB/M.bum" to """
                <org.eventb.core.machineFile name="M">
                    <org.eventb.core.seesContext org.eventb.core.target="C0"/>
                    <org.eventb.core.invariant org.eventb.core.label="inv1"
                        org.eventb.core.predicate="c ∈ ℕ" org.eventb.core.theorem="false"/>
                </org.eventb.core.machineFile>
            """.trimIndent(),
        )

        val result = ProjectValidator().validate(zip.absolutePath)

        assertThat(result.isValid).isFalse()
        // c must NOT leak from ProjectA's identically-named context.
        assertThat(result.errors).anyMatch {
            it.severity == ValidationSeverity.ERROR &&
                it.ruleId == ValidationRules.UNDECLARED_IDENTIFIER.id &&
                it.filePath == "ProjectB/M.bum" &&
                it.message.contains("'c'")
        }
    }

    @Test
    fun `proof obligations are scoped and counted per project`() {
        val zip = createZip(
            tempDir,
            contextC0("ProjectA"),
            machineM0("ProjectA"),
            "ProjectA/M0.bpr" to """
                <?xml version="1.0" encoding="UTF-8"?>
                <org.eventb.core.prFile version="1">
                    <org.eventb.core.prProof name="INITIALISATION/inv1/INV" org.eventb.core.confidence="1000"/>
                </org.eventb.core.prFile>
            """.trimIndent(),
            contextC0("ProjectB"),
            machineM0("ProjectB"),
            "ProjectB/M0.bpr" to """
                <?xml version="1.0" encoding="UTF-8"?>
                <org.eventb.core.prFile version="1">
                    <org.eventb.core.prProof name="INITIALISATION/inv1/INV" org.eventb.core.confidence="0"/>
                </org.eventb.core.prFile>
            """.trimIndent(),
        )

        val result = ProjectValidator(checkProofs = true).validate(zip.absolutePath)

        val ps = result.summary.proofSummary
        assertThat(ps).isNotNull
        // Same PO name in both projects must not collide: one discharged, one pending.
        assertThat(ps!!.total).isEqualTo(2)
        assertThat(ps.discharged).isEqualTo(1)
        assertThat(ps.pending).isEqualTo(1)
        // ProjectB's undischarged PO is reported against its own prefixed component path.
        assertThat(result.errors).anyMatch {
            it.severity == ValidationSeverity.WARNING &&
                it.message.contains("not discharged") &&
                it.filePath == "ProjectB/M0"
        }
    }

    @Test
    fun `type dump keeps same-named components from every project`() {
        val zip = createZip(
            tempDir,
            contextC0("ProjectA"),
            machineM0("ProjectA"),
            contextC0("ProjectB"),
            machineM0("ProjectB"),
        )

        val dump = ProjectValidator().dumpTypes(zip.absolutePath)

        // Both projects' identically-named components survive, qualified by project prefix
        // (a bare-name merge would silently drop one C0 / one M0).
        assertThat(dump.contexts.keys).contains("ProjectA/C0", "ProjectB/C0")
        assertThat(dump.machines.keys).contains("ProjectA/M0", "ProjectB/M0")
        assertThat(dump.contexts.getValue("ProjectA/C0")).containsKey("lim")
        assertThat(dump.contexts.getValue("ProjectB/C0")).containsKey("lim")
    }

    @Test
    fun `flat archive with no project directory is treated as a single project`() {
        val zip = createZip(
            tempDir,
            "M.bum" to """
                <org.eventb.core.machineFile name="M">
                    <org.eventb.core.seesContext org.eventb.core.target="C"/>
                </org.eventb.core.machineFile>
            """.trimIndent(),
            "C.buc" to """
                <org.eventb.core.contextFile name="C">
                    <org.eventb.core.carrierSet org.eventb.core.identifier="S"/>
                </org.eventb.core.contextFile>
            """.trimIndent(),
        )

        val result = ProjectValidator().validate(zip.absolutePath)

        assertThat(result.isValid)
            .describedAs("Validation errors: %s", result.errors)
            .isTrue()
        assertThat(result.summary.machineCount).isEqualTo(1)
        assertThat(result.summary.contextCount).isEqualTo(1)
    }
}
