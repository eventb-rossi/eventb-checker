package com.eventb.checker.validation

import com.eventb.checker.TestModelBuilders.event
import com.eventb.checker.TestModelBuilders.inheritance
import com.eventb.checker.TestModelBuilders.machine
import com.eventb.checker.TestModelBuilders.project
import com.eventb.checker.model.Action
import com.eventb.checker.model.EventBProject
import com.eventb.checker.model.Invariant
import com.eventb.checker.model.Machine
import com.eventb.checker.model.Variable
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class DisjointAssignmentCheckerTest {

    private val checker = DisjointAssignmentChecker()

    private fun check(project: EventBProject) = checker.check(project, inheritance(project))

    private fun variables(vararg names: String) = names.map { Variable(it, it) }

    /** Abstract machine M0: `inc` increments x. */
    private fun base(): Machine = machine(
        "M0",
        variables = variables("x"),
        invariants = listOf(Invariant("t", "x ∈ ℤ", false)),
        events = listOf(
            event("INITIALISATION", actions = listOf(Action("init", "x ≔ 0"))),
            event("inc", actions = listOf(Action("act1", "x ≔ x + 1"))),
        ),
    )

    @Test
    fun `every action sharing a variable is flagged`() {
        // Rodin's TestEvents.testEvents_16_actionMultipleLHSConflict.
        val project = project(
            machines = listOf(
                machine(
                    "mac",
                    variables = variables("u", "v", "w", "x", "y", "z"),
                    events = listOf(
                        event(
                            "evt",
                            actions = listOf(
                                Action("A1", "u ≔ 1"),
                                Action("A2", "v,w :∣ ⊤"),
                                Action("A3", "v ≔ 1"),
                                Action("A4", "x ≔ u"),
                                Action("A5", "y,z,w ≔ v,w,1"),
                            ),
                        ),
                    ),
                ),
            ),
        )

        val findings = check(project)

        assertThat(findings.map { it.element }).containsExactly("evt/A2", "evt/A3", "evt/A5")
        assertThat(findings).allSatisfy({
            assertThat(it.ruleId).isEqualTo(ValidationRules.CONFLICTING_ASSIGNMENTS.id)
            assertThat(it.severity).isEqualTo(ValidationSeverity.ERROR)
            assertThat(it.filePath).isEqualTo("mac.bum")
        })
        // A3 assigns only 'v' and A5 only 'w', so each is named with its own.
        assertThat(findings[0].message).startsWith(
            "Action 'A2' of event 'evt' assigns 'v', which action 'A3' also assigns, " +
                "and 'w', which action 'A5' also assigns;",
        )
    }

    @Test
    fun `variables shared with the same actions are named together`() {
        val project = project(
            machines = listOf(
                machine(
                    "m",
                    variables = variables("x", "y"),
                    events = listOf(
                        event("e", actions = listOf(Action("a", "x, y ≔ 1, 1"), Action("b", "y, x ≔ 2, 2"))),
                    ),
                ),
            ),
        )

        val findings = check(project)

        assertThat(findings.map { it.element }).containsExactly("e/a", "e/b")
        assertThat(findings[0].message).startsWith("Action 'a' of event 'e' assigns 'x' and 'y', which action 'b' also assigns;")
    }

    @Test
    fun `two updates of one function conflict`() {
        val project = project(
            machines = listOf(
                machine(
                    "m",
                    variables = variables("f"),
                    events = listOf(
                        event("set", actions = listOf(Action("a1", "f(1) ≔ 2"), Action("a2", "f(3) ≔ 4"))),
                    ),
                ),
            ),
        )

        val findings = check(project)

        assertThat(findings.map { it.element }).containsExactly("set/a1", "set/a2")
        assertThat(findings[0].message).contains("assigns 'f', which action 'a2' also assigns;")
    }

    @Test
    fun `an own action of an extended event conflicts with an inherited one`() {
        val project = project(
            machines = listOf(
                base(),
                machine(
                    "M1",
                    refinesMachine = "M0",
                    variables = variables("x", "y"),
                    events = listOf(
                        event("INITIALISATION", extended = true, actions = listOf(Action("init2", "y ≔ 0"))),
                        event(
                            "inc",
                            extended = true,
                            actions = listOf(Action("act2", "x ≔ 0"), Action("act3", "y ≔ y + 1")),
                        ),
                    ),
                ),
            ),
        )

        val findings = check(project)

        assertThat(findings).singleElement().satisfies({
            assertThat(it.element).isEqualTo("inc/act2")
            assertThat(it.filePath).isEqualTo("M1.bum")
            assertThat(it.message).contains("assigns 'x', which an inherited action also assigns;")
        })
    }

    @Test
    fun `an extended INITIALISATION may not assign an inherited variable again`() {
        val project = project(
            machines = listOf(
                base(),
                machine(
                    "M1",
                    refinesMachine = "M0",
                    variables = variables("x", "y"),
                    events = listOf(
                        event(
                            "INITIALISATION",
                            extended = true,
                            actions = listOf(Action("init2", "x ≔ 1"), Action("init3", "y ≔ 0")),
                        ),
                    ),
                ),
            ),
        )

        assertThat(check(project).map { it.element }).containsExactly("INITIALISATION/init2")
    }

    @Test
    fun `an action naming a variable twice conflicts with itself`() {
        val project = project(
            machines = listOf(
                machine("m", variables = variables("x"), events = listOf(event("e", actions = listOf(Action("a1", "x,x ≔ 1,2"))))),
            ),
        )

        assertThat(check(project)).singleElement().satisfies({
            assertThat(it.element).isEqualTo("e/a1")
            assertThat(it.message).startsWith("Action 'a1' of event 'e' assigns 'x' more than once;")
        })
    }

    @Test
    fun `disjoint actions, a multiple assignment and a refining event are accepted`() {
        val project = project(
            machines = listOf(
                base(),
                machine(
                    "M1",
                    refinesMachine = "M0",
                    variables = variables("x", "y"),
                    events = listOf(
                        event("INITIALISATION", actions = listOf(Action("init", "x ≔ 0"), Action("init2", "y ≔ 0"))),
                        // Refining without `extended` inherits no action, so assigning x is fine.
                        event("inc", refinesEvents = listOf("inc"), actions = listOf(Action("act1", "x ≔ x + 2"))),
                        event("swap", actions = listOf(Action("a1", "x,y ≔ y,x"))),
                    ),
                ),
            ),
        )

        assertThat(check(project)).isEmpty()
    }
}
