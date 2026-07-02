package com.eventb.checker.integration

import com.eventb.checker.validation.ProjectValidator
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource
import java.io.File
import java.util.stream.Stream

/**
 * Integration tests using real Rodin-exported Event-B models from
 * https://github.com/17451k/eventb-models
 */
class RealModelTest {

    private val validator = ProjectValidator()
    private val validatorWithProofs = ProjectValidator(checkProofs = true)

    private fun resourceZipPath(name: String): String {
        val url = javaClass.getResource("/samples/$name")
            ?: throw IllegalStateException("Test resource not found: /samples/$name")
        return File(url.toURI()).absolutePath
    }

    companion object {
        @JvmStatic
        fun validModels(): Stream<Arguments> = Stream.of(
            Arguments.of("binary-search.zip", 4, 1),
            Arguments.of("cars-on-bridge.zip", 4, 3),
            Arguments.of("file-system.zip", 1, 1),
            Arguments.of("traffic-light.zip", 3, 1),
            Arguments.of("base-model.zip", 1, 1),
        )
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("validModels")
    fun `model validates`(zipName: String, machineCount: Int, contextCount: Int) {
        val result = validator.validate(resourceZipPath(zipName))

        assertThat(result.isValid)
            .describedAs("Validation errors: %s", result.errors)
            .isTrue()
        assertThat(result.summary.machineCount).isEqualTo(machineCount)
        assertThat(result.summary.contextCount).isEqualTo(contextCount)
        assertThat(result.summary.formulaCount).isGreaterThan(0)

        // isValid only counts ERRORs. The samples are also proof-complete Rodin models built on
        // the `extends` idiom: every variable is referenced and initialised once inherited clauses
        // are materialized, so any EB011/EB012/EB014 finding on them is a false positive from
        // judging the literal file instead of the machine Rodin builds (34 of them before
        // MachineInheritanceResolver existed).
        assertThat(result.errors)
            .describedAs("EB011/EB012/EB014 must not fire on the materialized samples")
            .noneMatch { it.ruleId in setOf("EB011", "EB012", "EB014") }
    }

    @Test
    fun `traffic light proof summary reports undischarged POs`() {
        val result = validatorWithProofs.validate(resourceZipPath("traffic-light.zip"))

        val ps = result.summary.proofSummary
        assertThat(ps).isNotNull
        assertThat(ps!!.total).isEqualTo(32)
        assertThat(ps.discharged).isEqualTo(26)
        assertThat(ps.discharged + ps.pending + ps.unattempted).isEqualTo(ps.total)
    }

    @Test
    fun `all samples have proof files and produce summaries`() {
        val samples = listOf(
            "binary-search.zip",
            "cars-on-bridge.zip",
            "file-system.zip",
            "traffic-light.zip",
            "base-model.zip",
        )

        for (sample in samples) {
            val result = validatorWithProofs.validate(resourceZipPath(sample))
            val ps = result.summary.proofSummary
            assertThat(ps)
                .describedAs("Proof summary should be present for $sample")
                .isNotNull
            assertThat(ps!!.total)
                .describedAs("$sample should have proof obligations")
                .isGreaterThan(0)
            assertThat(ps.discharged + ps.reviewed + ps.pending + ps.unattempted)
                .describedAs("$sample PO counts should add up")
                .isEqualTo(ps.total)
        }
    }
}
