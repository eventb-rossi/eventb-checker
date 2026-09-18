package com.eventb.checker.validation

import org.eventb.core.ast.Assignment
import org.eventb.core.ast.Expression
import org.eventb.core.ast.FormulaFactory
import org.eventb.core.ast.Predicate

/**
 * The parsed assignment, or null when [assignment] has syntax errors. Checkers that parse model
 * formulas directly (rather than looking them up by label, which is ambiguous under duplicate
 * labels — EB021/EB022) share this so they cannot disagree on what parses; the syntax error itself
 * is reported elsewhere (EB005).
 */
internal fun FormulaFactory.parseAssignmentOrNull(assignment: String): Assignment? {
    val result = stackSafeOrNull { parseAssignment(assignment, null) } ?: return null
    return if (result.hasProblem()) null else result.parsedAssignment
}

/** The parsed predicate, or null when [predicate] has syntax errors; see [parseAssignmentOrNull]. */
internal fun FormulaFactory.parsePredicateOrNull(predicate: String): Predicate? {
    val result = stackSafeOrNull { parsePredicate(predicate, null) } ?: return null
    return if (result.hasProblem()) null else result.parsedPredicate
}

/** The parsed expression, or null when [expression] has syntax errors; see [parseAssignmentOrNull]. */
internal fun FormulaFactory.parseExpressionOrNull(expression: String): Expression? {
    val result = stackSafeOrNull { parseExpression(expression, null) } ?: return null
    return if (result.hasProblem()) null else result.parsedExpression
}

/**
 * Runs [block], returning null if it exhausts the JVM stack.
 *
 * A formula nested a few thousand levels deep (`c = ((((…1…))))`, `¬¬¬…P`) overflows Rodin's
 * recursive-descent parser — and, once parsed, the equally recursive AST walks (`typeCheck`,
 * `getWDPredicate`, `toString`). A `StackOverflowError` is not an `Exception`, so it would escape
 * `main` as a raw JVM stack trace with no report on stdout, losing every finding in the rest of
 * the model over one pathological formula.
 *
 * Recovery is sound because nothing shared survives the unwind: `FormulaFactory.parseGeneric`
 * builds a fresh `ParseResult`, `Scanner` and `GenParser` per call, and `Formula.typeCheck`
 * snapshots the type environment rather than mutating the caller's builder. Only
 * `StackOverflowError` is caught — an `OutOfMemoryError` leaves the whole JVM unusable and belongs
 * to the top-level net in `main`.
 *
 * Wrap one item's worth of work: a guard around a loop would discard the items after the
 * offending one as well.
 */
internal inline fun <T> stackSafeOrNull(block: () -> T): T? = try {
    block()
} catch (overflow: StackOverflowError) {
    null
}

/**
 * Longest formula text a finding carries. Chosen so real model formulas are never abridged — the
 * longest in the sample corpus is 292 characters — while hostile input stays bounded.
 */
private const val MAX_FORMULA_EXCERPT = 500

/**
 * [this] formula text, shortened for a finding's `formula` field. A hostile formula is thousands
 * of characters of nested parentheses, and every formatter serialises the field verbatim and
 * unbounded — SARIF included, which goes on to GitHub Code Scanning.
 */
internal fun String.formulaExcerpt(): String {
    if (length <= MAX_FORMULA_EXCERPT) return this
    // Never cut a surrogate pair in half: a lone surrogate is not valid UTF-16 text and is mangled
    // or rejected on the way into JSON and SARIF.
    val end = if (this[MAX_FORMULA_EXCERPT - 1].isHighSurrogate()) MAX_FORMULA_EXCERPT - 1 else MAX_FORMULA_EXCERPT
    return take(end) + "… ($length characters total)"
}

/**
 * EB005 for [elementLabel], whose formula exhausted the JVM stack.
 *
 * One wording for every stage that can overflow — the initial parse, the type checker's re-parse,
 * the type check itself. They are the same fact about the same formula, and the reader's remedy is
 * the same, so they are deliberately byte-identical: [ValidationResult] carries one finding per
 * distinct defect, and only identical findings collapse.
 */
internal fun tooDeeplyNestedError(filePath: String, elementLabel: String, formula: String) = ValidationError(
    filePath = filePath,
    severity = ValidationSeverity.ERROR,
    message = "Formula parse error: '$elementLabel' is nested too deeply to analyse " +
        "(the checker exhausted the JVM stack); reduce the nesting depth",
    element = elementLabel,
    formula = formula.formulaExcerpt(),
    ruleId = ValidationRules.FORMULA_PARSE_ERROR.id,
)
