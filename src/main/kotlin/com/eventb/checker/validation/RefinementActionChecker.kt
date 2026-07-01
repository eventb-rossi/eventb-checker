package com.eventb.checker.validation

import com.eventb.checker.model.Event
import com.eventb.checker.model.EventBProject
import org.eventb.core.ast.FormulaFactory

/**
 * Reports new events that assign inherited machine state. In Event-B, an event introduced in a
 * refinement that does not refine an abstract event (and is not `extended`) implicitly refines
 * `skip`, so it must not modify any variable inherited from its abstract machine. Assigning such a
 * retained inherited variable is a refinement-soundness error: the change to that variable is not
 * justified by any abstract event.
 *
 * "Inherited" is defined by the *immediate* abstract machine only: Event-B requires every machine
 * to re-list all the variables it retains, so the parent's variable set already is the complete set
 * of inherited state. A variable the parent dropped and this machine re-declares is new, not
 * inherited; a variable this machine drops has disappeared and is handled by
 * [ValidationRules.DISAPPEARED_VARIABLE] (EB025).
 */
class RefinementActionChecker(private val ff: FormulaFactory = FormulaFactory.getDefault()) {

    fun check(project: EventBProject): List<ValidationError> {
        val findings = mutableListOf<ValidationError>()
        val machinesByName = project.machines.associateBy { it.name }

        for (machine in project.machines) {
            val parent = machine.refinesMachine?.let { machinesByName[it] } ?: continue

            val inherited = parent.variables.mapTo(mutableSetOf()) { it.identifier }
            val retainedInherited = machine.variables.map { it.identifier }.toSet().intersect(inherited)
            if (retainedInherited.isEmpty()) continue

            for (event in machine.events.filter { it.isNewEvent() }) {
                for (action in event.actions) {
                    for (name in assignedVariables(action.assignment).filter { it in retainedInherited }) {
                        findings.add(
                            ValidationError(
                                filePath = machine.filePath,
                                severity = ValidationSeverity.ERROR,
                                message = "New event '${event.label}' assigns inherited variable '$name'; a new " +
                                    "event refines skip and must not modify inherited state — refine the abstract " +
                                    "event that changes '$name', or data-refine it",
                                element = "${event.label}/${action.label}",
                                ruleId = ValidationRules.NEW_EVENT_ASSIGNS_INHERITED_VARIABLE.id,
                            ),
                        )
                    }
                }
            }
        }

        return findings
    }

    /**
     * The left-hand-side variable names assigned by [assignment], sorted for deterministic output,
     * or empty if it does not parse as an assignment. Parsing the action string directly (rather
     * than looking it up by "event/action" label) keeps the result unambiguous even when a model
     * has duplicate event or action labels (themselves reported as EB021/EB022).
     */
    private fun assignedVariables(assignment: String): List<String> {
        val result = ff.parseAssignment(assignment, null)
        if (result.hasProblem()) return emptyList()
        return result.parsedAssignment?.assignedIdentifiers?.map { it.name }?.sorted() ?: emptyList()
    }

    /** A new event refines skip: it refines no abstract event, is not extended, and is not INITIALISATION. */
    private fun Event.isNewEvent(): Boolean = label != "INITIALISATION" && refinesEvents.isEmpty() && !extended
}
