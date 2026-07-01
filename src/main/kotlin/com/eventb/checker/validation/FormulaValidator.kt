package com.eventb.checker.validation

import com.eventb.checker.model.EventBProject
import org.eventb.core.ast.FormulaFactory
import org.eventb.core.ast.IParseResult

enum class FormulaKind { PREDICATE, EXPRESSION, ASSIGNMENT }

class FormulaValidator {

    private val ff: FormulaFactory = FormulaFactory.getDefault()

    data class FormulaCheck(val filePath: String, val elementLabel: String, val formula: String, val kind: FormulaKind)

    fun collectFormulas(project: EventBProject): List<FormulaCheck> {
        val checks = mutableListOf<FormulaCheck>()

        for (machine in project.machines) {
            val fp = machine.filePath

            for (inv in machine.invariants) {
                checks.add(FormulaCheck(fp, inv.label, inv.predicate, FormulaKind.PREDICATE))
            }
            machine.variant?.let {
                checks.add(FormulaCheck(fp, it.label, it.expression, FormulaKind.EXPRESSION))
            }
            for (event in machine.events) {
                for (guard in event.guards) {
                    checks.add(FormulaCheck(fp, "${event.label}/${guard.label}", guard.predicate, FormulaKind.PREDICATE))
                }
                for (action in event.actions) {
                    checks.add(FormulaCheck(fp, "${event.label}/${action.label}", action.assignment, FormulaKind.ASSIGNMENT))
                }
                for (witness in event.witnesses) {
                    checks.add(FormulaCheck(fp, "${event.label}/${witness.label}", witness.predicate, FormulaKind.PREDICATE))
                }
            }
        }

        for (ctx in project.contexts) {
            for (axiom in ctx.axioms) {
                checks.add(FormulaCheck(ctx.filePath, axiom.label, axiom.predicate, FormulaKind.PREDICATE))
            }
        }

        return checks
    }

    fun validate(check: FormulaCheck): List<ValidationError> {
        val result = parse(check.formula, check.kind)

        if (!result.hasProblem()) return emptyList()

        // A predicate that fails to parse but is a well-formed assignment is a misplaced
        // assignment operator (:=, :∈, :|) in an invariant/guard/witness/axiom, where '='
        // was almost certainly intended. Report that precisely instead of a generic parse error.
        if (check.kind == FormulaKind.PREDICATE && !parse(check.formula, FormulaKind.ASSIGNMENT).hasProblem()) {
            return listOf(
                ValidationError(
                    filePath = check.filePath,
                    severity = ValidationSeverity.ERROR,
                    message = "Assignment operator in predicate: '${check.elementLabel}' uses an assignment " +
                        "(':=', ':∈', or ':|') where a predicate is required — did you mean '=' for equality?",
                    element = check.elementLabel,
                    formula = check.formula,
                    ruleId = ValidationRules.ASSIGNMENT_IN_PREDICATE.id,
                ),
            )
        }

        return result.problems.map { problem ->
            ValidationError(
                filePath = check.filePath,
                severity = ValidationSeverity.ERROR,
                message = "Formula parse error: $problem",
                element = check.elementLabel,
                formula = check.formula,
                ruleId = ValidationRules.FORMULA_PARSE_ERROR.id,
            )
        }
    }

    private fun parse(formula: String, kind: FormulaKind): IParseResult = when (kind) {
        FormulaKind.PREDICATE -> ff.parsePredicate(formula, null)
        FormulaKind.EXPRESSION -> ff.parseExpression(formula, null)
        FormulaKind.ASSIGNMENT -> ff.parseAssignment(formula, null)
    }
}
