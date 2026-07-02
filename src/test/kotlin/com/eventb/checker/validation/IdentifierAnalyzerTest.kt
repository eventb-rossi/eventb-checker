package com.eventb.checker.validation

import com.eventb.checker.TestModelBuilders.context
import com.eventb.checker.TestModelBuilders.event
import com.eventb.checker.TestModelBuilders.inheritance
import com.eventb.checker.TestModelBuilders.machine
import com.eventb.checker.TestModelBuilders.parsedFormulas
import com.eventb.checker.TestModelBuilders.project
import com.eventb.checker.model.Action
import com.eventb.checker.model.Axiom
import com.eventb.checker.model.CarrierSet
import com.eventb.checker.model.Constant
import com.eventb.checker.model.EventBProject
import com.eventb.checker.model.Guard
import com.eventb.checker.model.Invariant
import com.eventb.checker.model.Parameter
import com.eventb.checker.model.Variable
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class IdentifierAnalyzerTest {

    private val analyzer = IdentifierAnalyzer()

    private fun analyzeProject(project: EventBProject): List<ValidationError> =
        analyzer.analyze(project, parsedFormulas(project), inheritance(project))

    @Test
    fun `no warnings for used variable`() {
        val project = project(
            machines = listOf(
                machine(
                    "M1",
                    variables = listOf(Variable("n", "n")),
                    invariants = listOf(Invariant("inv1", "n ∈ ℤ", false)),
                    events = listOf(
                        event(
                            "INITIALISATION",
                            actions = listOf(Action("act1", "n ≔ 0")),
                        ),
                        event(
                            "inc",
                            actions = listOf(Action("act1", "n ≔ n + 1")),
                        ),
                    ),
                ),
            ),
        )

        val findings = analyzeProject(project)

        assertThat(findings).isEmpty()
    }

    @Test
    fun `dead variable detected`() {
        val project = project(
            machines = listOf(
                machine(
                    "M1",
                    variables = listOf(Variable("x", "x"), Variable("unused", "unused")),
                    invariants = listOf(Invariant("inv1", "x ∈ ℤ", false)),
                    events = listOf(
                        event(
                            "INITIALISATION",
                            actions = listOf(Action("act1", "x ≔ 0")),
                        ),
                    ),
                ),
            ),
        )

        val findings = analyzeProject(project)

        assertThat(findings)
            .filteredOn {
                it.severity == ValidationSeverity.WARNING && it.message.contains("Dead variable") && it.message.contains("unused")
            }
            .singleElement()
    }

    @Test
    fun `dead constant detected`() {
        val project = project(
            contexts = listOf(
                context(
                    "C1",
                    carrierSets = listOf(CarrierSet("S", "S")),
                    constants = listOf(Constant("used", "used"), Constant("unused", "unused")),
                    axioms = listOf(Axiom("axm1", "used ∈ S", false)),
                ),
            ),
        )

        val findings = analyzeProject(project)

        assertThat(findings)
            .filteredOn {
                it.severity == ValidationSeverity.WARNING && it.message.contains("Dead constant") && it.message.contains("unused")
            }
            .singleElement()
    }

    @Test
    fun `unmodified variable detected`() {
        val project = project(
            machines = listOf(
                machine(
                    "M1",
                    variables = listOf(Variable("x", "x")),
                    invariants = listOf(Invariant("inv1", "x ∈ ℤ", false)),
                ),
            ),
        )

        val findings = analyzeProject(project)

        assertThat(findings)
            .filteredOn { it.severity == ValidationSeverity.INFO && it.message.contains("Unmodified variable") && it.message.contains("x") }
            .singleElement()
    }

    @Test
    fun `assigned variable not flagged as unmodified`() {
        val project = project(
            machines = listOf(
                machine(
                    "M1",
                    variables = listOf(Variable("n", "n")),
                    invariants = listOf(Invariant("inv1", "n ∈ ℤ", false)),
                    events = listOf(
                        event(
                            "inc",
                            actions = listOf(Action("act1", "n ≔ n + 1")),
                        ),
                    ),
                ),
            ),
        )

        val findings = analyzeProject(project)

        assertThat(findings)
            .filteredOn { it.message.contains("Unmodified variable") && it.message.contains("'n'") }
            .isEmpty()
    }

    @Test
    fun `carrier set not flagged as dead constant`() {
        val project = project(
            contexts = listOf(
                context(
                    "C1",
                    carrierSets = listOf(CarrierSet("S", "S")),
                    axioms = listOf(Axiom("axm1", "finite(S)", false)),
                ),
            ),
        )

        val findings = analyzeProject(project)

        assertThat(findings)
            .filteredOn { it.message.contains("Dead constant") && it.message.contains("S") }
            .isEmpty()
    }

    @Test
    fun `parameter not flagged as dead variable`() {
        val project = project(
            contexts = listOf(
                context(
                    "C1",
                    carrierSets = listOf(CarrierSet("S", "S")),
                ),
            ),
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

        val findings = analyzeProject(project)

        assertThat(findings)
            .filteredOn { it.message.contains("Dead variable") && it.message.contains("p") }
            .isEmpty()
    }

    @Test
    fun `empty project produces no findings`() {
        val project = project()

        val findings = analyzeProject(project)

        assertThat(findings).isEmpty()
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

        val findings = analyzeProject(project)

        assertThat(findings).filteredOn { it.message.contains("Dead variable") }.isEmpty()
    }

    @Test
    fun `variable assigned only by an inherited action is not unmodified`() {
        val project = project(
            machines = listOf(
                machine(
                    "M0",
                    variables = listOf(Variable("v", "v")),
                    invariants = listOf(Invariant("inv1", "v ∈ ℤ", false)),
                    events = listOf(
                        event("INITIALISATION", actions = listOf(Action("act1", "v ≔ 0"))),
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

        val findings = analyzeProject(project)

        assertThat(findings)
            .filteredOn { it.message.contains("Unmodified variable") || it.message.contains("Dead variable") }
            .isEmpty()
    }

    @Test
    fun `renamed multi level extends chain keeps variable alive`() {
        val project = project(
            machines = listOf(
                machine(
                    "M0",
                    variables = listOf(Variable("v", "v")),
                    events = listOf(
                        event("INITIALISATION", actions = listOf(Action("act1", "v ≔ 0"))),
                        event("abstract_op", actions = listOf(Action("act1", "v ≔ 1"))),
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

        val findings = analyzeProject(project)

        assertThat(findings)
            .filteredOn { it.message.contains("Unmodified variable") || it.message.contains("Dead variable") }
            .isEmpty()
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

        val findings = analyzeProject(project)

        assertThat(findings)
            .filteredOn { it.message.contains("Dead variable") && it.filePath == "M1.bum" }
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

        val findings = analyzeProject(project)

        assertThat(findings).filteredOn { it.message.contains("Dead variable") }.hasSize(2)
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

        val findings = analyzeProject(project)

        assertThat(findings)
            .filteredOn { it.message.contains("Dead variable") && it.message.contains("'p'") }
            .singleElement()
    }

    @Test
    fun `variable referenced only via an inherited invariant is unmodified not dead`() {
        val project = project(
            machines = listOf(
                machine(
                    "M0",
                    variables = listOf(Variable("r", "r")),
                    invariants = listOf(Invariant("inv1", "r ∈ ℤ", false)),
                    events = listOf(event("INITIALISATION", actions = listOf(Action("act1", "r ≔ 0")))),
                ),
                machine(
                    "M1",
                    refinesMachine = "M0",
                    variables = listOf(Variable("r", "r")),
                ),
            ),
        )

        val findings = analyzeProject(project)

        assertThat(findings).filteredOn { it.message.contains("Dead variable") }.isEmpty()
        assertThat(findings)
            .filteredOn { it.message.contains("Unmodified variable") && it.filePath == "M1.bum" }
            .singleElement()
    }

    @Test
    fun `dead constant detection is unaffected by machine inheritance`() {
        val project = project(
            contexts = listOf(
                context("C1", constants = listOf(Constant("k", "k"))),
            ),
            machines = listOf(
                machine(
                    "M0",
                    variables = listOf(Variable("k", "k")),
                    invariants = listOf(Invariant("inv1", "k ∈ ℤ", false)),
                    events = listOf(event("INITIALISATION", actions = listOf(Action("act1", "k ≔ 0")))),
                ),
                machine(
                    "M1",
                    refinesMachine = "M0",
                    seesContexts = listOf("C1"),
                    variables = listOf(Variable("k", "k")),
                    events = listOf(event("INITIALISATION", extended = true)),
                ),
            ),
        )

        val findings = analyzeProject(project)

        assertThat(findings)
            .filteredOn { it.message.contains("Dead constant") && it.message.contains("'k'") }
            .singleElement()
    }
}
