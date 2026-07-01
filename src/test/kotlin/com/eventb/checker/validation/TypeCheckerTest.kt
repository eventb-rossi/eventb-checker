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
import com.eventb.checker.model.Variant
import com.eventb.checker.model.Witness
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class TypeCheckerTest {

    private val typeChecker = TypeChecker()

    @Test
    fun `carrier set registers as type`() {
        val project = project(
            contexts = listOf(
                context(
                    "C1",
                    carrierSets = listOf(CarrierSet("S", "S")),
                    axioms = listOf(Axiom("axm1", "finite(S)", false)),
                ),
            ),
        )

        val errors = typeChecker.checkProject(project)

        assertThat(errors).isEmpty()
    }

    @Test
    fun `constant typed by axiom`() {
        val project = project(
            contexts = listOf(
                context(
                    "C1",
                    carrierSets = listOf(CarrierSet("S", "S")),
                    constants = listOf(Constant("c", "c")),
                    axioms = listOf(
                        Axiom("axm1", "c ∈ S", false),
                        Axiom("axm2", "c = c", false),
                    ),
                ),
            ),
        )

        val errors = typeChecker.checkProject(project)

        assertThat(errors).isEmpty()
    }

    @Test
    fun `variable typed by invariant`() {
        val project = project(
            machines = listOf(
                machine(
                    "M1",
                    variables = listOf(Variable("n", "n")),
                    invariants = listOf(Invariant("inv1", "n ∈ ℤ", false)),
                ),
            ),
        )

        val errors = typeChecker.checkProject(project)

        assertThat(errors).isEmpty()
    }

    @Test
    fun `type mismatch detected`() {
        val project = project(
            machines = listOf(
                machine(
                    "M1",
                    variables = listOf(Variable("n", "n")),
                    invariants = listOf(
                        Invariant("inv1", "n ∈ ℤ", false),
                        Invariant("inv2", "n = TRUE", false),
                    ),
                ),
            ),
        )

        val errors = typeChecker.checkProject(project)

        assertThat(errors).allSatisfy { assertThat(it.severity).isEqualTo(ValidationSeverity.ERROR) }
        assertThat(errors).filteredOn { it.message.contains("Type error") }.isNotEmpty
        assertThat(errors).allSatisfy { assertThat(it.ruleId).isEqualTo(ValidationRules.TYPE_ERROR.id) }
    }

    @Test
    fun `maplet on right side of membership is a type error`() {
        // Regression: auction.zip guard `a ∈ AUCTIONS ↦ item` — the right-hand side is a
        // pair ℙ(AUCTIONS)×ℙ(ITEMS), not a set, so the membership cannot be typed.
        val project = project(
            contexts = listOf(
                context(
                    "C1",
                    carrierSets = listOf(CarrierSet("AUCTIONS", "AUCTIONS"), CarrierSet("ITEMS", "ITEMS")),
                ),
            ),
            machines = listOf(
                machine(
                    "M1",
                    seesContexts = listOf("C1"),
                    variables = listOf(Variable("item", "item")),
                    invariants = listOf(Invariant("inv1", "item ⊆ ITEMS", false)),
                    events = listOf(
                        event(
                            "CreateAuction",
                            parameters = listOf(Parameter("a", "a")),
                            guards = listOf(Guard("grd1", "a ∈ AUCTIONS ↦ item", false)),
                        ),
                    ),
                ),
            ),
        )

        val errors = typeChecker.checkProject(project)

        val typeErrors = errors.filter { it.ruleId == ValidationRules.TYPE_ERROR.id }
        assertThat(typeErrors).isNotEmpty
        assertThat(typeErrors).allSatisfy { assertThat(it.severity).isEqualTo(ValidationSeverity.ERROR) }
        assertThat(typeErrors.map { it.element }).contains("CreateAuction/grd1")
    }

    @Test
    fun `unresolved identifier type stays a warning`() {
        val project = project(
            machines = listOf(
                machine(
                    "M1",
                    variables = listOf(Variable("n", "n")),
                    invariants = listOf(Invariant("inv1", "n = n", false)),
                ),
            ),
        )

        val errors = typeChecker.checkProject(project)

        assertThat(errors).isNotEmpty
        assertThat(errors).allSatisfy {
            assertThat(it.severity).isEqualTo(ValidationSeverity.WARNING)
            assertThat(it.ruleId).isEqualTo(ValidationRules.UNKNOWN_TYPE.id)
        }
    }

    @Test
    fun `context env flows to machine via SEES`() {
        val project = project(
            contexts = listOf(
                context(
                    "C1",
                    carrierSets = listOf(CarrierSet("COLOR", "COLOR")),
                    constants = listOf(Constant("red", "red")),
                    axioms = listOf(Axiom("axm1", "red ∈ COLOR", false)),
                ),
            ),
            machines = listOf(
                machine(
                    "M1",
                    seesContexts = listOf("C1"),
                    variables = listOf(Variable("x", "x")),
                    invariants = listOf(Invariant("inv1", "x ∈ COLOR", false)),
                    events = listOf(
                        event(
                            "set_color",
                            actions = listOf(Action("act1", "x ≔ red")),
                        ),
                    ),
                ),
            ),
        )

        val errors = typeChecker.checkProject(project)

        assertThat(errors).isEmpty()
    }

    @Test
    fun `event parameter typed by guard`() {
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

        val errors = typeChecker.checkProject(project)

        assertThat(errors).isEmpty()
    }

    @Test
    fun `undeclared identifier produces error`() {
        val project = project(
            machines = listOf(
                machine(
                    "M1",
                    invariants = listOf(Invariant("inv1", "x = y", false)),
                ),
            ),
        )

        val errors = typeChecker.checkProject(project)

        assertThat(errors)
            .filteredOn { it.severity == ValidationSeverity.ERROR && it.ruleId == ValidationRules.UNDECLARED_IDENTIFIER.id }
            .hasSize(2)
        assertThat(errors.map { it.message }).anyMatch { it.contains("'x'") }
        assertThat(errors.map { it.message }).anyMatch { it.contains("'y'") }
    }

    @Test
    fun `undeclared assignment target produces error`() {
        val project = project(
            machines = listOf(
                machine(
                    "M1",
                    events = listOf(
                        event(
                            "update",
                            actions = listOf(Action("act1", "x ≔ 0")),
                        ),
                    ),
                ),
            ),
        )

        val errors = typeChecker.checkProject(project)

        assertThat(errors).anyMatch {
            it.severity == ValidationSeverity.ERROR &&
                it.ruleId == ValidationRules.UNDECLARED_IDENTIFIER.id &&
                it.message.contains("'x'")
        }
    }

    @Test
    fun `refinement chain inherits types`() {
        val project = project(
            machines = listOf(
                machine(
                    "Base",
                    variables = listOf(Variable("x", "x")),
                    invariants = listOf(Invariant("inv1", "x ∈ ℤ", false)),
                ),
                machine(
                    "Refined",
                    refinesMachine = "Base",
                    variables = listOf(Variable("y", "y")),
                    invariants = listOf(
                        Invariant("inv1", "y ∈ ℤ", false),
                        Invariant("inv2", "y ≥ x", false),
                    ),
                ),
            ),
        )

        val errors = typeChecker.checkProject(project)

        assertThat(errors).isEmpty()
    }

    @Test
    fun `refinement inherits seen context declarations`() {
        val project = project(
            contexts = listOf(
                context(
                    "C",
                    carrierSets = listOf(CarrierSet("S", "S")),
                    constants = listOf(Constant("c", "c")),
                    axioms = listOf(Axiom("axm1", "c ∈ S", false)),
                ),
            ),
            machines = listOf(
                machine(
                    "Base",
                    seesContexts = listOf("C"),
                    variables = listOf(Variable("x", "x")),
                    invariants = listOf(Invariant("inv1", "x ∈ S ∧ c ∈ S", false)),
                ),
                machine(
                    "Refined",
                    refinesMachine = "Base",
                    variables = listOf(Variable("y", "y")),
                    invariants = listOf(
                        Invariant("inv1", "y ∈ S", false),
                        Invariant("inv2", "c ∈ S", false),
                    ),
                ),
            ),
        )

        val errors = typeChecker.checkProject(project)

        assertThat(errors).isEmpty()
    }

    @Test
    fun `extended event inherits refined event parameters`() {
        val project = project(
            machines = listOf(
                machine(
                    "Base",
                    variables = listOf(Variable("bytes", "bytes")),
                    invariants = listOf(Invariant("inv1", "bytes ∈ ℕ ⇸ ℕ", false)),
                    events = listOf(
                        event(
                            "split",
                            parameters = listOf(Parameter("left_size", "left_size")),
                            guards = listOf(Guard("grd1", "left_size ∈ ℕ1", false)),
                        ),
                    ),
                ),
                machine(
                    "Refined",
                    refinesMachine = "Base",
                    variables = listOf(Variable("bytes", "bytes")),
                    invariants = listOf(Invariant("inv1", "bytes ∈ ℕ ⇸ ℕ", false)),
                    events = listOf(
                        event(
                            "split",
                            extended = true,
                            refinesEvents = listOf("split"),
                            guards = listOf(Guard("grd2", "left_size = bytes(1)", false)),
                        ),
                    ),
                ),
            ),
        )

        val errors = typeChecker.checkProject(project)

        assertThat(errors).isEmpty()
    }

    @Test
    fun `extended event falls back to same-label refined event`() {
        val project = project(
            machines = listOf(
                machine(
                    "Base",
                    variables = listOf(Variable("x", "x")),
                    invariants = listOf(Invariant("inv1", "x ∈ ℕ", false)),
                    events = listOf(
                        event(
                            "update",
                            parameters = listOf(Parameter("p", "p")),
                            guards = listOf(Guard("grd1", "p ∈ ℕ", false)),
                        ),
                    ),
                ),
                machine(
                    "Refined",
                    refinesMachine = "Base",
                    variables = listOf(Variable("x", "x")),
                    invariants = listOf(Invariant("inv1", "x ∈ ℕ", false)),
                    events = listOf(
                        event(
                            "update",
                            extended = true,
                            guards = listOf(Guard("grd2", "p ≥ x", false)),
                        ),
                    ),
                ),
            ),
        )

        val errors = typeChecker.checkProject(project)

        assertThat(errors).isEmpty()
    }

    @Test
    fun `guard cannot reference abstract-only variable`() {
        val project = project(
            machines = listOf(
                machine(
                    "Base",
                    variables = listOf(Variable("x", "x")),
                    invariants = listOf(Invariant("inv1", "x ∈ ℕ", false)),
                ),
                machine(
                    "Refined",
                    refinesMachine = "Base",
                    variables = listOf(Variable("y", "y")),
                    invariants = listOf(Invariant("inv1", "y ∈ ℕ", false)),
                    events = listOf(
                        event(
                            "bad",
                            guards = listOf(Guard("grd1", "x = y", false)),
                        ),
                    ),
                ),
            ),
        )

        val errors = typeChecker.checkProject(project)

        // A variable declared only in the abstract machine has disappeared in this refinement, so it
        // is reported precisely as EB025 rather than a generic undeclared identifier (EB018).
        assertThat(errors).anyMatch {
            it.severity == ValidationSeverity.ERROR &&
                it.ruleId == ValidationRules.DISAPPEARED_VARIABLE.id &&
                it.element == "bad/grd1" &&
                it.message.contains("'x'")
        }
        assertThat(errors).noneMatch { it.ruleId == ValidationRules.UNDECLARED_IDENTIFIER.id }
    }

    @Test
    fun `variant cannot reference abstract-only variable`() {
        val project = project(
            machines = listOf(
                machine(
                    "Base",
                    variables = listOf(Variable("x", "x")),
                    invariants = listOf(Invariant("inv1", "x ∈ ℕ", false)),
                ),
                machine(
                    "Refined",
                    refinesMachine = "Base",
                    variables = listOf(Variable("y", "y")),
                    invariants = listOf(Invariant("inv1", "y ∈ ℕ", false)),
                    variant = Variant("vrn1", "x"),
                ),
            ),
        )

        val errors = typeChecker.checkProject(project)

        assertThat(errors).anyMatch {
            it.severity == ValidationSeverity.ERROR &&
                it.ruleId == ValidationRules.UNDECLARED_IDENTIFIER.id &&
                it.element == "vrn1" &&
                it.message.contains("'x'")
        }
    }

    @Test
    fun `action cannot assign abstract-only variable`() {
        val project = project(
            machines = listOf(
                machine(
                    "Base",
                    variables = listOf(Variable("x", "x")),
                    invariants = listOf(Invariant("inv1", "x ∈ ℕ", false)),
                ),
                machine(
                    "Refined",
                    refinesMachine = "Base",
                    variables = listOf(Variable("y", "y")),
                    invariants = listOf(Invariant("inv1", "y ∈ ℕ", false)),
                    events = listOf(
                        event(
                            "bad",
                            actions = listOf(Action("act1", "x ≔ y")),
                        ),
                    ),
                ),
            ),
        )

        val errors = typeChecker.checkProject(project)

        // Assigning a variable that disappeared in this refinement is reported as EB025, not EB018.
        assertThat(errors).anyMatch {
            it.severity == ValidationSeverity.ERROR &&
                it.ruleId == ValidationRules.DISAPPEARED_VARIABLE.id &&
                it.element == "bad/act1" &&
                it.message.contains("'x'")
        }
        assertThat(errors).noneMatch { it.ruleId == ValidationRules.UNDECLARED_IDENTIFIER.id }
    }

    @Test
    fun `context extension inherits carrier sets`() {
        val project = project(
            contexts = listOf(
                context(
                    "Base",
                    carrierSets = listOf(CarrierSet("S", "S")),
                ),
                context(
                    "Ext",
                    extendsContexts = listOf("Base"),
                    constants = listOf(Constant("c", "c")),
                    axioms = listOf(Axiom("axm1", "c ∈ S", false)),
                ),
            ),
        )

        val errors = typeChecker.checkProject(project)

        assertThat(errors).isEmpty()
    }

    @Test
    fun `empty project produces no errors`() {
        val project = project()

        val errors = typeChecker.checkProject(project)

        assertThat(errors).isEmpty()
    }

    @Test
    fun `undeclared identifiers do not suppress inferred types for later formulas`() {
        val project = project(
            machines = listOf(
                machine(
                    "M1",
                    variables = listOf(Variable("v", "v")),
                    invariants = listOf(Invariant("inv1", "v ∈ ℕ ∧ y = 0", false)),
                    variant = Variant("vrn1", "v"),
                ),
            ),
        )

        val errors = typeChecker.checkProject(project)

        assertThat(errors)
            .singleElement()
            .satisfies({ error ->
                assertThat(error.severity).isEqualTo(ValidationSeverity.ERROR)
                assertThat(error.ruleId).isEqualTo(ValidationRules.UNDECLARED_IDENTIFIER.id)
                assertThat(error.message).contains("'y'")
            })
    }

    @Test
    fun `variant expression type checked`() {
        val project = project(
            machines = listOf(
                machine(
                    "M1",
                    variables = listOf(Variable("n", "n")),
                    invariants = listOf(Invariant("inv1", "n ∈ ℤ", false)),
                    variant = Variant("vrn1", "n"),
                ),
            ),
        )

        val errors = typeChecker.checkProject(project)

        assertThat(errors).isEmpty()
    }

    @Test
    fun `witness type checked in event`() {
        val project = project(
            machines = listOf(
                machine(
                    "M1",
                    variables = listOf(Variable("x", "x")),
                    invariants = listOf(Invariant("inv1", "x ∈ ℤ", false)),
                    events = listOf(
                        event(
                            "evt",
                            witnesses = listOf(Witness("wit1", "x' = x + 1")),
                        ),
                    ),
                ),
            ),
        )

        val errors = typeChecker.checkProject(project)

        assertThat(errors).isEmpty()
    }

    @Test
    fun `witness can reference abstract parameter named by label`() {
        val project = project(
            machines = listOf(
                machine(
                    "Base",
                    variables = listOf(Variable("x", "x")),
                    invariants = listOf(Invariant("inv1", "x ∈ ℤ", false)),
                    events = listOf(
                        event(
                            "evt",
                            parameters = listOf(Parameter("p", "p")),
                            guards = listOf(Guard("grd1", "p ∈ ℤ", false)),
                        ),
                    ),
                ),
                machine(
                    "Refined",
                    refinesMachine = "Base",
                    variables = listOf(Variable("x", "x")),
                    invariants = listOf(Invariant("inv1", "x ∈ ℤ", false)),
                    events = listOf(
                        event(
                            "evt",
                            refinesEvents = listOf("evt"),
                            witnesses = listOf(Witness("p", "p = x + 1")),
                        ),
                    ),
                ),
            ),
        )

        val errors = typeChecker.checkProject(project)

        assertThat(errors).isEmpty()
    }

    @Test
    fun `witness cannot introduce arbitrary label identifier`() {
        val project = project(
            machines = listOf(
                machine(
                    "M1",
                    variables = listOf(Variable("x", "x")),
                    invariants = listOf(Invariant("inv1", "x ∈ ℤ", false)),
                    events = listOf(
                        event(
                            "evt",
                            witnesses = listOf(Witness("p", "p = x + 1")),
                        ),
                    ),
                ),
            ),
        )

        val errors = typeChecker.checkProject(project)

        assertThat(errors).anyMatch {
            it.severity == ValidationSeverity.ERROR &&
                it.ruleId == ValidationRules.UNDECLARED_IDENTIFIER.id &&
                it.element == "evt/p" &&
                it.message.contains("'p'")
        }
    }

    @Test
    fun `retained variable in refinement is not a disappeared variable`() {
        val project = project(
            machines = listOf(
                machine(
                    "Base",
                    variables = listOf(Variable("x", "x")),
                    invariants = listOf(Invariant("inv1", "x ∈ ℤ", false)),
                ),
                machine(
                    "Refined",
                    refinesMachine = "Base",
                    variables = listOf(Variable("x", "x")),
                    invariants = listOf(Invariant("inv1", "x ∈ ℤ", false)),
                    events = listOf(
                        event("evt", guards = listOf(Guard("grd1", "x > 0", false))),
                    ),
                ),
            ),
        )

        val errors = typeChecker.checkProject(project)

        assertThat(errors).noneMatch { it.ruleId == ValidationRules.DISAPPEARED_VARIABLE.id }
    }

    @Test
    fun `reading a disappeared variable on an action right-hand side is flagged`() {
        val project = project(
            machines = listOf(
                machine(
                    "Base",
                    variables = listOf(Variable("x", "x"), Variable("y", "y")),
                    invariants = listOf(Invariant("inv1", "x ∈ ℤ ∧ y ∈ ℤ", false)),
                ),
                machine(
                    "Refined",
                    refinesMachine = "Base",
                    variables = listOf(Variable("y", "y")),
                    invariants = listOf(Invariant("inv1", "y ∈ ℤ", false)),
                    events = listOf(
                        event("evt", actions = listOf(Action("act1", "y ≔ x"))),
                    ),
                ),
            ),
        )

        val errors = typeChecker.checkProject(project)

        assertThat(errors).anyMatch {
            it.severity == ValidationSeverity.ERROR &&
                it.ruleId == ValidationRules.DISAPPEARED_VARIABLE.id &&
                it.element == "evt/act1" &&
                it.message.contains("'x'")
        }
    }
}
