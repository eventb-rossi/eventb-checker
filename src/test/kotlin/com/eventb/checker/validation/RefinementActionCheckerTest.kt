package com.eventb.checker.validation

import com.eventb.checker.TestModelBuilders.event
import com.eventb.checker.TestModelBuilders.machine
import com.eventb.checker.TestModelBuilders.project
import com.eventb.checker.model.Action
import com.eventb.checker.model.EventBProject
import com.eventb.checker.model.Invariant
import com.eventb.checker.model.Machine
import com.eventb.checker.model.Variable
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class RefinementActionCheckerTest {

    private val checker = RefinementActionChecker()

    private fun check(project: EventBProject) = checker.check(project)

    /** Abstract machine M0 with a variable v that only its INITIALISATION assigns. */
    private fun base(): Machine = machine(
        "M0",
        variables = listOf(Variable("v", "v")),
        invariants = listOf(Invariant("inv1", "v ∈ ℤ", false)),
        events = listOf(event("INITIALISATION", actions = listOf(Action("act1", "v ≔ 0")))),
    )

    @Test
    fun `new event assigning retained inherited variable is flagged`() {
        val project = project(
            machines = listOf(
                base(),
                machine(
                    "M1",
                    refinesMachine = "M0",
                    variables = listOf(Variable("v", "v")),
                    invariants = listOf(Invariant("inv1", "v ∈ ℤ", false)),
                    events = listOf(
                        event("INITIALISATION", actions = listOf(Action("act1", "v ≔ 0"))),
                        event("newstep", actions = listOf(Action("act1", "v ≔ v + 1"))),
                    ),
                ),
            ),
        )

        val findings = check(project)

        assertThat(findings).singleElement().satisfies({
            assertThat(it.ruleId).isEqualTo(ValidationRules.NEW_EVENT_ASSIGNS_INHERITED_VARIABLE.id)
            assertThat(it.severity).isEqualTo(ValidationSeverity.ERROR)
            assertThat(it.element).isEqualTo("newstep/act1")
            assertThat(it.message).contains("'v'")
        })
    }

    @Test
    fun `refining event assigning inherited variable is not flagged`() {
        val project = project(
            machines = listOf(
                machine(
                    "M0",
                    variables = listOf(Variable("v", "v")),
                    invariants = listOf(Invariant("inv1", "v ∈ ℤ", false)),
                    events = listOf(
                        event("INITIALISATION", actions = listOf(Action("act1", "v ≔ 0"))),
                        event("step", actions = listOf(Action("act1", "v ≔ v + 1"))),
                    ),
                ),
                machine(
                    "M1",
                    refinesMachine = "M0",
                    variables = listOf(Variable("v", "v")),
                    invariants = listOf(Invariant("inv1", "v ∈ ℤ", false)),
                    events = listOf(
                        event("INITIALISATION", actions = listOf(Action("act1", "v ≔ 0"))),
                        event("step", refinesEvents = listOf("step"), actions = listOf(Action("act1", "v ≔ v + 1"))),
                    ),
                ),
            ),
        )

        assertThat(check(project)).isEmpty()
    }

    @Test
    fun `extended event assigning inherited variable is not flagged`() {
        val project = project(
            machines = listOf(
                machine(
                    "M0",
                    variables = listOf(Variable("v", "v")),
                    invariants = listOf(Invariant("inv1", "v ∈ ℤ", false)),
                    events = listOf(
                        event("INITIALISATION", actions = listOf(Action("act1", "v ≔ 0"))),
                        event("step", actions = listOf(Action("act1", "v ≔ v + 1"))),
                    ),
                ),
                machine(
                    "M1",
                    refinesMachine = "M0",
                    variables = listOf(Variable("v", "v")),
                    invariants = listOf(Invariant("inv1", "v ∈ ℤ", false)),
                    events = listOf(
                        event("INITIALISATION", actions = listOf(Action("act1", "v ≔ 0"))),
                        event("step", extended = true, actions = listOf(Action("act1", "v ≔ v + 1"))),
                    ),
                ),
            ),
        )

        assertThat(check(project)).isEmpty()
    }

    @Test
    fun `INITIALISATION assigning inherited variable is not flagged`() {
        val project = project(
            machines = listOf(
                base(),
                machine(
                    "M1",
                    refinesMachine = "M0",
                    variables = listOf(Variable("v", "v")),
                    invariants = listOf(Invariant("inv1", "v ∈ ℤ", false)),
                    events = listOf(
                        event("INITIALISATION", actions = listOf(Action("act1", "v ≔ 1"))),
                    ),
                ),
            ),
        )

        assertThat(check(project)).isEmpty()
    }

    @Test
    fun `new event assigning a fresh (non-inherited) variable is not flagged`() {
        val project = project(
            machines = listOf(
                base(),
                machine(
                    "M1",
                    refinesMachine = "M0",
                    variables = listOf(Variable("v", "v"), Variable("w", "w")),
                    invariants = listOf(Invariant("inv1", "v ∈ ℤ ∧ w ∈ ℤ", false)),
                    events = listOf(
                        event(
                            "INITIALISATION",
                            actions = listOf(Action("act1", "v ≔ 0"), Action("act2", "w ≔ 0")),
                        ),
                        event("newstep", actions = listOf(Action("act1", "w ≔ w + 1"))),
                    ),
                ),
            ),
        )

        assertThat(check(project)).isEmpty()
    }

    @Test
    fun `variable dropped by an intermediate machine and reintroduced later is not inherited`() {
        // M0 declares v; M1 drops it; M2 re-declares v as a genuinely new variable. Inheritance is
        // relative to the immediate parent (M1), which lacks v, so a new event in M2 may assign it.
        val project = project(
            machines = listOf(
                base(),
                machine(
                    "M1",
                    refinesMachine = "M0",
                    events = listOf(event("INITIALISATION")),
                ),
                machine(
                    "M2",
                    refinesMachine = "M1",
                    variables = listOf(Variable("v", "v")),
                    invariants = listOf(Invariant("inv1", "v ∈ ℤ", false)),
                    events = listOf(
                        event("INITIALISATION", actions = listOf(Action("act1", "v ≔ 0"))),
                        event("newstep", actions = listOf(Action("act1", "v ≔ v + 1"))),
                    ),
                ),
            ),
        )

        assertThat(check(project)).isEmpty()
    }
}
