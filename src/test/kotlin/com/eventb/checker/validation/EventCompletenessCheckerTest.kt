package com.eventb.checker.validation

import com.eventb.checker.TestModelBuilders.event
import com.eventb.checker.TestModelBuilders.inheritance
import com.eventb.checker.TestModelBuilders.machine
import com.eventb.checker.TestModelBuilders.project
import com.eventb.checker.model.Action
import com.eventb.checker.model.EventBProject
import com.eventb.checker.model.Invariant
import com.eventb.checker.model.Variable
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class EventCompletenessCheckerTest {

    private val checker = EventCompletenessChecker()

    private fun checkProject(project: EventBProject): List<ValidationError> = checker.check(project, inheritance(project))

    /** An abstract machine with variable v typed by an invariant and assigned by its INITIALISATION. */
    private fun abstractM0(initAction: String = "v ≔ 0") = machine(
        "M0",
        variables = listOf(Variable("v", "v")),
        invariants = listOf(Invariant("inv1", "v ∈ ℤ", false)),
        events = listOf(event("INITIALISATION", actions = listOf(Action("act1", initAction)))),
    )

    @Test
    fun `complete INITIALISATION produces no warnings`() {
        val project = project(
            machines = listOf(
                machine(
                    "M1",
                    variables = listOf(Variable("x", "x"), Variable("y", "y")),
                    invariants = listOf(
                        Invariant("inv1", "x ∈ ℤ", false),
                        Invariant("inv2", "y ∈ ℤ", false),
                    ),
                    events = listOf(
                        event(
                            "INITIALISATION",
                            actions = listOf(
                                Action("act1", "x ≔ 0"),
                                Action("act2", "y ≔ 0"),
                            ),
                        ),
                    ),
                ),
            ),
        )

        val findings = checkProject(project)

        assertThat(findings).isEmpty()
    }

    @Test
    fun `incomplete INITIALISATION warns for missing variable`() {
        val project = project(
            machines = listOf(
                machine(
                    "M1",
                    variables = listOf(Variable("x", "x"), Variable("y", "y")),
                    invariants = listOf(
                        Invariant("inv1", "x ∈ ℤ", false),
                        Invariant("inv2", "y ∈ ℤ", false),
                    ),
                    events = listOf(
                        event(
                            "INITIALISATION",
                            actions = listOf(Action("act1", "x ≔ 0")),
                        ),
                    ),
                ),
            ),
        )

        val findings = checkProject(project)

        assertThat(findings).hasSize(1)
        assertThat(findings[0].severity).isEqualTo(ValidationSeverity.WARNING)
        assertThat(findings[0].message).contains("INITIALISATION").contains("y")
    }

    @Test
    fun `no INITIALISATION event produces no findings`() {
        val project = project(
            machines = listOf(
                machine(
                    "M1",
                    variables = listOf(Variable("x", "x")),
                    invariants = listOf(Invariant("inv1", "x ∈ ℤ", false)),
                    events = listOf(
                        event(
                            "inc",
                            actions = listOf(Action("act1", "x ≔ x + 1")),
                        ),
                    ),
                ),
            ),
        )

        val findings = checkProject(project)

        assertThat(findings).isEmpty()
    }

    @Test
    fun `no variables produces no findings`() {
        val project = project(
            machines = listOf(
                machine(
                    "M1",
                    events = listOf(
                        event("INITIALISATION"),
                    ),
                ),
            ),
        )

        val findings = checkProject(project)

        assertThat(findings).isEmpty()
    }

    @Test
    fun `empty project produces no findings`() {
        val project = project()

        val findings = checkProject(project)

        assertThat(findings).isEmpty()
    }

    @Test
    fun `extended INITIALISATION inheriting abstract assignments is complete`() {
        val project = project(
            machines = listOf(
                abstractM0(),
                machine(
                    "M1",
                    refinesMachine = "M0",
                    variables = listOf(Variable("v", "v")),
                    events = listOf(event("INITIALISATION", extended = true)),
                ),
            ),
        )

        val findings = checkProject(project)

        assertThat(findings).isEmpty()
    }

    @Test
    fun `extended INITIALISATION missing its own new variable is flagged`() {
        val project = project(
            machines = listOf(
                abstractM0(),
                machine(
                    "M1",
                    refinesMachine = "M0",
                    variables = listOf(Variable("v", "v"), Variable("w", "w")),
                    invariants = listOf(Invariant("inv1", "w ∈ ℤ", false)),
                    events = listOf(event("INITIALISATION", extended = true)),
                ),
            ),
        )

        val findings = checkProject(project)

        assertThat(findings).singleElement().satisfies({
            assertThat(it.message).contains("'w'")
            assertThat(it.filePath).isEqualTo("M1.bum")
        })
    }

    @Test
    fun `extended INITIALISATION with missing parent machine is silent`() {
        val project = project(
            machines = listOf(
                machine(
                    "M1",
                    refinesMachine = "Absent",
                    variables = listOf(Variable("v", "v")),
                    events = listOf(event("INITIALISATION", extended = true)),
                ),
            ),
        )

        val findings = checkProject(project)

        assertThat(findings).isEmpty()
    }

    @Test
    fun `extended INITIALISATION with ancestor lacking INITIALISATION is silent`() {
        val project = project(
            machines = listOf(
                machine("M0", variables = listOf(Variable("v", "v"))),
                machine(
                    "M1",
                    refinesMachine = "M0",
                    variables = listOf(Variable("v", "v")),
                    events = listOf(event("INITIALISATION", extended = true)),
                ),
            ),
        )

        val findings = checkProject(project)

        assertThat(findings).isEmpty()
    }

    @Test
    fun `extended INITIALISATION with refines cycle is silent`() {
        val project = project(
            machines = listOf(
                machine(
                    "M1",
                    refinesMachine = "M2",
                    variables = listOf(Variable("v", "v")),
                    events = listOf(event("INITIALISATION", extended = true)),
                ),
                machine(
                    "M2",
                    refinesMachine = "M1",
                    variables = listOf(Variable("v", "v")),
                    events = listOf(event("INITIALISATION", extended = true)),
                ),
            ),
        )

        val findings = checkProject(project)

        assertThat(findings).isEmpty()
    }

    @Test
    fun `extended INITIALISATION with unparseable inherited action is silent`() {
        val project = project(
            machines = listOf(
                // A predicate where an assignment belongs: what this INIT assigns is unknowable,
                // so the descendant's inherited chain must count as unresolved.
                abstractM0(initAction = "v = 0"),
                machine(
                    "M1",
                    refinesMachine = "M0",
                    variables = listOf(Variable("v", "v")),
                    events = listOf(event("INITIALISATION", extended = true)),
                ),
            ),
        )

        val findings = checkProject(project)

        assertThat(findings).filteredOn { it.filePath == "M1.bum" }.isEmpty()
    }

    @Test
    fun `multi level extended INITIALISATION covering every variable is complete`() {
        val project = project(
            machines = listOf(
                abstractM0(),
                machine(
                    "M1",
                    refinesMachine = "M0",
                    variables = listOf(Variable("v", "v"), Variable("w", "w")),
                    invariants = listOf(Invariant("inv1", "w ∈ ℤ", false)),
                    events = listOf(event("INITIALISATION", extended = true, actions = listOf(Action("act2", "w ≔ 0")))),
                ),
                machine(
                    "M2",
                    refinesMachine = "M1",
                    variables = listOf(Variable("v", "v"), Variable("w", "w"), Variable("u", "u")),
                    invariants = listOf(Invariant("inv1", "u ∈ ℤ", false)),
                    events = listOf(event("INITIALISATION", extended = true, actions = listOf(Action("act3", "u ≔ 0")))),
                ),
            ),
        )

        val findings = checkProject(project)

        assertThat(findings).isEmpty()
    }
}
