package com.eventb.checker.validation

import com.eventb.checker.model.Event
import com.eventb.checker.model.EventBProject
import com.eventb.checker.model.Machine

/**
 * Reports identifiers and labels that are declared more than once within the scope
 * where Event-B requires uniqueness. Identifiers (variables, constants, carrier sets,
 * parameters) and labels (invariants, events, guards, actions, axioms, witnesses) form
 * two separate namespaces, so a variable and an invariant that share a spelling do not
 * conflict. For an extended event, inherited guards and actions are part of the event's
 * shared label namespace and therefore also conflict with same-named local clauses.
 */
class DuplicateNameChecker {

    fun check(project: EventBProject): List<ValidationError> {
        val findings = mutableListOf<ValidationError>()
        val inheritedLabels = InheritedEventLabelResolver(project)

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
                findings += checkEvent(event, machine, inheritedLabels)
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

    private fun checkEvent(event: Event, machine: Machine, inheritedLabels: InheritedEventLabelResolver): List<ValidationError> {
        val scope = "in event '${event.label}' of machine '${machine.name}'"
        val localClauses = event.guards.map { it.label to EventClauseKind.GUARD } +
            event.actions.map { it.label to EventClauseKind.ACTION }
        val duplicateLocalClauseCounts = duplicateNonBlankNameCounts(localClauses.map { it.first })
        return findDuplicates(
            event.parameters,
            { it.identifier },
            machine.filePath,
            ValidationRules.DUPLICATE_IDENTIFIER,
            kind = "parameter identifier",
            scope = scope,
        ) +
            // Rodin shares a single label namespace across guards and actions within an event, so a guard and an
            // action with the same label conflict (both would yield the proof-obligation name evt/<label>/WD).
            duplicateFindings(
                duplicateLocalClauseCounts.toSortedMap(),
                machine.filePath,
                ValidationRules.DUPLICATE_LABEL,
                kind = "guard or action label",
                scope = scope,
            ) +
            findDuplicates(
                event.witnesses,
                { it.label },
                machine.filePath,
                ValidationRules.DUPLICATE_LABEL,
                kind = "witness label",
                scope = scope,
            ) + findInheritedLabelConflicts(event, machine, inheritedLabels, localClauses, duplicateLocalClauseCounts.keys)
    }

    private fun findInheritedLabelConflicts(
        event: Event,
        machine: Machine,
        resolver: InheritedEventLabelResolver,
        localClauses: List<Pair<String, EventClauseKind>>,
        duplicateLocalLabels: Set<String>,
    ): List<ValidationError> {
        if (localClauses.isEmpty()) return emptyList()
        val inherited = resolver.labelsInheritedBy(machine, event)
        if (inherited.isEmpty()) return emptyList()

        // The ordinary event-local pass already reports one EB022 for a name used more
        // than once locally. Do not add another finding for the inherited collision.
        return localClauses.mapNotNull { (label, localKind) ->
            if (label.isBlank() || label in duplicateLocalLabels) return@mapNotNull null
            val inheritedKind = inherited[label] ?: return@mapNotNull null
            ValidationError(
                filePath = machine.filePath,
                severity = ValidationSeverity.ERROR,
                message = "${localKind.displayName} label '$label' in extended event '${event.label}' conflicts " +
                    "with inherited ${inheritedKind.noun} label",
                element = "${event.label}/$label",
                ruleId = ValidationRules.DUPLICATE_LABEL.id,
            )
        }
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
        val duplicateCounts = duplicateNonBlankNameCounts(items.map(nameOf)).toSortedMap()
        return duplicateFindings(duplicateCounts, filePath, rule, kind, scope)
    }

    private fun duplicateFindings(
        duplicateCounts: Map<String, Int>,
        filePath: String,
        rule: RuleDescriptor,
        kind: String,
        scope: String,
    ): List<ValidationError> {
        val verb = if (rule == ValidationRules.DUPLICATE_IDENTIFIER) "declared" else "used"
        return duplicateCounts.map { (name, count) ->
            ValidationError(
                filePath = filePath,
                severity = ValidationSeverity.ERROR,
                message = "Duplicate $kind '$name' $scope ($verb $count times)",
                element = name,
                ruleId = rule.id,
            )
        }
    }

    private enum class EventClauseKind(val noun: String, val displayName: String) {
        GUARD("guard", "Guard"),
        ACTION("action", "Action"),
    }

    /** Resolves the effective guard/action label table inherited by extended events. */
    private class InheritedEventLabelResolver(project: EventBProject) {
        private val machinesByName = project.machines.associateBy { it.name }

        fun labelsInheritedBy(machine: Machine, event: Event): Map<String, EventClauseKind> {
            if (!event.extended) return emptyMap()
            return inheritedLabels(machine, event, mutableSetOf(machine.name to event.label))
        }

        private fun effectiveLabels(
            machine: Machine,
            event: Event,
            visiting: MutableSet<Pair<String, String>>,
        ): Map<String, EventClauseKind> {
            val key = machine.name to event.label
            if (!visiting.add(key)) return emptyMap()

            val labels = linkedMapOf<String, EventClauseKind>()
            if (event.extended) {
                for ((label, kind) in inheritedLabels(machine, event, visiting)) {
                    labels.putIfAbsent(label, kind)
                }
            }

            for (guard in event.guards) {
                if (guard.label.isNotBlank()) labels.putIfAbsent(guard.label, EventClauseKind.GUARD)
            }
            for (action in event.actions) {
                if (action.label.isNotBlank()) labels.putIfAbsent(action.label, EventClauseKind.ACTION)
            }
            visiting.remove(key)
            return labels
        }

        private fun inheritedLabels(
            machine: Machine,
            event: Event,
            visiting: MutableSet<Pair<String, String>>,
        ): Map<String, EventClauseKind> {
            val parent = machine.refinesMachine?.let { machinesByName[it] } ?: return emptyMap()
            val labels = linkedMapOf<String, EventClauseKind>()
            for (refinedEvent in abstractEventsOf(parent, event)) {
                for ((label, kind) in effectiveLabels(parent, refinedEvent, visiting)) {
                    labels.putIfAbsent(label, kind)
                }
            }
            return labels
        }
    }
}
