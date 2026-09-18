package com.eventb.checker.validation

import com.eventb.checker.TestModelBuilders.checkedFormulas
import com.eventb.checker.TestModelBuilders.context
import com.eventb.checker.TestModelBuilders.machine
import com.eventb.checker.TestModelBuilders.project
import com.eventb.checker.TestStackHelper.DEEP
import com.eventb.checker.TestStackHelper.SMALL_STACK_BYTES
import com.eventb.checker.TestStackHelper.negated
import com.eventb.checker.TestStackHelper.onSmallStack
import com.eventb.checker.model.Axiom
import com.eventb.checker.model.CarrierSet
import com.eventb.checker.model.Constant
import com.eventb.checker.model.Invariant
import com.eventb.checker.model.Variable
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class WellDefinednessCheckerTest {

    private val wdChecker = WellDefinednessChecker()

    @Test
    fun `trivial WD produces no report`() {
        val project = project(
            machines = listOf(
                machine(
                    "M1",
                    variables = listOf(Variable("x", "x")),
                    invariants = listOf(Invariant("inv1", "x ∈ ℤ", false)),
                ),
            ),
        )

        val findings = wdChecker.check(checkedFormulas(project))

        assertThat(findings).isEmpty()
    }

    @Test
    fun `division generates WD condition`() {
        val project = project(
            machines = listOf(
                machine(
                    "M1",
                    variables = listOf(Variable("x", "x"), Variable("y", "y")),
                    invariants = listOf(
                        Invariant("inv1", "x ∈ ℤ", false),
                        Invariant("inv2", "y ∈ ℤ", false),
                        Invariant("inv3", "x ÷ y > 0", false),
                    ),
                ),
            ),
        )

        val findings = wdChecker.check(checkedFormulas(project))

        assertThat(findings).allSatisfy { assertThat(it.severity).isEqualTo(ValidationSeverity.INFO) }
        assertThat(findings).filteredOn { it.message.contains("Well-definedness condition") }.isNotEmpty
    }

    @Test
    fun `function application generates WD condition`() {
        val project = project(
            contexts = listOf(
                context(
                    "C1",
                    carrierSets = listOf(CarrierSet("S", "S")),
                    constants = listOf(Constant("f", "f")),
                    axioms = listOf(
                        Axiom("axm1", "f ∈ S ⇸ ℤ", false),
                    ),
                ),
            ),
            machines = listOf(
                machine(
                    "M1",
                    seesContexts = listOf("C1"),
                    variables = listOf(Variable("x", "x")),
                    invariants = listOf(
                        Invariant("inv1", "x ∈ S", false),
                        Invariant("inv2", "f(x) = 1", false),
                    ),
                ),
            ),
        )

        val findings = wdChecker.check(checkedFormulas(project))

        assertThat(findings).allSatisfy { assertThat(it.severity).isEqualTo(ValidationSeverity.INFO) }
        assertThat(findings).filteredOn { it.message.contains("Well-definedness condition") }.isNotEmpty
    }

    @Test
    fun `simple conjunction has trivial WD`() {
        val project = project(
            machines = listOf(
                machine(
                    "M1",
                    variables = listOf(Variable("a", "a"), Variable("b", "b")),
                    invariants = listOf(
                        Invariant("inv1", "a ∈ ℤ", false),
                        Invariant("inv2", "b ∈ ℤ", false),
                        Invariant("inv3", "a ∈ ℤ ∧ b ∈ ℤ", false),
                    ),
                ),
            ),
        )

        val findings = wdChecker.check(checkedFormulas(project))

        assertThat(findings).isEmpty()
    }

    @Test
    fun `empty formula list produces no findings`() {
        val findings = wdChecker.check(emptyList())

        assertThat(findings).isEmpty()
    }

    @Test
    fun `a formula too deep for a well-definedness walk is skipped rather than fatal`() {
        // Negations, not parentheses: `¬¬¬…P` builds a real AST 5000 nodes deep, where `((((P))))`
        // collapses to nothing. So this formula PARSES and type-checks, and it is the recursive WD
        // walk that runs out of stack -- the case a guard on the parse call alone would miss.
        val project = project(
            machines = listOf(
                machine(
                    "M1",
                    variables = listOf(Variable("x", "x")),
                    invariants = listOf(
                        Invariant("inv1", "x ∈ ℤ", false),
                        Invariant("inv2", negated(DEEP, "x ÷ x = 1"), false),
                    ),
                ),
            ),
        )
        // Built on a large stack so the parse and type-check succeed; only the walk below is
        // starved, which is exactly what this test is about.
        val checked = onSmallStack(stackBytes = 512L * SMALL_STACK_BYTES) { checkedFormulas(project) }
        // Without this the test could pass vacuously, on a formula that never got this far.
        assertThat(checked).anyMatch { it.elementLabel == "inv2" }

        val findings = onSmallStack { wdChecker.check(checked) }

        assertThat(findings).noneMatch { it.element == "inv2" }
    }
}
