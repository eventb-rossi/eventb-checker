package com.eventb.checker.validation

import com.eventb.checker.model.Event
import com.eventb.checker.model.EventBProject
import com.eventb.checker.model.Machine
import org.eventb.core.ast.FormulaFactory

/**
 * The identifier sets Event-B refinement materializes into a machine beyond its literal clauses.
 */
data class MachineInheritance(
    /**
     * Names referenced by inherited clauses: every ancestor invariant, plus the guards and actions
     * that `extended` events inherit from their abstract events (with inherited event parameters
     * subtracted, so they never surface as machine-level references).
     */
    val inheritedReferences: Set<String>,
    /** Names assigned by actions that `extended` events (INITIALISATION included) inherit. */
    val inheritedAssignments: Set<String>,
    /**
     * Every name the machine's materialized INITIALISATION assigns — its own actions plus, for an
     * extended INITIALISATION, the inherited chain's — or null when that chain cannot be fully
     * resolved (missing parent machine, REFINES cycle, ancestor without an INITIALISATION event,
     * or an unparseable inherited action). Empty for a machine without an INITIALISATION event.
     */
    val initAssignedIdentifiers: Set<String>?,
)

/**
 * Resolves per-machine [MachineInheritance] by walking REFINES chains upward: an `extended` event
 * inherits its abstract event's parameters, guards, and actions — transitively, while each link in
 * the chain is itself extended — and abstract invariants constrain every refinement unconditionally.
 * Judging EB011/EB012/EB014 without these sets contradicts the machine Rodin actually builds.
 *
 * Ancestor formulas are parsed directly from the model rather than looked up in the type checker's
 * per-file records; label-keyed lookup would be ambiguous under duplicate labels (EB021/EB022).
 * The walks are plain cycle-guarded recursions, not caches: refinement chains are a handful of
 * machines deep, and recomputing per machine keeps every result independent of the order machines
 * are resolved in, cycles included. Reporting cycles and missing REFINES targets is EB008/EB009's
 * job, never this resolver's — the guards here exist only to terminate.
 */
class MachineInheritanceResolver(private val ff: FormulaFactory = FormulaFactory.getDefault()) {

    fun resolve(project: EventBProject): Map<String, MachineInheritance> {
        val resolution = Resolution(project.machines.associateBy { it.name })
        return project.machines.associate { it.name to resolution.inheritanceOf(it) }
    }

    private data class EventContribution(val references: Set<String>, val assignments: Set<String>, val effectiveParameters: Set<String>)

