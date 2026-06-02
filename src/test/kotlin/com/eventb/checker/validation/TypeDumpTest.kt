package com.eventb.checker.validation

import com.eventb.checker.TestModelBuilders.context
import com.eventb.checker.TestModelBuilders.event
import com.eventb.checker.TestModelBuilders.machine
import com.eventb.checker.TestModelBuilders.project
import com.eventb.checker.model.Axiom
import com.eventb.checker.model.CarrierSet
import com.eventb.checker.model.Constant
import com.eventb.checker.model.Guard
import com.eventb.checker.model.Invariant
import com.eventb.checker.model.Parameter
import com.eventb.checker.model.Variable
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class TypeDumpTest {

    private val typeChecker = TypeChecker()

    @Test
    fun `dumps inferred constant types and omits untyped ones`() {
        val project = project(
            contexts = listOf(
                context(
                    "C1",
                    carrierSets = listOf(CarrierSet("S", "S")),
                    constants = listOf(Constant("c", "c"), Constant("rel", "rel"), Constant("bare", "bare")),
                    // `bare` has no typing axiom, so Rodin leaves it untyped and it is omitted.
                    axioms = listOf(
                        Axiom("axm1", "c ∈ S", false),
                        Axiom("axm2", "rel ∈ S ↔ S", false),
                    ),
                ),
            ),
        )

        val constants = typeChecker.dumpTypes(project).contexts.getValue("C1")

        assertThat(constants["c"]).isEqualTo("S")
        assertThat(constants["rel"]).isEqualTo("ℙ(S×S)")
        assertThat(constants).doesNotContainKey("bare")
    }

    @Test
    fun `dumps machine variable and event parameter types`() {
        val project = project(
            contexts = listOf(context("C1", carrierSets = listOf(CarrierSet("S", "S")))),
            machines = listOf(
                machine(
                    "M1",
                    seesContexts = listOf("C1"),
                    variables = listOf(Variable("v", "v")),
                    invariants = listOf(Invariant("inv1", "v ∈ S", false)),
                    events = listOf(
                        event(
                            "evt",
                            parameters = listOf(Parameter("p", "p")),
                            guards = listOf(Guard("grd1", "p ∈ S", false)),
                        ),
                    ),
                ),
            ),
        )

        val machine = typeChecker.dumpTypes(project).machines.getValue("M1")

        assertThat(machine.variables["v"]).isEqualTo("S")
        assertThat(machine.events.getValue("evt")["p"]).isEqualTo("S")
    }
}
