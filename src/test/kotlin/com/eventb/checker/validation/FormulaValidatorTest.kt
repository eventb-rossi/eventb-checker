package com.eventb.checker.validation

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class FormulaValidatorTest {

    private val validator = FormulaValidator()

    @Test
    fun `valid predicates parse without problems`() {
        for (formula in listOf("x = 1", "x ∈ ℤ", "x > 0 ∧ y > 0")) {
            val errors = validator.validate(formulaCheck(formula, FormulaKind.PREDICATE))
            assertThat(errors).describedAs("Expected no errors for: $formula").isEmpty()
        }
    }

    @Test
    fun `invalid predicate reports problems`() {
        val errors = validator.validate(formulaCheck("x ==== y", FormulaKind.PREDICATE))
        assertThat(errors).isNotEmpty
    }

    @Test
    fun `valid expressions parse without problems`() {
        for (formula in listOf("x + 1", "card(s)")) {
            val errors = validator.validate(formulaCheck(formula, FormulaKind.EXPRESSION))
            assertThat(errors).describedAs("Expected no errors for: $formula").isEmpty()
        }
    }

    @Test
    fun `invalid expression reports problems`() {
        val errors = validator.validate(formulaCheck("+ + +", FormulaKind.EXPRESSION))
        assertThat(errors).isNotEmpty
    }

    @Test
    fun `valid assignments parse without problems`() {
        for (formula in listOf("x ≔ x + 1", "x :∈ ℤ")) {
            val errors = validator.validate(formulaCheck(formula, FormulaKind.ASSIGNMENT))
            assertThat(errors).describedAs("Expected no errors for: $formula").isEmpty()
        }
    }

    @Test
    fun `invalid assignment reports problems`() {
        val errors = validator.validate(formulaCheck("not an assignment at all", FormulaKind.ASSIGNMENT))
        assertThat(errors).isNotEmpty
    }

    @Test
    fun `parallel deterministic assignment requires matching arity`() {
        for (formula in listOf("x, y ≔ 1", "x ≔ 1, 2")) {
            val errors = validator.validate(formulaCheck(formula, FormulaKind.ASSIGNMENT))

            assertThat(errors).describedAs("Expected EB005 for: $formula").singleElement()
                .satisfies({
                    assertThat(it.ruleId).isEqualTo(ValidationRules.FORMULA_PARSE_ERROR.id)
                    assertThat(it.severity).isEqualTo(ValidationSeverity.ERROR)
                    assertThat(it.message).contains("Incompatible number of arguments")
                })
        }
    }

    @Test
    fun `validate check produces errors for invalid formula`() {
        val check = FormulaValidator.FormulaCheck(
            filePath = "test.bum",
            elementLabel = "inv1",
            formula = "x ==== y",
            kind = FormulaKind.PREDICATE,
        )

        val errors = validator.validate(check)

        assertThat(errors).isNotEmpty
        assertThat(errors[0].filePath).isEqualTo("test.bum")
        assertThat(errors[0].element).isEqualTo("inv1")
        assertThat(errors[0].severity).isEqualTo(ValidationSeverity.ERROR)
    }

    @Test
    fun `assignment written as a predicate is reported as EB026`() {
        for (formula in listOf("x ≔ 5", "x :∈ ℕ", "x :∣ x' > 0")) {
            val errors = validator.validate(formulaCheck(formula, FormulaKind.PREDICATE))
            assertThat(errors).describedAs("Expected EB026 for: $formula").singleElement()
                .satisfies({
                    assertThat(it.ruleId).isEqualTo(ValidationRules.ASSIGNMENT_IN_PREDICATE.id)
                    assertThat(it.severity).isEqualTo(ValidationSeverity.ERROR)
                })
        }
    }

    @Test
    fun `malformed predicate that is not an assignment stays EB005`() {
        val errors = validator.validate(formulaCheck("x ==== y", FormulaKind.PREDICATE))
        assertThat(errors).singleElement()
            .satisfies({ assertThat(it.ruleId).isEqualTo(ValidationRules.FORMULA_PARSE_ERROR.id) })
    }

    @Test
    fun `valid predicate is not misreported as an assignment`() {
        val errors = validator.validate(formulaCheck("x = 5", FormulaKind.PREDICATE))
        assertThat(errors).isEmpty()
    }

    private fun formulaCheck(formula: String, kind: FormulaKind) = FormulaValidator.FormulaCheck(
        filePath = "test.bum",
        elementLabel = "test",
        formula = formula,
        kind = kind,
    )
}
