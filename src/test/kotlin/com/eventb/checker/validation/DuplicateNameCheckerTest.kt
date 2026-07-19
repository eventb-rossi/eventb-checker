package com.eventb.checker.validation

import com.eventb.checker.TestModelBuilders.context
import com.eventb.checker.TestModelBuilders.event
import com.eventb.checker.TestModelBuilders.machine
import com.eventb.checker.TestModelBuilders.project
import com.eventb.checker.model.Action
import com.eventb.checker.model.Axiom
import com.eventb.checker.model.CarrierSet
import com.eventb.checker.model.Constant
import com.eventb.checker.model.Guard
import com.eventb.checker.model.Invariant
import com.eventb.checker.model.Parameter
import com.eventb.checker.model.Variable
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class DuplicateNameCheckerTest {

    private val checker = DuplicateNameChecker()

    @Test
    fun `duplicate variable identifier reported as error`() {
        val project = project(
            machines = listOf(
                machine(
                    "M1",
                    variables = listOf(Variable("x", "x"), Variable("x", "x")),
                ),
            ),
        )

        val findings = checker.check(project)

        assertThat(findings)
            .filteredOn { it.ruleId == ValidationRules.DUPLICATE_IDENTIFIER.id && it.element == "x" }
            .singleElement()
            .satisfies({
                assertThat(it.severity).isEqualTo(ValidationSeverity.ERROR)
                assertThat(it.message).contains("Duplicate variable identifier 'x'").contains("M1")
            })
    }

    @Test
    fun `duplicate invariant label reported as error`() {
        val project = project(
            machines = listOf(
                machine(
                    "M1",
                    variables = listOf(Variable("x", "x")),
                    invariants = listOf(
                        Invariant("inv1", "x ∈ ℤ", false),
                        Invariant("inv1", "x ≥ 0", false),
                    ),
                ),
            ),
        )

        val findings = checker.check(project)

        assertThat(findings)
            .filteredOn { it.ruleId == ValidationRules.DUPLICATE_LABEL.id && it.element == "inv1" }
            .singleElement()
            .satisfies({ assertThat(it.severity).isEqualTo(ValidationSeverity.ERROR) })
    }

    @Test
    fun `duplicate event label reported`() {
        val project = project(
            machines = listOf(
                machine(
                    "M1",
                    events = listOf(event("evt"), event("evt")),
                ),
            ),
        )

        val findings = checker.check(project)

        assertThat(findings)
            .filteredOn { it.ruleId == ValidationRules.DUPLICATE_LABEL.id && it.element == "evt" }
            .singleElement()
    }

    @Test
    fun `duplicate guard action and parameter within an event reported`() {
        val project = project(
            machines = listOf(
                machine(
                    "M1",
                    variables = listOf(Variable("x", "x")),
                    events = listOf(
                        event(
                            "evt",
                            parameters = listOf(Parameter("p", "p"), Parameter("p", "p")),
                            guards = listOf(Guard("grd1", "p ∈ ℤ", false), Guard("grd1", "p ≥ 0", false)),
                            actions = listOf(Action("act1", "x ≔ p"), Action("act1", "x ≔ 0")),
                        ),
                    ),
                ),
            ),
        )

        val findings = checker.check(project)

        assertThat(findings.filteredOnElement("p")).singleElement()
            .satisfies({ assertThat(it.ruleId).isEqualTo(ValidationRules.DUPLICATE_IDENTIFIER.id) })
        assertThat(findings.filteredOnElement("grd1")).singleElement()
            .satisfies({ assertThat(it.ruleId).isEqualTo(ValidationRules.DUPLICATE_LABEL.id) })
        assertThat(findings.filteredOnElement("act1")).singleElement()
            .satisfies({ assertThat(it.ruleId).isEqualTo(ValidationRules.DUPLICATE_LABEL.id) })
    }

    @Test
    fun `guard and action sharing a label within an event reported`() {
        val project = project(
            machines = listOf(
                machine(
                    "M1",
                    variables = listOf(Variable("x", "x")),
                    events = listOf(
                        event(
                            "evt",
                            guards = listOf(Guard("lbl", "x ∈ ℤ", false)),
                            actions = listOf(Action("lbl", "x ≔ 0")),
                        ),
                    ),
                ),
            ),
        )

        val findings = checker.check(project)

        assertThat(findings)
            .filteredOn { it.ruleId == ValidationRules.DUPLICATE_LABEL.id && it.element == "lbl" }
            .singleElement()
            .satisfies({ assertThat(it.message).contains("guard or action label") })
    }

    @Test
    fun `carrier set and constant sharing a name reported once`() {
        val project = project(
            contexts = listOf(
                context(
                    "C1",
                    carrierSets = listOf(CarrierSet("S", "S")),
                    constants = listOf(Constant("S", "S")),
                ),
            ),
        )

        val findings = checker.check(project)

        assertThat(findings)
            .filteredOn { it.ruleId == ValidationRules.DUPLICATE_IDENTIFIER.id && it.element == "S" }
            .singleElement()
            .satisfies({ assertThat(it.message).contains("carrier set or constant") })
    }

    @Test
    fun `duplicate axiom label reported`() {
        val project = project(
            contexts = listOf(
                context(
                    "C1",
                    axioms = listOf(Axiom("axm1", "1 = 1", false), Axiom("axm1", "2 = 2", false)),
                ),
            ),
        )

        val findings = checker.check(project)

        assertThat(findings)
            .filteredOn { it.ruleId == ValidationRules.DUPLICATE_LABEL.id && it.element == "axm1" }
            .singleElement()
    }

    @Test
    fun `clean model produces no duplicate findings`() {
        val project = project(
            machines = listOf(
                machine(
                    "M1",
                    variables = listOf(Variable("x", "x"), Variable("y", "y")),
                    invariants = listOf(Invariant("inv1", "x ∈ ℤ", false), Invariant("inv2", "y ∈ ℤ", false)),
                    events = listOf(
                        event("INITIALISATION", actions = listOf(Action("act1", "x ≔ 0"), Action("act2", "y ≔ 0"))),
                    ),
                ),
            ),
        )

        val findings = checker.check(project)

        assertThat(findings).isEmpty()
    }

    @Test
    fun `identifier and label in separate namespaces do not conflict`() {
        val project = project(
            machines = listOf(
                machine(
                    "M1",
                    variables = listOf(Variable("x", "x")),
                    invariants = listOf(Invariant("x", "x ∈ ℤ", false)),
                ),
            ),
        )

        val findings = checker.check(project)

        assertThat(findings).isEmpty()
    }

    @Test
    fun `extended event local clauses conflict with inherited guard and action labels`() {
        val project = project(
            machines = listOf(
                machine(
                    "Base",
                    events = listOf(
                        event(
                            "evt",
                            guards = listOf(Guard("grd1", "x = x", false)),
                            actions = listOf(Action("act1", "x ≔ x")),
                        ),
                    ),
                ),
                machine(
                    "Refined",
                    refinesMachine = "Base",
                    events = listOf(
                        event(
                            "evt",
                            extended = true,
                            refinesEvents = listOf("evt"),
                            guards = listOf(Guard("act1", "x = x", false)),
                            actions = listOf(Action("grd1", "x ≔ x")),
                        ),
                    ),
                ),
            ),
        )

        val findings = checker.check(project).filter { it.ruleId == ValidationRules.DUPLICATE_LABEL.id }

        assertThat(findings).hasSize(2)
        assertThat(findings).anySatisfy {
            assertThat(it.element).isEqualTo("evt/act1")
            assertThat(it.message).contains("Guard label 'act1'").contains("inherited action label")
        }
        assertThat(findings).anySatisfy {
            assertThat(it.element).isEqualTo("evt/grd1")
            assertThat(it.message).contains("Action label 'grd1'").contains("inherited guard label")
        }
    }

    @Test
    fun `inherited label conflict follows a transitive extended chain`() {
        val project = project(
            machines = listOf(
                machine(
                    "Root",
                    events = listOf(event("evt", guards = listOf(Guard("root", "x = x", false)))),
                ),
                machine(
                    "Middle",
                    refinesMachine = "Root",
                    events = listOf(event("evt", extended = true, refinesEvents = listOf("evt"))),
                ),
                machine(
                    "Leaf",
                    refinesMachine = "Middle",
                    events = listOf(
                        event(
                            "evt",
                            extended = true,
                            refinesEvents = listOf("evt"),
                            guards = listOf(Guard("root", "x = x", false)),
                        ),
                    ),
                ),
            ),
        )

        val findings = checker.check(project)

        assertThat(findings)
            .filteredOn { it.ruleId == ValidationRules.DUPLICATE_LABEL.id && it.element == "evt/root" }
            .singleElement()
    }

    @Test
    fun `extended initialisation checks inherited action labels`() {
        val project = project(
            machines = listOf(
                machine(
                    "Base",
                    events = listOf(event(INITIALISATION, actions = listOf(Action("init1", "x ≔ 0")))),
                ),
                machine(
                    "Refined",
                    refinesMachine = "Base",
                    events = listOf(
                        event(
                            INITIALISATION,
                            extended = true,
                            refinesEvents = listOf(INITIALISATION),
                            actions = listOf(Action("init1", "y ≔ 0")),
                        ),
                    ),
                ),
            ),
        )

        val findings = checker.check(project)

        assertThat(findings)
            .filteredOn { it.ruleId == ValidationRules.DUPLICATE_LABEL.id && it.element == "$INITIALISATION/init1" }
            .singleElement()
    }

    @Test
    fun `non-extended refinement may reuse an abstract clause label`() {
        val project = project(
            machines = listOf(
                machine(
                    "Base",
                    events = listOf(event("evt", guards = listOf(Guard("same", "x = x", false)))),
                ),
                machine(
                    "Refined",
                    refinesMachine = "Base",
                    events = listOf(
                        event(
                            "evt",
                            refinesEvents = listOf("evt"),
                            guards = listOf(Guard("same", "x = x", false)),
                        ),
                    ),
                ),
            ),
        )

        assertThat(checker.check(project)).isEmpty()
    }

    @Test
    fun `locally repeated inherited label is reported only once`() {
        val project = project(
            machines = listOf(
                machine(
                    "Base",
                    events = listOf(event("evt", guards = listOf(Guard("same", "x = x", false)))),
                ),
                machine(
                    "Refined",
                    refinesMachine = "Base",
                    events = listOf(
                        event(
                            "evt",
                            extended = true,
                            refinesEvents = listOf("evt"),
                            guards = listOf(
                                Guard("same", "x = x", false),
                                Guard("same", "x = x", false),
                            ),
                        ),
                    ),
                ),
            ),
        )

        val findings = checker.check(project).filter { it.ruleId == ValidationRules.DUPLICATE_LABEL.id }

        assertThat(findings).singleElement()
        assertThat(findings.single().element).isEqualTo("same")
    }

    @Test
    fun `circular refinement does not treat a local clause label as inherited from itself`() {
        val project = project(
            machines = listOf(
                machine(
                    "M1",
                    refinesMachine = "M2",
                    events = listOf(
                        event(
                            "evt",
                            extended = true,
                            refinesEvents = listOf("evt"),
                            guards = listOf(Guard("own", "1 = 1", false)),
                        ),
                    ),
                ),
                machine(
                    "M2",
                    refinesMachine = "M1",
                    events = listOf(event("evt", extended = true, refinesEvents = listOf("evt"))),
                ),
            ),
        )

        assertThat(checker.check(project)).noneMatch {
            it.ruleId == ValidationRules.DUPLICATE_LABEL.id && it.element == "evt/own"
        }
    }

    private fun List<ValidationError>.filteredOnElement(element: String): List<ValidationError> = filter { it.element == element }
}
