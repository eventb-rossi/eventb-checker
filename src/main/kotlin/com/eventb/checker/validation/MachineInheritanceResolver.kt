package com.eventb.checker.validation

import com.eventb.checker.model.Event
import com.eventb.checker.model.EventBProject
import com.eventb.checker.model.Machine
import org.eventb.core.ast.FormulaFactory

/**
 * Per-machine identifier sets the EB011/EB012/EB014 lints need but a machine's literal clauses do not
 * give directly: what refinement materializes into it from ancestors (the `inherited*` sets), and — for
 * the dead/unmodified-variable lints — how its own-and-below clauses use its declared variables across
 * the refinement chain (the `chain*` sets and [inheritedVariableNames]).
 */
data class MachineInheritance(
    /**
     * Names referenced by inherited clauses: every ancestor invariant (typing-shaped conjuncts
     * excluded, exactly as [nonTypingReferenceNames] does for own invariants), plus the guards and
     * action right-hand sides that `extended` events inherit from their abstract events (with
     * inherited event parameters subtracted, so they never surface as machine-level references).
     */
    val inheritedReferences: Set<String>,
    /**
     * Names assigned by actions that `extended` **non-INITIALISATION** events inherit. INITIALISATION
     * writes are tracked apart in [initAssignedIdentifiers]: giving a variable its initial value is
     * not *modifying* it, and the split is what lets EB012 recognise a constant-in-disguise
     * (INIT-assigned, never modified) inherited from an abstract machine.
     */
    val inheritedEventAssignments: Set<String>,
    /**
     * Every name the machine's materialized INITIALISATION assigns — its own actions plus, for an
     * extended INITIALISATION, the inherited chain's — or null when that chain cannot be fully
     * resolved (missing parent machine, REFINES cycle, ancestor without an INITIALISATION event,
     * or an unparseable inherited action). Empty for a machine without an INITIALISATION event.
     */
    val initAssignedIdentifiers: Set<String>?,
    /**
     * The identifiers declared by this machine's REFINES ancestors' own `variables`. A variable in
     * this set is *inherited*, not declared here, so [IdentifierAnalyzer] skips it and judges it at
     * the more abstract machine that declares it — each retained variable is judged exactly once.
     */
    val inheritedVariableNames: Set<String>,
    /**
     * References of this machine's own clauses ([Machine.ownUsage]) unioned with those of its transitive
     * REFINES **descendants** — i.e. every use of a variable declared here, at or below its declaring
     * machine. [IdentifierAnalyzer] combines this with the upward [inheritedReferences]; the downward
     * half keeps a variable declared and typed here but used only in a refinement from being flagged
     * dead. Descendants contribute their *own* usage only — their inherited sets are not echoed back up.
     */
    val chainReferences: Set<String>,
    /**
     * Variables assigned by this machine's own non-INITIALISATION events unioned with those of its
     * transitive REFINES **descendants** — the whole-chain modification set. Keeps a variable declared
     * here but modified only in a refinement from being flagged unmodified (EB012).
     */
    val chainEventAssignments: Set<String>,
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
        val resolution = Resolution(
            machinesByName = project.machines.associateBy { it.name },
            childrenByParent = project.machines.groupBy { it.refinesMachine },
        )
        return project.machines.associate { it.name to resolution.inheritanceOf(it) }
    }

    private data class EventContribution(val references: Set<String>, val assignments: Set<String>, val effectiveParameters: Set<String>)

    private inner class Resolution(
        val machinesByName: Map<String, Machine>,
        /** Machines keyed by the name of the machine they REFINES (null key = root machines). */
        val childrenByParent: Map<String?, List<Machine>>,
    ) {
        /**
         * Each machine's own-text usage, parsed exactly once. Unlike the inheritance walks — which are
         * deliberately recomputed per machine to stay order- and cycle-independent — [Machine.ownUsage]
         * is a pure per-machine function, so memoizing it is safe and keeps the downward union from
         * re-parsing a descendant's formulas once per ancestor (mirrors rossi's precomputed `mach_refs`).
         */
        private val ownUsageByName: Map<String, MachineUsage> = machinesByName.mapValues { it.value.ownUsage(ff) }

        /** The machine [machine] REFINES, resolved against the project, or null if none or unresolvable. */
        fun parentOf(machine: Machine): Machine? = machine.refinesMachine?.let { machinesByName[it] }

        fun inheritanceOf(machine: Machine): MachineInheritance {
            val references = mutableSetOf<String>()
            val eventAssignments = mutableSetOf<String>()

            val parent = parentOf(machine)
            if (parent != null) {
                references.addAll(invariantReferencesIncludingSelf(parent))
                for (event in machine.events.filter { it.extended }) {
                    for (abstractEvent in abstractEventsOf(parent, event)) {
                        val contribution = contributionOf(parent, abstractEvent)
                        references.addAll(contribution.references)
                        // An extended INITIALISATION's inherited writes are init-assignments, tracked
                        // by initAssignedIdentifiers; only genuine events contribute modifications.
                        if (event.label != INITIALISATION) eventAssignments.addAll(contribution.assignments)
                    }
                }
            }

            // Own usage plus every descendant's own usage — the memo already holds each machine's, so
            // no formula is re-parsed here (the analyzer reads these instead of recomputing own usage).
            val own = ownUsageByName.getValue(machine.name)
            val chainReferences = own.references.toMutableSet()
            val chainEventAssignments = own.eventAssigned.toMutableSet()
            for (descendantName in transitiveDescendants(machine)) {
                val usage = ownUsageByName.getValue(descendantName)
                chainReferences.addAll(usage.references)
                chainEventAssignments.addAll(usage.eventAssigned)
            }

            return MachineInheritance(
                inheritedReferences = references,
                inheritedEventAssignments = eventAssignments,
                initAssignedIdentifiers = initAssignedIdentifiers(machine),
                inheritedVariableNames = inheritedVariableNames(machine),
                chainReferences = chainReferences,
                chainEventAssignments = chainEventAssignments,
            )
        }

        /**
         * The identifiers declared by [machine]'s REFINES ancestors' own `variables` — the variables
         * it inherits rather than declares. Cycle-guarded upward walk (a cyclic member sees its
         * partner's variables as inherited, so such a variable is judged nowhere, matching rossi's
         * disabling of the lints on a broken chain).
         */
        fun inheritedVariableNames(machine: Machine, visiting: MutableSet<String> = mutableSetOf()): Set<String> {
            val parent = parentOf(machine) ?: return emptySet()
            if (!visiting.add(machine.name)) return emptySet()

            val names = parent.variables.mapTo(mutableSetOf()) { it.identifier }
            names.addAll(inheritedVariableNames(parent, visiting))
            return names
        }

        /** Names of the machines that refine [machine] transitively, cycle-guarded (mirrors the upward walks). */
        fun transitiveDescendants(machine: Machine, visiting: MutableSet<String> = mutableSetOf()): Set<String> {
            if (!visiting.add(machine.name)) return emptySet()

            val descendants = mutableSetOf<String>()
            for (child in childrenByParent[machine.name].orEmpty()) {
                descendants.add(child.name)
                descendants.addAll(transitiveDescendants(child, visiting))
            }
            return descendants
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
                val parent = parentOf(machine)
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
                ff.parsePredicateOrNull(guard.predicate)?.let { ownReferences.addAll(it.referenceNames()) }
            }
            for (action in event.actions) {
                val parsed = ff.parseAssignmentOrNull(action.assignment) ?: continue
                // Only the right-hand side is a reference; a written variable is not "used" by being
                // assigned (mirrors the own-machine rule in IdentifierAnalyzer).
                ownReferences.addAll(parsed.rhsReferenceNames())
                assignments.addAll(parsed.assignedNames())
            }
            references.addAll(ownReferences - effectiveParameters)

            visiting.remove(key)
            return EventContribution(references, assignments, effectiveParameters)
        }

        /**
         * References of [machine]'s own invariants unioned with its ancestors', typing-shaped
         * conjuncts excluded (each machine's invariants judged against that machine's own variables,
         * so `v ∈ E` does not count as a use of `v`). Theorem invariants are kept in full — a theorem
         * states a property, not a typing declaration.
         */
        fun invariantReferencesIncludingSelf(machine: Machine, visiting: MutableSet<String> = mutableSetOf()): Set<String> {
            if (!visiting.add(machine.name)) return emptySet()

            val references = machine.invariantReferenceNames(ff).toMutableSet()
            parentOf(machine)?.let { parent ->
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
