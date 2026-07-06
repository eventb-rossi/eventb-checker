package com.eventb.checker.validation

import org.eventb.core.ast.Formula

interface FormulaRecord {
    val formula: Formula<*>
    val formulaText: String
    val filePath: String
    val elementLabel: String
    val kind: FormulaKind
}

data class ParsedFormula(
    override val formula: Formula<*>,
    override val formulaText: String,
    override val filePath: String,
    override val elementLabel: String,
    override val kind: FormulaKind,
) : FormulaRecord

data class TypeCheckedFormula(
    override val formula: Formula<*>,
    override val formulaText: String,
    override val filePath: String,
    override val elementLabel: String,
    override val kind: FormulaKind,
) : FormulaRecord

data class TypeCheckResult(
    val errors: List<ValidationError>,
    val parsedFormulas: List<ParsedFormula>,
    val checkedFormulas: List<TypeCheckedFormula>,
)
