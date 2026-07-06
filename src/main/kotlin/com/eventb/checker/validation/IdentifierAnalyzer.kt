package com.eventb.checker.validation

import com.eventb.checker.model.EventBProject
import com.eventb.checker.model.Machine
import com.eventb.checker.model.Variable

/**
 * The variable-usage lints EB011 (dead variable) and EB012 (unmodified variable). Each machine
 * variable draws **at most one** of the two, chosen so that acting on the finding yields a correct
 * model — mirroring rossi's `lint_dead_variable` / `lint_unmodified_variable`:
 *
 * - **EB011 (dead)** — nothing references it outside typing-shaped invariants (`v ∈ E` / `v ⊆ E`)
 *   *and* no event assigns it, so it serves no purpose. A write-only variable (assigned by an event
 *   but never read) is exempt: it is an output, and deleting it would break the events that write it.
 * - **EB012 (unmodified)** — it is assigned by INITIALISATION, never modified by any event, and
 *   referenced somewhere real: a constant in disguise. The initial value could become an axiom on a
 *   CONSTANT.
 *
 * References, event-assignments, and INITIALISATION writes are read from the machine as Event-B
 * materializes it, and are unioned across the **whole refinement chain**: [MachineInheritance] supplies
 * the clauses inherited from `extended` events and ancestor invariants (upward) *and* the own-text
 * usage of every REFINES descendant (downward). An assignment's right-hand side counts as a reference;
 * its left-hand side does not. Typing-shaped invariant conjuncts do not count (every variable needs one
 * just to be typed).
 *
 * Each variable is judged exactly **once, at the machine that declares it** — a variable a refinement
 * re-lists (inherited from an ancestor) is filtered out here and judged at that ancestor, so a variable
 * declared/typed in an abstract machine but used or modified only in a refinement is not falsely
 * flagged, and a variable retained through a chain is reported once rather than at every level.
 */
class IdentifierAnalyzer {

    fun analyze(project: EventBProject, inheritance: Map<String, MachineInheritance>): List<ValidationError> {
        val findings = mutableListOf<ValidationError>()

        for (machine in project.machines) {
            val inherited = inheritance.getValue(machine.name)
            // A null init set means an unresolvable extended-INITIALISATION chain (missing or cyclic
            // parent, ancestor without an INITIALISATION): we cannot tell what the machine initialises,
            // so judging a variable dead or unmodified would be a guess. The broken chain is already an
            // error (EB008/EB009); stay silent on the advisory lints here.
            val initAssigned = inherited.initAssignedIdentifiers ?: continue

            // A variable declared here is judged against every clause that could reference or assign it
            // across the refinement chain: this machine and its descendants (chain*, own usage already
            // folded in by the resolver) plus what ancestors materialize into it (inherited*).
            val references = inherited.chainReferences + inherited.inheritedReferences
            val eventAssigned = inherited.chainEventAssignments + inherited.inheritedEventAssignments

            for (variable in machine.variables) {
                val id = variable.identifier
                // A variable declared by an ancestor is judged at that (more abstract) declaring
                // machine, where the whole chain's references and assignments are unioned — not here.
                if (id in inherited.inheritedVariableNames) continue
                when {
                    id !in references && id !in eventAssigned ->
                        findings.add(deadVariable(machine, variable))

                    id in initAssigned && id !in eventAssigned && id in references ->
                        findings.add(unmodifiedVariable(machine, variable))
                }
            }
        }

        return findings
    }

    private fun deadVariable(machine: Machine, variable: Variable) = ValidationError(
        filePath = machine.filePath,
        severity = ValidationSeverity.WARNING,
        message = "Dead variable: '${variable.identifier}' is never used — nothing references it outside " +
            "typing invariants and no event assigns it",
        element = variable.label,
        ruleId = ValidationRules.DEAD_VARIABLE.id,
    )

    private fun unmodifiedVariable(machine: Machine, variable: Variable) = ValidationError(
        filePath = machine.filePath,
        severity = ValidationSeverity.WARNING,
        message = "Unmodified variable: '${variable.identifier}' is assigned by INITIALISATION and never " +
            "modified by any event; consider a CONSTANT with the initialisation as an axiom",
        element = variable.label,
        ruleId = ValidationRules.UNMODIFIED_VARIABLE.id,
    )
}
