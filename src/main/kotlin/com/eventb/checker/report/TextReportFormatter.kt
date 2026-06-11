package com.eventb.checker.report

import com.eventb.checker.validation.ValidationResult
import com.eventb.checker.validation.ValidationSeverity

class TextReportFormatter : ReportFormatter {

    override fun format(result: ValidationResult): String {
        val sb = StringBuilder()

        sb.appendLine("=== Event-B Model Validation Report ===")
        sb.appendLine()

        val summary = result.summary
        sb.appendLine("Files:    ${summary.machineCount} machine(s), ${summary.contextCount} context(s)")
        sb.appendLine("Formulas: ${summary.formulaCount} checked")
        sb.appendLine("Errors:   ${summary.errorCount}")
        sb.appendLine("Warnings: ${summary.warningCount}")
        sb.appendLine("Info:     ${summary.infoCount}")
        summary.proofSummary?.let { ps ->
            val manual = if (ps.manualDischarged > 0) " (${ps.manualDischarged} manual)" else ""
            val parts = mutableListOf("${ps.discharged}/${ps.total} discharged$manual")
            if (ps.reviewed > 0) parts.add("${ps.reviewed} reviewed")
            if (ps.pending > 0) parts.add("${ps.pending} pending")
            if (ps.unattempted > 0) parts.add("${ps.unattempted} unattempted")
            if (ps.broken > 0) parts.add("${ps.broken} broken")
            sb.appendLine("Proofs:   ${parts.joinToString(", ")}")
        }
        sb.appendLine()

        if (result.isValid && result.errors.isEmpty()) {
            sb.appendLine("RESULT: VALID")
            return sb.toString()
        }

        if (result.isValid && result.errors.isNotEmpty()) {
            sb.appendLine("RESULT: VALID (with warnings)")
        } else {
            sb.appendLine("RESULT: INVALID")
        }
        sb.appendLine()

        val byFile = result.errors.groupBy { it.filePath }
        for ((filePath, errors) in byFile) {
            sb.appendLine("--- $filePath ---")
            for (error in errors) {
                val severity = when (error.severity) {
                    ValidationSeverity.ERROR -> "ERROR"
                    ValidationSeverity.WARNING -> "WARN "
                    ValidationSeverity.INFO -> "INFO "
                }
                val elementPart = error.element?.let { " [$it]" } ?: ""
                sb.appendLine("  $severity:$elementPart ${error.message}")
                if (error.formula != null) {
                    sb.appendLine("         formula: ${error.formula}")
                }
            }
            sb.appendLine()
        }

        return sb.toString()
    }
}
