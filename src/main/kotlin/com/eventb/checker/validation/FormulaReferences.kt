package com.eventb.checker.validation

import com.eventb.checker.model.Machine
import org.eventb.core.ast.Assignment
import org.eventb.core.ast.AssociativePredicate
import org.eventb.core.ast.BecomesEqualTo
import org.eventb.core.ast.BecomesMemberOf
import org.eventb.core.ast.BecomesSuchThat
import org.eventb.core.ast.Formula
import org.eventb.core.ast.FormulaFactory
import org.eventb.core.ast.FreeIdentifier
import org.eventb.core.ast.Predicate
import org.eventb.core.ast.RelationalPredicate

/**
 * Reference and assignment extraction shared by the variable-usage lints (EB011/EB012) and the
 * inheritance resolver. Mirrors rossi's `lint::collect_non_typing_refs` and
 * `collect_referenced_in_action_rhs` so the two tools classify a variable the same way.
 */

/** The names [this] predicate references (its free identifiers; quantifier binders excluded by Rodin). */
fun Predicate.referenceNames(): Set<String> = freeIdentifiers.mapTo(mutableSetOf()) { it.name }

/**
 * The names [this] predicate references, **excluding** top-level `∧`-conjuncts of the typing shape
 * `v ∈ E` / `v ⊆ E` whose bound `E` mentions no machine variable. Every Rodin variable needs such an
 * invariant just to be typed, so counting the bare left-hand `v` as a use would make a variable that
 * exists only to be typed look alive; only `E`'s identifiers are kept. A bound that reads a machine
 * variable (`cur ∈ dom(routes)`, a gluing `abs ∈ ran(conc)`) constrains dynamic state — a real use —
 * so the whole conjunct counts. Non-membership conjuncts (equalities, strict subsets, negations) are
 * always real uses. [machineVars] is the owning machine's variable set.
 */
fun Predicate.nonTypingReferenceNames(machineVars: Set<String>): Set<String> {
    val acc = mutableSetOf<String>()
    collectNonTyping(this, machineVars, acc)
    return acc
}

private fun collectNonTyping(pred: Predicate, machineVars: Set<String>, acc: MutableSet<String>) {
    when {
        pred.tag == Formula.LAND && pred is AssociativePredicate ->
            pred.children.forEach { collectNonTyping(it, machineVars, acc) }

        (pred.tag == Formula.IN || pred.tag == Formula.SUBSETEQ) &&
            pred is RelationalPredicate &&
            pred.left is FreeIdentifier -> {
            val boundRefs = pred.right.freeIdentifiers.map { it.name }
            acc.addAll(boundRefs)
            if (boundRefs.any { it in machineVars }) {
                // The bound reads machine state: a constraint between variables, not typing — so the
                // bare left-hand identifier counts as a use too.
                pred.freeIdentifiers.mapTo(acc) { it.name }
            }
        }

        else -> pred.freeIdentifiers.mapTo(acc) { it.name }
    }
}

/**
 * The names [this] assignment reads on its **right-hand side**, so a variable that is only ever
 * written (an output) is not counted as referenced. A becomes-such-that condition names the primed
 * post-state (`x'`); its prime is stripped so it reads as a use of `x`.
 */
fun Assignment.rhsReferenceNames(): Set<String> = when (this) {
    is BecomesEqualTo -> expressions.flatMapTo(mutableSetOf()) { e -> e.freeIdentifiers.map { it.name } }
    is BecomesMemberOf -> set.freeIdentifiers.mapTo(mutableSetOf()) { it.name }
    is BecomesSuchThat -> condition.freeIdentifiers.mapTo(mutableSetOf()) { it.name.removeSuffix("'") }
    else -> emptySet()
}

/** The variable names [this] assignment writes (its left-hand side). */
fun Assignment.assignedNames(): Set<String> = assignedIdentifiers.mapTo(mutableSetOf()) { it.name }

/**
 * The names [this] machine's own invariants reference, with typing-shaped conjuncts excluded and
 * theorems kept in full, each judged against this machine's own variables. Shared by the EB011/EB012
 * lints ([IdentifierAnalyzer]) and the inheritance resolver so the typing-exclusion rule — the core of
 * those lints — lives in exactly one place.
 */
fun Machine.invariantReferenceNames(ff: FormulaFactory): Set<String> {
    val machineVars = variables.mapTo(mutableSetOf()) { it.identifier }
    val references = mutableSetOf<String>()
    for (invariant in invariants) {
        val parsed = ff.parsePredicateOrNull(invariant.predicate) ?: continue
        references.addAll(
            if (invariant.theorem) parsed.referenceNames() else parsed.nonTypingReferenceNames(machineVars),
        )
    }
    return references
}