    private inner class Resolution(val machinesByName: Map<String, Machine>) {

        fun inheritanceOf(machine: Machine): MachineInheritance {
            val references = mutableSetOf<String>()
            val assignments = mutableSetOf<String>()

            val parent = machine.refinesMachine?.let { machinesByName[it] }
            if (parent != null) {
                references.addAll(invariantReferencesIncludingSelf(parent))
                for (event in machine.events.filter { it.extended }) {
                    for (abstractEvent in abstractEventsOf(parent, event)) {
                        val contribution = contributionOf(parent, abstractEvent)
                        references.addAll(contribution.references)
                        assignments.addAll(contribution.assignments)
                    }
                }
            }

            return MachineInheritance(references, assignments, initAssignedIdentifiers(machine))
        }

        /** The events of [parent] that [event] refines, resolved via the shared [refinedEventLabels]. */
        fun abstractEventsOf(parent: Machine, event: Event): List<Event> =
            refinedEventLabels(event).mapNotNull { label -> parent.events.find { it.label == label } }

        /**
         * What [event], as materialized in [machine], contributes to any extended event refining
         * it: its ancestors' clauses (only while [event] is itself extended, so the chain stops
         * after the first non-extended event, whose body is self-contained) plus its own guard and
         * action identifiers. References are collected against the parameters in scope at each
         * level — a level's own parameters and everything inherited from above — so an event
         * parameter never escapes as a machine-level reference.
         */
        fun contributionOf(
            machine: Machine,
            event: Event,
            visiting: MutableSet<Pair<String, String>> = mutableSetOf(),
        ): EventContribution {
            val key = machine.name to event.label
            if (!visiting.add(key)) {
                // Refinement cycle: this event's own clauses are already being collected by the
                // frame in progress, so contribute nothing further.
                return EventContribution(emptySet(), emptySet(), parameterNames(event))
            }

            val references = mutableSetOf<String>()
            val assignments = mutableSetOf<String>()
            val effectiveParameters = mutableSetOf<String>()
            if (event.extended) {
                val parent = machine.refinesMachine?.let { machinesByName[it] }
                if (parent != null) {
                    for (abstractEvent in abstractEventsOf(parent, event)) {
                        val ancestor = contributionOf(parent, abstractEvent, visiting)
                        references.addAll(ancestor.references)
                        assignments.addAll(ancestor.assignments)
                        effectiveParameters.addAll(ancestor.effectiveParameters)
                    }
                }
            }
            effectiveParameters.addAll(parameterNames(event))

            val ownReferences = mutableSetOf<String>()
            for (guard in event.guards) {
                ff.parsePredicateOrNull(guard.predicate)?.freeIdentifiers?.mapTo(ownReferences) { it.name }
            }
            for (action in event.actions) {
                val parsed = ff.parseAssignmentOrNull(action.assignment) ?: continue
                parsed.freeIdentifiers.mapTo(ownReferences) { it.name }
                val assigned = parsed.assignedIdentifiers.map { it.name }
                ownReferences.addAll(assigned)
                assignments.addAll(assigned)
            }
            references.addAll(ownReferences - effectiveParameters)

            visiting.remove(key)
            return EventContribution(references, assignments, effectiveParameters)
        }

        /** Free identifiers of [machine]'s own invariants unioned with its ancestors'. */
        fun invariantReferencesIncludingSelf(machine: Machine, visiting: MutableSet<String> = mutableSetOf()): Set<String> {
            if (!visiting.add(machine.name)) return emptySet()

            val references = mutableSetOf<String>()
            for (invariant in machine.invariants) {
                ff.parsePredicateOrNull(invariant.predicate)?.freeIdentifiers?.mapTo(references) { it.name }
            }
            machine.refinesMachine?.let { machinesByName[it] }?.let { parent ->
                references.addAll(invariantReferencesIncludingSelf(parent, visiting))
            }
            return references
        }

        /**
         * Every name [machine]'s materialized INITIALISATION assigns, or null when its extended
         * chain is unresolvable. An own action that does not parse contributes nothing — the
         * syntax error is EB005's, and the INITIALISATION provably does not assign what does not
         * parse — whereas an unresolvable inherited link is a defect in another file, so it
         * suppresses the whole judgment instead.
         */
        fun initAssignedIdentifiers(machine: Machine): Set<String>? {
            val initEvent = machine.events.find { it.label == INITIALISATION } ?: return emptySet()

            val assigned = mutableSetOf<String>()
            for (action in initEvent.actions) {
                ff.parseAssignmentOrNull(action.assignment)?.assignedIdentifiers?.mapTo(assigned) { it.name }
            }
            if (!initEvent.extended) return assigned

            val inherited = inheritedInitAssignments(machine) ?: return null
            return assigned + inherited
        }

        /**
         * Names assigned along [machine]'s inherited INITIALISATION chain, or null when a link
         * does not resolve: a missing parent machine, a REFINES cycle, an ancestor without an
         * INITIALISATION event, or an ancestor INITIALISATION action that does not parse (what it
         * assigns is unknowable). Unlike ordinary events, an INITIALISATION always refines its
         * parent's by name, and completeness can only be judged when every link up to a
         * self-contained (non-extended) INITIALISATION — or a root machine — is present.
         */
        fun inheritedInitAssignments(machine: Machine, visiting: MutableSet<String> = mutableSetOf()): Set<String>? {
            if (!visiting.add(machine.name)) return null

            val parentName = machine.refinesMachine ?: return emptySet()
            val parent = machinesByName[parentName] ?: return null
            val parentInit = parent.events.find { it.label == INITIALISATION } ?: return null

            val assigned = mutableSetOf<String>()
            for (action in parentInit.actions) {
                val parsed = ff.parseAssignmentOrNull(action.assignment) ?: return null
                parsed.assignedIdentifiers.mapTo(assigned) { it.name }
            }
            if (parentInit.extended) {
                val above = inheritedInitAssignments(parent, visiting) ?: return null
                assigned.addAll(above)
            }
            return assigned
        }
    }

    private fun parameterNames(event: Event): Set<String> = event.parameters.mapTo(mutableSetOf()) { it.identifier }
}
