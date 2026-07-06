package com.eventb.checker.validation

import com.eventb.checker.TestModelBuilders.event
import com.eventb.checker.TestModelBuilders.machine
import com.eventb.checker.TestModelBuilders.project
import com.eventb.checker.model.Action
import com.eventb.checker.model.Guard
import com.eventb.checker.model.Invariant
import com.eventb.checker.model.Parameter
import com.eventb.checker.model.Witness
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class MachineInheritanceResolverTest {

    private val resolver = MachineInheritanceResolver()

    private fun guard(predicate: String) = Guard("grd1", predicate, false)

    @Test
    fun `extended event inherits abstract guard and action identifiers`() {
        val project = project(
            machines = listOf(
                machine(
                    "M0",
                    events = listOf(
                        event("op", guards = listOf(guard("v < 10")), actions = listOf(Action("act1", "v ≔ v + 1"))),
                    ),
                ),
                machine("M1", refinesMachine = "M0", events = listOf(event("op", extended = true))),
            ),
        )

        val inheritance = resolver.resolve(project)

        assertThat(inheritance.getValue("M1").inheritedReferences).contains("v")
        assertThat(inheritance.getValue("M1").inheritedEventAssignments).containsExactly("v")
        assertThat(inheritance.getValue("M0").inheritedReferences).isEmpty()
        assertThat(inheritance.getValue("M0").inheritedEventAssignments).isEmpty()
    }

    @Test
    fun `explicit refines target resolves renamed extended event`() {
        val project = project(
            machines = listOf(
                machine("M0", events = listOf(event("foo", guards = listOf(guard("v > 0"))))),
                machine("M1", refinesMachine = "M0", events = listOf(event("bar", extended = true, refinesEvents = listOf("foo")))),
            ),
        )

        assertThat(resolver.resolve(project).getValue("M1").inheritedReferences).contains("v")
    }

    @Test
    fun `multi level chain collects every extended ancestor's clauses`() {
        val project = project(
            machines = listOf(
                machine("M0", events = listOf(event("op", guards = listOf(guard("v0 > 0"))))),
                machine(
                    "M1",
                    refinesMachine = "M0",
                    events = listOf(event("op", extended = true, guards = listOf(guard("v1 > 0")))),
                ),
                machine("M2", refinesMachine = "M1", events = listOf(event("op", extended = true))),
            ),
        )

        assertThat(resolver.resolve(project).getValue("M2").inheritedReferences).contains("v0", "v1")
    }

    @Test
    fun `chain stops after first non extended ancestor`() {
        val project = project(
            machines = listOf(
                machine("M0", events = listOf(event("op", guards = listOf(guard("a > 0"))))),
                machine(
                    "M1",
                    refinesMachine = "M0",
                    events = listOf(event("op", refinesEvents = listOf("op"), guards = listOf(guard("b > 0")))),
                ),
                machine("M2", refinesMachine = "M1", events = listOf(event("op", extended = true))),
            ),
        )

        val inherited = resolver.resolve(project).getValue("M2").inheritedReferences

        assertThat(inherited).contains("b")
        assertThat(inherited).doesNotContain("a")
    }

    @Test
    fun `inherited parameter does not become a machine level reference`() {
        val project = project(
            machines = listOf(
                machine(
                    "M0",
                    events = listOf(
                        event("op", parameters = listOf(Parameter("p", "p")), guards = listOf(guard("p < limit"))),
                    ),
                ),
                machine("M1", refinesMachine = "M0", events = listOf(event("op", extended = true))),
            ),
        )

        val inherited = resolver.resolve(project).getValue("M1").inheritedReferences

        assertThat(inherited).contains("limit")
        assertThat(inherited).doesNotContain("p")
    }

    @Test
    fun `parameter declared at the root is subtracted from mid chain clauses`() {
        val project = project(
            machines = listOf(
                machine(
                    "M0",
                    events = listOf(event("op", parameters = listOf(Parameter("p", "p")), guards = listOf(guard("p ∈ ℤ")))),
                ),
                machine(
                    "M1",
                    refinesMachine = "M0",
                    events = listOf(event("op", extended = true, guards = listOf(guard("p < v1")))),
                ),
                machine("M2", refinesMachine = "M1", events = listOf(event("op", extended = true))),
            ),
        )

        val inherited = resolver.resolve(project).getValue("M2").inheritedReferences

        assertThat(inherited).contains("v1")
        assertThat(inherited).doesNotContain("p")
    }

    @Test
    fun `non extended event contributes nothing even with a refines target`() {
        val project = project(
            machines = listOf(
                machine("M0", events = listOf(event("op", guards = listOf(guard("v > 0"))))),
                machine("M1", refinesMachine = "M0", events = listOf(event("op", refinesEvents = listOf("op")))),
            ),
        )

        assertThat(resolver.resolve(project).getValue("M1").inheritedReferences).isEmpty()
    }

    @Test
    fun `witnesses are not collected`() {
        val project = project(
            machines = listOf(
                machine("M0", events = listOf(event("op", witnesses = listOf(Witness("q", "q = wonly"))))),
                machine("M1", refinesMachine = "M0", events = listOf(event("op", extended = true))),
            ),
        )

        assertThat(resolver.resolve(project).getValue("M1").inheritedReferences).doesNotContain("wonly")
    }

    @Test
    fun `refines cycle terminates and still collects resolvable clauses`() {
        val project = project(
            machines = listOf(
                machine(
                    "M1",
                    refinesMachine = "M2",
                    events = listOf(event("op", extended = true, guards = listOf(guard("g1 > 0")))),
                ),
                machine(
                    "M2",
                    refinesMachine = "M1",
                    events = listOf(event("op", extended = true, guards = listOf(guard("g2 > 0")))),
                ),
            ),
        )

        val inheritance = resolver.resolve(project)

        assertThat(inheritance.getValue("M1").inheritedReferences).contains("g2")
        assertThat(inheritance.getValue("M2").inheritedReferences).contains("g1")
    }

    @Test
    fun `missing parent machine yields no contributions`() {
        val project = project(
            machines = listOf(
                machine("M1", refinesMachine = "Absent", events = listOf(event("op", extended = true))),
            ),
        )

        assertThat(resolver.resolve(project).getValue("M1").inheritedReferences).isEmpty()
    }

    @Test
    fun `ancestor invariants are inherited across the whole chain`() {
        // Non-typing invariants (`a = 0`, not `a ∈ ℤ`): typing-shaped conjuncts are excluded, so only
        // real references propagate down the chain.
        val project = project(
            machines = listOf(
                machine("M0", invariants = listOf(Invariant("inv1", "a = 0", false))),
                machine("M1", refinesMachine = "M0", invariants = listOf(Invariant("inv1", "b = 0", false))),
                machine("M2", refinesMachine = "M1", invariants = listOf(Invariant("inv1", "c = 0", false))),
            ),
        )

        val inheritance = resolver.resolve(project)

        assertThat(inheritance.getValue("M2").inheritedReferences).contains("a", "b")
        assertThat(inheritance.getValue("M2").inheritedReferences).doesNotContain("c")
        assertThat(inheritance.getValue("M1").inheritedReferences).contains("a")
        assertThat(inheritance.getValue("M0").inheritedReferences).isEmpty()
    }

    @Test
    fun `multi level extended initialisation chain unions assignments`() {
        val project = project(
            machines = listOf(
                machine("M0", events = listOf(event("INITIALISATION", actions = listOf(Action("act1", "v ≔ 0"))))),
                machine(
                    "M1",
                    refinesMachine = "M0",
                    events = listOf(event("INITIALISATION", extended = true, actions = listOf(Action("act2", "w ≔ 0")))),
                ),
                machine("M2", refinesMachine = "M1", events = listOf(event("INITIALISATION", extended = true))),
            ),
        )

        val inheritance = resolver.resolve(project)

        assertThat(inheritance.getValue("M2").initAssignedIdentifiers).containsExactlyInAnyOrder("v", "w")
        assertThat(inheritance.getValue("M1").initAssignedIdentifiers).containsExactlyInAnyOrder("v", "w")
        assertThat(inheritance.getValue("M0").initAssignedIdentifiers).containsExactly("v")
    }

    @Test
    fun `root machine with extended initialisation resolves to empty`() {
        val project = project(
            machines = listOf(machine("M0", events = listOf(event("INITIALISATION", extended = true)))),
        )

        assertThat(resolver.resolve(project).getValue("M0").initAssignedIdentifiers).isEmpty()
    }

    @Test
    fun `missing parent yields unresolved initialisation chain`() {
        val project = project(
            machines = listOf(
                machine("M1", refinesMachine = "Absent", events = listOf(event("INITIALISATION", extended = true))),
            ),
        )

        assertThat(resolver.resolve(project).getValue("M1").initAssignedIdentifiers).isNull()
    }

    @Test
    fun `ancestor without initialisation yields unresolved chain`() {
        val project = project(
            machines = listOf(
                machine("M0"),
                machine("M1", refinesMachine = "M0", events = listOf(event("INITIALISATION", extended = true))),
            ),
        )

        assertThat(resolver.resolve(project).getValue("M1").initAssignedIdentifiers).isNull()
    }

    @Test
    fun `unparseable ancestor initialisation action yields unresolved chain`() {
        val project = project(
            machines = listOf(
                // "v = 0" is a predicate, not an assignment, so what the INIT assigns is unknowable.
                machine("M0", events = listOf(event("INITIALISATION", actions = listOf(Action("act1", "v = 0"))))),
                machine("M1", refinesMachine = "M0", events = listOf(event("INITIALISATION", extended = true))),
            ),
        )

        assertThat(resolver.resolve(project).getValue("M1").initAssignedIdentifiers).isNull()
    }

    @Test
    fun `cycle does not truncate contributions of downstream machines`() {
        // M1 and M2 form a REFINES cycle; M3 refines M1 from outside it. Resolving M1 first walks
        // M1's chain with M2 already on the visiting stack, so nothing cached from that walk may be
        // reused for M3, whose own walk resolves both cycle members' clauses.
        val project = project(
            machines = listOf(
                machine(
                    "M1",
                    refinesMachine = "M2",
                    events = listOf(event("op", extended = true, guards = listOf(guard("g1 > 0")))),
                ),
                machine(
                    "M2",
                    refinesMachine = "M1",
                    events = listOf(event("op", extended = true, guards = listOf(guard("g2 > 0")))),
                ),
                machine("M3", refinesMachine = "M1", events = listOf(event("op", extended = true))),
            ),
        )

        assertThat(resolver.resolve(project).getValue("M3").inheritedReferences).contains("g1", "g2")
    }

    @Test
    fun `refines cycle yields unresolved initialisation chain`() {
        val project = project(
            machines = listOf(
                machine("M1", refinesMachine = "M2", events = listOf(event("INITIALISATION", extended = true))),
                machine("M2", refinesMachine = "M1", events = listOf(event("INITIALISATION", extended = true))),
            ),
        )

        val inheritance = resolver.resolve(project)

        assertThat(inheritance.getValue("M1").initAssignedIdentifiers).isNull()
        assertThat(inheritance.getValue("M2").initAssignedIdentifiers).isNull()
    }
}
