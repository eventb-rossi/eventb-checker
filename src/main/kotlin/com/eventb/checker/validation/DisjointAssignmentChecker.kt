package com.eventb.checker.validation

import com.eventb.checker.model.EventBProject
import org.eventb.core.ast.FormulaFactory

/**
 * EB037: the actions of an event must assign disjoint sets of variables. The actions of an event
 * happen simultaneously, so a variable can receive only one new value; Rodin reports
 * `ActionDisjointLHSError` on every action that assigns a variable another action of the same event
 * also assigns (`MachineEventActionModule`), including an action that names a variable twice itself.
 * In an `extended` event the inherited actions take part: an own action that assigns one of their
 * variables is in error, the inherited action is not.
 *
 * Every action that parses is judged. Rodin first drops actions with a label, identifier or type
 * error, which those rules already report, so such an action can draw an extra EB037 finding here
 * without changing the model's verdict.
 */
class DisjointAssignmentChecker(private val ff: FormulaFactory = FormulaFactory.getDefault()) {

    fun check(project: EventBProject, inheritance: Map<String, MachineInheritance>): List<ValidationError> {
        val findings = mutableListOf<ValidationError>()

        for (machine in project.machines) {
            val inheritedByEvent = inheritance.getValue(machine.name).inheritedActionAssignments
            for (event in machine.events) {
                val inherited = inheritedByEvent[event.label].orEmpty()
                // Each own action's assigned names, repeats kept: `x, x ≔ 1, 2` names `x` twice.
                val assigned = event.actions.map { action ->
                    ff.parseAssignmentOrNull(action.assignment)?.assignedIdentifiers?.map { it.name }.orEmpty()
                }
                val assigners = mutableMapOf<String, MutableList<Int>>()
                assigned.forEachIndexed { index, names ->
                    for (name in names) assigners.getOrPut(name) { mutableListOf() }.add(index)
                }

                event.actions.forEachIndexed { index, action ->
                    val conflicting = assigned[index].distinct()
                        .filter { it in inherited || assigners.getValue(it).size > 1 }
                    if (conflicting.isEmpty()) return@forEachIndexed
                    // The variables grouped by the other actions that assign them, so that the
                    // message names an action only with the variables it assigns.
                    val groups = conflicting.groupBy { name ->
                        (name in inherited) to assigners.getValue(name).filter { it != index }.distinct()
                    }
                    val parts = groups.map { (others, names) ->
                        shared(names, others.first, others.second.map { event.actions[it].label })
                    }
                    // Each part but the last ends in a clause, so a comma precedes "and".
                    val shared = if (parts.size < 2) {
                        parts.single()
                    } else {
                        parts.dropLast(1).joinToString(", ") + ", and " + parts.last()
                    }
                    findings.add(
                        ValidationError(
                            filePath = machine.filePath,
                            severity = ValidationSeverity.ERROR,
                            message = "Action '${action.label}' of event '${event.label}' assigns $shared" +
                                "; the actions of an event must assign disjoint sets of variables",
                            element = "${event.label}/${action.label}",
                            ruleId = ValidationRules.CONFLICTING_ASSIGNMENTS.id,
                        ),
                    )
                }
            }
        }

        return findings
    }

    /**
     * One part of the message: `'v' and 'w', which action 'a' also assigns` for names that the
     * other actions also assign, or `'x' more than once` when the action names `x` twice itself.
     */
    private fun shared(names: List<String>, withInherited: Boolean, others: List<String>): String {
        val assigned = andList(names.map { "'$it'" })
        val parties = buildList {
            if (withInherited) add("an inherited action")
            if (others.isNotEmpty()) add((if (others.size == 1) "action " else "actions ") + andList(others.map { "'$it'" }))
        }
        if (parties.isEmpty()) return "$assigned more than once"
        val verb = if (others.size + (if (withInherited) 1 else 0) == 1) "assigns" else "assign"
        return "$assigned, which ${andList(parties)} also $verb"
    }

    /** `a`, `a and b`, `a, b and c`. */
    private fun andList(items: List<String>): String = if (items.size < 2) {
        items.joinToString("")
    } else {
        items.dropLast(1).joinToString(", ") + " and " + items.last()
    }
}
