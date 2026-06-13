package com.eventb.checker.validation

import com.eventb.checker.model.Event
import com.eventb.checker.model.EventBProject

/**
 * Reports identifiers and labels that are declared more than once within the scope
 * where Event-B requires uniqueness. Identifiers (variables, constants, carrier sets,
 * parameters) and labels (invariants, events, guards, actions, axioms, witnesses) form
 * two separate namespaces, so a variable and an invariant that share a spelling do not
 * conflict. Cross-component shadowing (e.g. a variable colliding with a seen context's
 * constant) is intentionally out of scope; it belongs with the type-checker's scope
 * resolution.
 */
class DuplicateNameChecker {

    fun check(project: EventBProject): List<ValidationError> {
        val findings = mutableListOf<ValidationError>()

        for (machine in project.machines) {
            val scope = "in machine '${machine.name}'"
            findings += findDuplicates(
                machine.variables,
                { it.identifier },
                machine.filePath,
                ValidationRules.DUPLICATE_IDENTIFIER,
                kind = "variable identifier",
                scope = scope,
            )
            findings += findDuplicates(
                machine.invariants,
                { it.label },
                machine.filePath,
                ValidationRules.DUPLICATE_LABEL,
                kind = "invariant label",
                scope = scope,
            )
            findings += findDuplicates(
                machine.events,
                { it.label },
                machine.filePath,
                ValidationRules.DUPLICATE_LABEL,
                kind = "event label",
                scope = scope,
            )
            for (event in machine.events) {
                findings += checkEvent(event, machine.name, machine.filePath)
            }
        }

        for (ctx in project.contexts) {
            val scope = "in context '${ctx.name}'"
            // Carrier sets and constants share a single identifier namespace, so a set and a constant
            // declared with the same name conflict.
            findings += findDuplicates(
                ctx.carrierSets.map { it.identifier } + ctx.constants.map { it.identifier },
                { it },
                ctx.filePath,
                ValidationRules.DUPLICATE_IDENTIFIER,
                kind = "carrier set or constant identifier",
                scope = scope,
            )
            findings += findDuplicates(
                ctx.axioms,
                { it.label },
                ctx.filePath,
                ValidationRules.DUPLICATE_LABEL,
                kind = "axiom label",
                scope = scope,
            )
        }

        return findings
    }

    private fun checkEvent(event: Event, machineName: String, filePath: String): List<ValidationError> {
        val scope = "in event '${event.label}' of machine '$machineName'"
        return findDuplicates(
            event.parameters,
            { it.identifier },
            filePath,
            ValidationRules.DUPLICATE_IDENTIFIER,
            kind = "parameter identifier",
            scope = scope,
        ) +
            // Rodin shares a single label namespace across guards and actions within an event, so a guard and an
            // action with the same label conflict (both would yield the proof-obligation name evt/<label>/WD).
            findDuplicates(
                event.guards.map { it.label } + event.actions.map { it.label },
                { it },
                filePath,
                ValidationRules.DUPLICATE_LABEL,
                kind = "guard or action label",
                scope = scope,
            ) +
            findDuplicates(
                event.witnesses,
                { it.label },
                filePath,
                ValidationRules.DUPLICATE_LABEL,
                kind = "witness label",
                scope = scope,
            )
    }

    /**
     * Emits one ERROR per name that occurs more than once, e.g.
     * "Duplicate invariant label 'inv1' in machine 'M' (used 2 times)". The verb follows the rule
     * (identifiers are "declared", labels are "used"). Blank names are skipped; results are ordered
     * by name for deterministic output.
     */
    private fun <T> findDuplicates(
        items: List<T>,
        nameOf: (T) -> String,
        filePath: String,
        rule: RuleDescriptor,
        kind: String,
        scope: String,
    ): List<ValidationError> {
        val verb = if (rule == ValidationRules.DUPLICATE_IDENTIFIER) "declared" else "used"
        return items
            .mapNotNull { nameOf(it).ifBlank { null } }
            .groupingBy { it }
            .eachCount()
            .filterValues { it > 1 }
            .toSortedMap()
            .map { (name, count) ->
                ValidationError(
                    filePath = filePath,
                    severity = ValidationSeverity.ERROR,
                    message = "Duplicate $kind '$name' $scope ($verb $count times)",
                    element = name,
                    ruleId = rule.id,
                )
            }
    }
}
