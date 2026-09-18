package com.eventb.checker.validation

import org.eventb.core.ast.Formula

class WellDefinednessChecker {

    fun check(checkedFormulas: List<TypeCheckedFormula>): List<ValidationError> =
        // Both computing the WD lemma and rendering it are recursive AST walks, so a formula deep
        // enough to exhaust the stack simply gets no WD note. Nothing can be hidden by skipping
        // one: these are INFO findings, and a formula that pathological already draws an ERROR
        // from the parse or type-check stage.
        checkedFormulas.mapNotNull { stackSafeOrNull { wdFinding(it) } }

    private fun wdFinding(tcf: TypeCheckedFormula): ValidationError? {
        val wdPredicate = tcf.formula.wdPredicate ?: return null

        if (wdPredicate.tag == Formula.BTRUE) return null

        return ValidationError(
            filePath = tcf.filePath,
            severity = ValidationSeverity.INFO,
            message = "Well-definedness condition: $wdPredicate",
            element = tcf.elementLabel,
            formula = tcf.formulaText.formulaExcerpt(),
            ruleId = ValidationRules.WELL_DEFINEDNESS.id,
        )
    }
}
