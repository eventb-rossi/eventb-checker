package com.eventb.checker.validation

import com.eventb.checker.ModelContents
import com.eventb.checker.ModelEntry
import com.eventb.checker.model.ProofConfidence
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class ProofStatusCheckerTest {

    private val checker = ProofStatusChecker()

    private fun wrapXml(rootElement: String, vararg children: String): String {
        val body = children.joinToString("\n")
        return """<?xml version="1.0" encoding="UTF-8"?>
            <$rootElement version="1">
            $body
            </$rootElement>
        """.trimIndent()
    }

    private fun bprXml(vararg proofs: String) = wrapXml("org.eventb.core.prFile", *proofs)

    private fun bpoXml(vararg sequents: String) = wrapXml("org.eventb.core.poFile", *sequents)

    private fun bpsXml(vararg statuses: String) = wrapXml("org.eventb.core.psFile", *statuses)

    private fun entry(path: String, xml: String) = ModelEntry(path, xml.toByteArray())

    private fun contents(
        proofFiles: List<ModelEntry> = emptyList(),
        poFiles: List<ModelEntry> = emptyList(),
        psFiles: List<ModelEntry> = emptyList(),
    ) = ModelContents(
        machines = emptyList(),
        contexts = emptyList(),
        proofFiles = proofFiles,
        proofObligationFiles = poFiles,
        proofStatusFiles = psFiles,
    )

    @Test
    fun `parse bpr with mix of discharged and pending POs`() {
        val bpr = bprXml(
            """<org.eventb.core.prProof name="INIT/inv1/INV" org.eventb.core.confidence="1000"/>""",
            """<org.eventb.core.prProof name="inc/inv1/INV" org.eventb.core.confidence="1000"/>""",
            """<org.eventb.core.prProof name="inc/grd1/WD" org.eventb.core.confidence="0"/>""",
        )

        val result = checker.check(contents(proofFiles = listOf(entry("project/M0.bpr", bpr))))

        assertThat(result.summary.total).isEqualTo(3)
        assertThat(result.summary.discharged).isEqualTo(2)
        assertThat(result.summary.pending).isEqualTo(1)
    }

    @Test
    fun `parse bpr with unattempted POs (no confidence attribute)`() {
        val bpr = bprXml(
            """<org.eventb.core.prProof name="INIT/inv1/INV" org.eventb.core.confidence="1000"/>""",
            """<org.eventb.core.prProof name="INIT/act1/SIM"/>""",
        )

        val result = checker.check(contents(proofFiles = listOf(entry("project/M0.bpr", bpr))))

        assertThat(result.summary.total).isEqualTo(2)
        assertThat(result.summary.discharged).isEqualTo(1)
        assertThat(result.summary.unattempted).isEqualTo(1)
    }

    @Test
    fun `parse bpo to get complete PO list`() {
        val bpr = bprXml(
            """<org.eventb.core.prProof name="INIT/inv1/INV" org.eventb.core.confidence="1000"/>""",
        )
        val bpo = bpoXml(
            """<org.eventb.core.poSequent name="INIT/inv1/INV" org.eventb.core.poDesc="Invariant establishment"/>""",
            """<org.eventb.core.poSequent name="inc/inv1/INV" org.eventb.core.poDesc="Invariant preservation"/>""",
        )

        val result = checker.check(
            contents(
                proofFiles = listOf(entry("project/M0.bpr", bpr)),
                poFiles = listOf(entry("project/M0.bpo", bpo)),
            ),
        )

        assertThat(result.summary.total).isEqualTo(2)
        assertThat(result.summary.discharged).isEqualTo(1)
        assertThat(result.summary.unattempted).isEqualTo(1)
        assertThat(result.obligations[0].description).isEqualTo("Invariant establishment")
        assertThat(result.obligations[1].description).isEqualTo("Invariant preservation")
    }

    @Test
    fun `parse bps for broken and manual flags`() {
        val bpr = bprXml(
            """<org.eventb.core.prProof name="INIT/inv1/INV" org.eventb.core.confidence="1000"/>""",
            """<org.eventb.core.prProof name="inc/inv1/INV" org.eventb.core.confidence="1000"/>""",
        )
        val bps = bpsXml(
            """<org.eventb.core.psStatus name="INIT/inv1/INV" org.eventb.core.psManual="true" org.eventb.core.psBroken="false"/>""",
            """<org.eventb.core.psStatus name="inc/inv1/INV" org.eventb.core.psManual="false" org.eventb.core.psBroken="true"/>""",
        )

        val result = checker.check(
            contents(
                proofFiles = listOf(entry("project/M0.bpr", bpr)),
                psFiles = listOf(entry("project/M0.bps", bps)),
            ),
        )

        assertThat(result.obligations[0].manual).isTrue()
        assertThat(result.obligations[0].broken).isFalse()
        assertThat(result.obligations[1].manual).isFalse()
        assertThat(result.obligations[1].broken).isTrue()
        assertThat(result.summary.broken).isEqualTo(1)
    }

    @Test
    fun `fall back gracefully when bpo and bps absent`() {
        val bpr = bprXml(
            """<org.eventb.core.prProof name="INIT/inv1/INV" org.eventb.core.confidence="1000"/>""",
        )

        val result = checker.check(contents(proofFiles = listOf(entry("project/M0.bpr", bpr))))

        assertThat(result.obligations).hasSize(1)
        assertThat(result.obligations[0].manual).isFalse()
        assertThat(result.obligations[0].broken).isFalse()
        assertThat(result.obligations[0].description).isNull()
    }

    @Test
    fun `empty bpr file produces no obligations`() {
        val bpr = """<?xml version="1.0" encoding="UTF-8"?>
            <org.eventb.core.prFile version="1"/>
        """.trimIndent()

        val result = checker.check(contents(proofFiles = listOf(entry("project/C1.bpr", bpr))))

        assertThat(result.summary.total).isEqualTo(0)
        assertThat(result.obligations).isEmpty()
        assertThat(result.errors).isEmpty()
    }

    @Test
    fun `malformed XML produces warning error`() {
        val result = checker.check(
            contents(proofFiles = listOf(entry("project/Bad.bpr", "not xml <<<"))),
        )

        assertThat(result.errors)
            .filteredOn { it.severity == ValidationSeverity.WARNING && it.message.contains("Failed to parse proof file") }
            .singleElement()
        assertThat(result.summary.total).isEqualTo(0)
    }

    @Test
    fun `confidence classification thresholds`() {
        val bpr = bprXml(
            """<org.eventb.core.prProof name="a" org.eventb.core.confidence="1000"/>""",
            """<org.eventb.core.prProof name="b" org.eventb.core.confidence="501"/>""",
            """<org.eventb.core.prProof name="c" org.eventb.core.confidence="500"/>""",
            """<org.eventb.core.prProof name="d" org.eventb.core.confidence="101"/>""",
            """<org.eventb.core.prProof name="e" org.eventb.core.confidence="100"/>""",
            """<org.eventb.core.prProof name="f" org.eventb.core.confidence="1"/>""",
            """<org.eventb.core.prProof name="g" org.eventb.core.confidence="0"/>""",
            """<org.eventb.core.prProof name="h" org.eventb.core.confidence="-99"/>""",
            """<org.eventb.core.prProof name="i"/>""",
        )

        val result = checker.check(contents(proofFiles = listOf(entry("project/M.bpr", bpr))))

        val byName = result.obligations.associateBy { it.name }
        assertThat(byName["a"]!!.confidence).isEqualTo(ProofConfidence.DISCHARGED)
        assertThat(byName["b"]!!.confidence).isEqualTo(ProofConfidence.DISCHARGED)
        assertThat(byName["c"]!!.confidence).isEqualTo(ProofConfidence.REVIEWED)
        assertThat(byName["d"]!!.confidence).isEqualTo(ProofConfidence.REVIEWED)
        assertThat(byName["e"]!!.confidence).isEqualTo(ProofConfidence.PENDING)
        assertThat(byName["f"]!!.confidence).isEqualTo(ProofConfidence.PENDING)
        assertThat(byName["g"]!!.confidence).isEqualTo(ProofConfidence.PENDING)
        assertThat(byName["h"]!!.confidence).isEqualTo(ProofConfidence.UNATTEMPTED)
        assertThat(byName["i"]!!.confidence).isEqualTo(ProofConfidence.UNATTEMPTED)
    }

    @Test
    fun `undischarged POs produce warnings`() {
        val bpr = bprXml(
            """<org.eventb.core.prProof name="INIT/inv1/INV" org.eventb.core.confidence="1000"/>""",
            """<org.eventb.core.prProof name="inc/inv1/INV" org.eventb.core.confidence="0"/>""",
        )

        val result = checker.check(contents(proofFiles = listOf(entry("project/M0.bpr", bpr))))

        assertThat(result.errors)
            .filteredOn {
                it.severity == ValidationSeverity.WARNING && it.message.contains("not discharged") && it.element == "inc/inv1/INV"
            }
            .singleElement()
    }

    @Test
    fun `multiple bpr files from different components`() {
        val bpr1 = bprXml(
            """<org.eventb.core.prProof name="INIT/inv1/INV" org.eventb.core.confidence="1000"/>""",
        )
        val bpr2 = bprXml(
            """<org.eventb.core.prProof name="evt/inv1/INV" org.eventb.core.confidence="0"/>""",
        )

        val result = checker.check(
            contents(
                proofFiles = listOf(
                    entry("project/M0.bpr", bpr1),
                    entry("project/M1.bpr", bpr2),
                ),
            ),
        )

        assertThat(result.summary.total).isEqualTo(2)
        assertThat(result.summary.discharged).isEqualTo(1)
        assertThat(result.summary.pending).isEqualTo(1)
        assertThat(result.obligations[0].component).isEqualTo("M0")
        assertThat(result.obligations[1].component).isEqualTo("M1")
    }

    @Test
    fun `proof component names use normalized entry paths`() {
        val bpr = bprXml(
            """<org.eventb.core.prProof name="INIT/inv1/INV" org.eventb.core.confidence="1000"/>""",
        )

        val result = checker.check(contents(proofFiles = listOf(entry("""project\nested\M0.bpr""", bpr))))

        assertThat(result.obligations).hasSize(1)
        assertThat(result.obligations.single().component).isEqualTo("M0")
    }

    @Test
    fun `no proof files produces empty result`() {
        val result = checker.check(contents())

        assertThat(result.summary.total).isEqualTo(0)
        assertThat(result.obligations).isEmpty()
        assertThat(result.errors).isEmpty()
    }
}
