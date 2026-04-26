package com.eventb.checker.validation

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class ValidationResultTest {

    private fun summary() = ValidationSummary(
        machineCount = 1,
        contextCount = 1,
        formulaCount = 10,
        errorCount = 1,
        warningCount = 1,
        infoCount = 2,
    )

    @Test
    fun `withoutInfo removes INFO errors and preserves others`() {
        val result = ValidationResult(
            errors = listOf(
                ValidationError("a.bum", ValidationSeverity.ERROR, "parse error"),
                ValidationError("a.bum", ValidationSeverity.WARNING, "type error"),
                ValidationError("a.bum", ValidationSeverity.INFO, "WD condition 1"),
                ValidationError("b.buc", ValidationSeverity.INFO, "WD condition 2"),
            ),
            summary = summary(),
        )

        val filtered = result.withoutInfo()

        assertThat(filtered.errors).hasSize(2)
        assertThat(filtered.errors.map { it.severity }).containsExactly(
            ValidationSeverity.ERROR,
            ValidationSeverity.WARNING,
        )
    }

    @Test
    fun `withoutInfo recomputes visible summary counts`() {
        val result = ValidationResult(
            errors = listOf(
                ValidationError("a.bum", ValidationSeverity.ERROR, "parse error"),
                ValidationError("a.bum", ValidationSeverity.WARNING, "type error"),
                ValidationError("a.bum", ValidationSeverity.INFO, "WD condition"),
            ),
            summary = summary(),
        )

        val filtered = result.withoutInfo()

        assertThat(filtered.summary.machineCount).isEqualTo(result.summary.machineCount)
        assertThat(filtered.summary.contextCount).isEqualTo(result.summary.contextCount)
        assertThat(filtered.summary.formulaCount).isEqualTo(result.summary.formulaCount)
        assertThat(filtered.summary.proofSummary).isEqualTo(result.summary.proofSummary)
        assertThat(filtered.summary.errorCount).isEqualTo(1)
        assertThat(filtered.summary.warningCount).isEqualTo(1)
        assertThat(filtered.summary.infoCount).isZero()
    }

    @Test
    fun `withoutInfo on result with no INFO errors returns same errors`() {
        val result = ValidationResult(
            errors = listOf(
                ValidationError("a.bum", ValidationSeverity.ERROR, "parse error"),
                ValidationError("a.bum", ValidationSeverity.WARNING, "type error"),
            ),
            summary = summary(),
        )

        val filtered = result.withoutInfo()

        assertThat(filtered.errors).isEqualTo(result.errors)
        assertThat(filtered.summary.errorCount).isEqualTo(result.summary.errorCount)
        assertThat(filtered.summary.warningCount).isEqualTo(result.summary.warningCount)
        assertThat(filtered.summary.infoCount).isZero()
    }

    @Test
    fun `withoutInfo does not affect isValid`() {
        val validResult = ValidationResult(
            errors = listOf(
                ValidationError("a.bum", ValidationSeverity.INFO, "WD condition"),
            ),
            summary = summary(),
        )

        assertThat(validResult.isValid).isTrue()
        assertThat(validResult.withoutInfo().isValid).isTrue()

        val invalidResult = ValidationResult(
            errors = listOf(
                ValidationError("a.bum", ValidationSeverity.ERROR, "parse error"),
                ValidationError("a.bum", ValidationSeverity.INFO, "WD condition"),
            ),
            summary = summary(),
        )

        assertThat(invalidResult.isValid).isFalse()
        assertThat(invalidResult.withoutInfo().isValid).isFalse()
    }
}
