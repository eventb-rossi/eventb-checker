package com.eventb.checker.validation

import com.eventb.checker.model.ProofStatusSummary

enum class ValidationSeverity { ERROR, WARNING, INFO }

data class ValidationError(
    val filePath: String,
    val severity: ValidationSeverity,
    val message: String,
    val element: String? = null,
    val formula: String? = null,
    val ruleId: String? = null,
)

data class ValidationSummary(
    val machineCount: Int,
    val contextCount: Int,
    val formulaCount: Int,
    val errorCount: Int,
    val warningCount: Int,
    val infoCount: Int = 0,
    val proofSummary: ProofStatusSummary? = null,
)

data class ValidationResult(val errors: List<ValidationError>, val summary: ValidationSummary) {
    val isValid: Boolean get() = errors.none { it.severity == ValidationSeverity.ERROR }

    fun withoutInfo(): ValidationResult {
        val filteredErrors = errors.filter { it.severity != ValidationSeverity.INFO }
        return ValidationResult(
            errors = filteredErrors,
            summary = summary.copy(
                errorCount = filteredErrors.count { it.severity == ValidationSeverity.ERROR },
                warningCount = filteredErrors.count { it.severity == ValidationSeverity.WARNING },
                infoCount = 0,
            ),
        )
    }
}
