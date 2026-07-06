package com.eventb.checker.validation

import com.eventb.checker.model.EventBProject
import com.eventb.checker.model.Machine
import com.eventb.checker.model.Variable
import org.eventb.core.ast.FormulaFactory

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
 * materializes it: [MachineInheritance] supplies the clauses inherited from `extended` events and
 * ancestor invariants. An assignment's right-hand side counts as a reference; its left-hand side does
 * not. Typing-shaped invariant conjuncts do not count (every variable needs one just to be typed).
 */
class IdentifierAnalyzer(private val ff: FormulaFactory = FormulaFactory.getDefault()) {

    fun analyze(project: EventBProject, inheritance: Map<String, MachineInheritance>): List<ValidationError> {
        val findings = mutableListOf<ValidationError>()

        for (machine in project.machines) {
            val inherited = inheritance.getValue(machine.name)
            // A null init set means an unresolvable extended-INITIALISATION chain (missing or cyclic
            // parent, ancestor without an INITIALISATION): we cannot tell what the machine initialises,
            // so judging a variable dead or unmodified would be a guess. The broken chain is already an
            // error (EB008/EB009); stay silent on the advisory lints here.
            val initAssigned = inherited.initAssignedIdentifiers ?: continue

            val usage = ownUsage(machine)
            val references = usage.references + inherited.inheritedReferences
            val eventAssigned = usage.eventAssigned + inherited.inheritedEventAssignments

            for (variable in machine.variables) {
                val id = variable.identifier
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

    private data class MachineUsage(val references: Set<String>, val eventAssigned: Set<String>)

    /**
     * The names [machine]'s own clauses reference and the variables its own **non-INITIALISATION**
     * events assign, computed in a single parse of each formula. References come from invariants
     * (typing conjuncts excluded, theorems in full), the variant, and every event's guards, witnesses,
     * and action right-hand sides — minus that event's parameters, which are local; INITIALISATION
     * action right-hand sides count. Event-assignments exclude INITIALISATION: giving a variable its
     * initial value is not modifying it, which is what lets EB012 recognise a constant-in-disguise
     * while EB011 still treats an initialised-but-unused variable as dead.
     */
    private fun ownUsage(machine: Machine): MachineUsage {
        val references = machine.invariantReferenceNames(ff).toMutableSet()
        val eventAssigned = mutableSetOf<String>()

        machine.variant?.let { ff.parseExpressionOrNull(it.expression) }
            ?.freeIdentifiers?.mapTo(references) { it.name }

        for (event in machine.events) {
            val params = event.parameters.mapTo(mutableSetOf()) { it.identifier }
            val isInit = event.label == INITIALISATION
            val eventRefs = mutableSetOf<String>()
            for (guard in event.guards) {
                ff.parsePredicateOrNull(guard.predicate)?.let { eventRefs.addAll(it.referenceNames()) }
            }
            for (witness in event.witnesses) {
                ff.parsePredicateOrNull(witness.predicate)?.let { eventRefs.addAll(it.referenceNames()) }
            }
            for (action in event.actions) {
                val parsed = ff.parseAssignmentOrNull(action.assignment) ?: continue
                eventRefs.addAll(parsed.rhsReferenceNames())
                if (!isInit) eventAssigned.addAll(parsed.assignedNames())
            }
            references.addAll(eventRefs - params)
        }
        return MachineUsage(references, eventAssigned)
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
