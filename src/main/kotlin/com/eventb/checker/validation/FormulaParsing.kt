package com.eventb.checker.validation

import org.eventb.core.ast.Assignment
import org.eventb.core.ast.FormulaFactory
import org.eventb.core.ast.Predicate

/**
 * The parsed assignment, or null when [assignment] has syntax errors. Checkers that parse model
 * formulas directly (rather than looking them up by label, which is ambiguous under duplicate
 * labels — EB021/EB022) share this so they cannot disagree on what parses; the syntax error itself
 * is reported elsewhere (EB005).
 */
internal fun FormulaFactory.parseAssignmentOrNull(assignment: String): Assignment? {
    val result = parseAssignment(assignment, null)
    return if (result.hasProblem()) null else result.parsedAssignment
}

/** The parsed predicate, or null when [predicate] has syntax errors; see [parseAssignmentOrNull]. */
internal fun FormulaFactory.parsePredicateOrNull(predicate: String): Predicate? {
    val result = parsePredicate(predicate, null)
    return if (result.hasProblem()) null else result.parsedPredicate
}
