package com.eventb.checker.validation

import com.eventb.checker.TestModelBuilders.context
import com.eventb.checker.TestModelBuilders.event
import com.eventb.checker.TestModelBuilders.inheritance
import com.eventb.checker.TestModelBuilders.machine
import com.eventb.checker.TestModelBuilders.project
import com.eventb.checker.model.Action
import com.eventb.checker.model.CarrierSet
import com.eventb.checker.model.EventBProject
import com.eventb.checker.model.Guard
import com.eventb.checker.model.Invariant
import com.eventb.checker.model.Parameter
import com.eventb.checker.model.Variable
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class IdentifierAnalyzerTest {

    private val analyzer = IdentifierAnalyzer()

    private fun analyze(project: EventBProject): List<ValidationError> = analyzer.analyze(project, inheritance(project))

    private fun List<ValidationError>.dead() = filter { it.ruleId == ValidationRules.DEAD_VARIABLE.id }

    private fun List<ValidationError>.unmodified() = filter { it.ruleId == ValidationRules.UNMODIFIED_VARIABLE.id }

    @Test
    fun `no warnings for a genuinely used variable`() {
        val project = project(
            machines = listOf(
                machine(
                    "M1",
                    variables = listOf(Variable("n", "n")),
                    invariants = listOf(Invariant("inv1", "n ∈ ℤ", false)),
                    events = listOf(
                        event("INITIALISATION", actions = listOf(Action("act1", "n ≔ 0"))),
                        event("inc", actions = listOf(Action("act1", "n ≔ n + 1"))),
                    ),
                ),
            ),
        )

        assertThat(analyze(project)).isEmpty()
    }

    @Test
    fun `unused variable is dead`() {
        val project = project(
            machines = listOf(
                machine(
                    "M1",
                    variables = listOf(Variable("x", "x"), Variable("unused", "unused")),
                    invariants = listOf(Invariant("inv1", "x ∈ ℤ", false)),
                    events = listOf(
                        event("INITIALISATION", actions = listOf(Action("act1", "x ≔ 0"))),
                        event("inc", actions = listOf(Action("act1", "x ≔ x + 1"))),
                    ),
                ),
            ),
        )

        val dead = analyze(project).dead()
        assertThat(dead).singleElement().satisfies({
            assertThat(it.severity).isEqualTo(ValidationSeverity.WARNING)
            assertThat(it.message).contains("unused")
        })
    }

    @Test
    fun `variable typed only by a typing invariant is dead`() {
        // `x ∈ ℤ` types x but is not a use of it; no event assigns x either, so x serves no purpose.
        val project = project(
            machines = listOf(
                machine(
                    "M1",
                    variables = listOf(Variable("x", "x")),
                    invariants = listOf(Invariant("inv1", "x ∈ ℤ", false)),
                    events = listOf(event("INITIALISATION", actions = listOf(Action("act1", "x ≔ 0")))),
                ),
            ),
        )

        assertThat(analyze(project).dead())
            .singleElement()
            .satisfies({ assertThat(it.message).contains("x") })
        assertThat(analyze(project).unmodified()).isEmpty()
    }

    @Test
    fun `write-only variable is not flagged`() {
        // `w` is assigned by an event but never read — an output. It is neither dead nor unmodified.
        val project = project(
            machines = listOf(
                machine(
                    "M1",
                    variables = listOf(Variable("w", "w")),
                    events = listOf(
                        event("INITIALISATION", actions = listOf(Action("act1", "w ≔ 0"))),
                        event("emit", actions = listOf(Action("act1", "w ≔ 42"))),
                    ),
                ),
            ),
        )

        assertThat(analyze(project)).isEmpty()
    }

    @Test
    fun `init-assigned variable never modified is unmodified`() {
        // `c` is set once by INITIALISATION, read by a real invariant, and never modified — a constant
        // in disguise.
        val project = project(
            machines = listOf(
                machine(
                    "M1",
                    variables = listOf(Variable("c", "c")),
                    invariants = listOf(Invariant("inv1", "c > 0", false)),
                    events = listOf(event("INITIALISATION", actions = listOf(Action("act1", "c ≔ 1")))),
                ),
            ),
        )

        assertThat(analyze(project).unmodified())
            .singleElement()
            .satisfies({
                assertThat(it.severity).isEqualTo(ValidationSeverity.WARNING)
                assertThat(it.message).contains("c")
            })
        assertThat(analyze(project).dead()).isEmpty()
    }

    @Test
    fun `variable modified by an event is not unmodified`() {
        val project = project(
            machines = listOf(
                machine(
                    "M1",
                    variables = listOf(Variable("n", "n")),
                    invariants = listOf(Invariant("inv1", "n > 0", false)),
                    events = listOf(
                        event("INITIALISATION", actions = listOf(Action("act1", "n ≔ 1"))),
                        event("inc", actions = listOf(Action("act1", "n ≔ n + 1"))),
                    ),
                ),
            ),
        )

        assertThat(analyze(project).unmodified()).isEmpty()
    }

    @Test
    fun `referenced but never assigned variable draws no finding`() {
        // `q` is read but never assigned (not even by INITIALISATION): not dead (it is referenced) and
        // not unmodified (EB012 is only for INIT-assigned variables). Its missing init is EB014's domain.
        val project = project(
            machines = listOf(
                machine(
                    "M1",
                    variables = listOf(Variable("q", "q")),
                    invariants = listOf(Invariant("inv1", "q > 0", false)),
                ),
            ),
        )

        assertThat(analyze(project)).isEmpty()
    }

    @Test
    fun `parameter is not treated as a variable`() {
        val project = project(
            contexts = listOf(context("C1", carrierSets = listOf(CarrierSet("S", "S")))),
            machines = listOf(
                machine(
                    "M1",
                    seesContexts = listOf("C1"),
                    variables = listOf(Variable("x", "x")),
                    invariants = listOf(Invariant("inv1", "x ∈ S", false)),
                    events = listOf(
                        event(
                            "update",
                            parameters = listOf(Parameter("p", "p")),
                            guards = listOf(Guard("grd1", "p ∈ S", false)),
                            actions = listOf(Action("act1", "x ≔ p")),
                        ),
                    ),
                ),
            ),
        )

        assertThat(analyze(project)).noneMatch { it.message.contains("'p'") }
    }

    @Test
    fun `empty project produces no findings`() {
        assertThat(analyze(project())).isEmpty()
    }

    @Test
    fun `variable referenced only by an inherited guard is not dead`() {
        val project = project(
            machines = listOf(
                machine(
                    "M0",
                    variables = listOf(Variable("v", "v")),
                    events = listOf(
                        event("INITIALISATION", actions = listOf(Action("act1", "v ≔ 0"))),
                        event("op", guards = listOf(Guard("grd1", "v > 0", false))),
                    ),
                ),
                machine(
                    "M1",
                    refinesMachine = "M0",
                    variables = listOf(Variable("v", "v")),
                    events = listOf(event("op", extended = true)),
                ),
            ),
        )

        assertThat(analyze(project).dead()).isEmpty()
    }

    @Test
    fun `variable assigned only by an inherited action is not unmodified`() {
        val project = project(
            machines = listOf(
                machine(
                    "M0",
                    variables = listOf(Variable("v", "v")),
                    invariants = listOf(Invariant("inv1", "v > 0", false)),
                    events = listOf(
                        event("INITIALISATION", actions = listOf(Action("act1", "v ≔ 1"))),
                        event("op", actions = listOf(Action("act1", "v ≔ v + 1"))),
                    ),
                ),
                machine(
                    "M1",
                    refinesMachine = "M0",
                    variables = listOf(Variable("v", "v")),
                    events = listOf(
                        event("INITIALISATION", extended = true),
                        event("op", extended = true),
                    ),
                ),
            ),
        )

        assertThat(analyze(project).unmodified()).isEmpty()
        assertThat(analyze(project).dead()).isEmpty()
    }

    @Test
    fun `renamed multi level extends chain keeps variable alive`() {
        val project = project(
            machines = listOf(
                machine(
                    "M0",
                    variables = listOf(Variable("v", "v")),
                    invariants = listOf(Invariant("inv1", "v > 0", false)),
                    events = listOf(
                        event("INITIALISATION", actions = listOf(Action("act1", "v ≔ 1"))),
                        event("abstract_op", actions = listOf(Action("act1", "v ≔ v + 1"))),
                    ),
                ),
                machine(
                    "M1",
                    refinesMachine = "M0",
                    variables = listOf(Variable("v", "v")),
                    events = listOf(
                        event("INITIALISATION", extended = true),
                        event("mid_op", extended = true, refinesEvents = listOf("abstract_op")),
                    ),
                ),
                machine(
                    "M2",
                    refinesMachine = "M1",
                    variables = listOf(Variable("v", "v")),
                    events = listOf(
                        event("INITIALISATION", extended = true),
                        event("mid_op", extended = true),
                    ),
                ),
            ),
        )

        assertThat(analyze(project).unmodified()).isEmpty()
        assertThat(analyze(project).dead()).isEmpty()
    }

    @Test
    fun `plain refines event does not inherit clauses`() {
        val project = project(
            machines = listOf(
                machine(
                    "M0",
                    variables = listOf(Variable("v", "v")),
                    events = listOf(
                        event("INITIALISATION", actions = listOf(Action("act1", "v ≔ 0"))),
                        event("op", guards = listOf(Guard("grd1", "v > 0", false))),
                    ),
                ),
                machine(
                    "M1",
                    refinesMachine = "M0",
                    variables = listOf(Variable("v", "v")),
                    events = listOf(event("op", refinesEvents = listOf("op"), guards = listOf(Guard("grd1", "1 > 0", false)))),
                ),
            ),
        )

        assertThat(analyze(project).dead())
            .filteredOn { it.filePath == "M1.bum" }
            .singleElement()
    }

    @Test
    fun `refines cycle still terminates and reports dead variables`() {
        val project = project(
            machines = listOf(
                machine(
                    "M1",
                    refinesMachine = "M2",
                    variables = listOf(Variable("v", "v")),
                    events = listOf(event("op", extended = true)),
                ),
                machine(
                    "M2",
                    refinesMachine = "M1",
                    variables = listOf(Variable("v", "v")),
                    events = listOf(event("op", extended = true)),
                ),
            ),
        )

        assertThat(analyze(project).dead()).hasSize(2)
    }

    @Test
    fun `inherited parameter does not keep a colliding variable alive`() {
        val project = project(
            machines = listOf(
                machine(
                    "M0",
                    events = listOf(
                        event(
                            "op",
                            parameters = listOf(Parameter("p", "p")),
                            guards = listOf(Guard("grd1", "p ∈ ℤ", false)),
                        ),
                    ),
                ),
                machine(
                    "M1",
                    refinesMachine = "M0",
                    variables = listOf(Variable("p", "p")),
                    events = listOf(event("op", extended = true)),
                ),
            ),
        )

        assertThat(analyze(project).dead())
            .filteredOn { it.message.contains("'p'") }
            .singleElement()
    }

    @Test
    fun `variable referenced only via an inherited non-typing invariant is not dead`() {
        // M0 keeps r alive through a real (non-typing) invariant; M1 refines M0 and re-declares r
        // without using it. The inherited invariant reference must reach M1 so r is not flagged dead.
        val project = project(
            machines = listOf(
                machine(
                    "M0",
                    variables = listOf(Variable("r", "r")),
                    invariants = listOf(Invariant("inv1", "r > 0", false)),
                    events = listOf(event("INITIALISATION", actions = listOf(Action("act1", "r ≔ 1")))),
                ),
                machine(
                    "M1",
                    refinesMachine = "M0",
                    variables = listOf(Variable("r", "r")),
                    events = listOf(event("INITIALISATION", extended = true)),
                ),
            ),
        )

        assertThat(analyze(project).dead()).isEmpty()
    }

    @Test
    fun `unresolvable extended initialisation chain suppresses the variable lints`() {
        // M1 refines an absent M0 with an extended INITIALISATION, so what it initialises is unknown.
        // The broken REFINES is reported elsewhere (EB009); the lints must not guess `v` is dead.
        val project = project(
            machines = listOf(
                machine(
                    "M1",
                    refinesMachine = "M0",
                    variables = listOf(Variable("v", "v")),
                    invariants = listOf(Invariant("inv1", "v ∈ ℤ", false)),
                    events = listOf(event("INITIALISATION", extended = true)),
                ),
            ),
        )

        assertThat(analyze(project)).isEmpty()
    }
}
