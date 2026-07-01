package com.eventb.checker.validation

import com.eventb.checker.model.EventBProject
import org.eventb.core.ast.FormulaFactory

/**
 * Reports declared identifiers whose spelling collides with a reserved Event-B operator or
 * keyword token (e.g. a variable named `mod` or a constant named `card`). Such a name cannot
 * serve as an identifier — Rodin lexes it as the operator, so any formula referring to it fails
 * to parse. Detection reuses the Rodin AST factory's own identifier-validity check, keeping the
 * reserved-token set in sync with the grammar instead of a hand-maintained operator list.
 */
class ShadowedNameChecker(private val ff: FormulaFactory = FormulaFactory.getDefault()) {

    fun check(project: EventBProject): List<ValidationError> {
        val findings = mutableListOf<ValidationError>()

        for (machine in project.machines) {
            for (variable in machine.variables) {
                report(findings, variable.identifier, variable.label, machine.filePath, "variable")
            }
            for (event in machine.events) {
                for (parameter in event.parameters) {
                    report(findings, parameter.identifier, parameter.label, machine.filePath, "event parameter")
                }
            }
        }

        for (ctx in project.contexts) {
            for (set in ctx.carrierSets) {
                report(findings, set.identifier, set.label, ctx.filePath, "carrier set")
            }
            for (constant in ctx.constants) {
                report(findings, constant.identifier, constant.label, ctx.filePath, "constant")
            }
        }

        return findings
    }

    private fun report(findings: MutableList<ValidationError>, name: String, label: String, filePath: String, kind: String) {
        if (name.isBlank() || ff.isValidIdentifierName(name)) return
        findings.add(
            ValidationError(
                filePath = filePath,
                severity = ValidationSeverity.WARNING,
                message = "Shadowed name: $kind '$name' cannot be used as an identifier — it is a reserved " +
                    "Event-B operator or keyword token, or is otherwise not a valid identifier name",
                element = label,
                ruleId = ValidationRules.SHADOWED_NAME.id,
            ),
        )
    }
}
