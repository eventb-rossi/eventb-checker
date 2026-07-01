package com.eventb.checker.validation

import com.eventb.checker.TestModelBuilders.context
import com.eventb.checker.TestModelBuilders.event
import com.eventb.checker.TestModelBuilders.machine
import com.eventb.checker.TestModelBuilders.project
import com.eventb.checker.model.CarrierSet
import com.eventb.checker.model.Constant
import com.eventb.checker.model.Parameter
import com.eventb.checker.model.Variable
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class ShadowedNameCheckerTest {

    private val checker = ShadowedNameChecker()

    @Test
    fun `variable named like an operator is flagged`() {
        val project = project(
            machines = listOf(
                machine("M1", variables = listOf(Variable("mod", "mod"))),
            ),
        )

        val findings = checker.check(project)

        assertThat(findings).singleElement().satisfies({
            assertThat(it.ruleId).isEqualTo(ValidationRules.SHADOWED_NAME.id)
            assertThat(it.severity).isEqualTo(ValidationSeverity.WARNING)
            assertThat(it.message).contains("mod")
        })
    }

    @Test
    fun `carrier set and constant named like operators are both flagged`() {
        val project = project(
            contexts = listOf(
                context(
                    "C1",
                    carrierSets = listOf(CarrierSet("dom", "dom")),
                    constants = listOf(Constant("card", "card")),
                ),
            ),
        )

        val findings = checker.check(project)

        assertThat(findings)
            .filteredOn { it.ruleId == ValidationRules.SHADOWED_NAME.id }
            .hasSize(2)
    }

    @Test
    fun `event parameter named like an operator is flagged`() {
        val project = project(
            machines = listOf(
                machine(
                    "M1",
                    events = listOf(event("evt", parameters = listOf(Parameter("union", "union")))),
                ),
            ),
        )

        val findings = checker.check(project)

        assertThat(findings).singleElement().satisfies({
            assertThat(it.ruleId).isEqualTo(ValidationRules.SHADOWED_NAME.id)
            assertThat(it.message).contains("union")
        })
    }

    @Test
    fun `ordinary names are not flagged`() {
        val project = project(
            machines = listOf(
                machine("M1", variables = listOf(Variable("counter", "counter"), Variable("x", "x"))),
            ),
            contexts = listOf(
                context("C1", constants = listOf(Constant("value", "value"))),
            ),
        )

        val findings = checker.check(project)

        assertThat(findings).isEmpty()
    }
}
