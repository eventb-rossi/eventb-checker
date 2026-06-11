package com.eventb.checker.report

import com.eventb.checker.validation.ValidationResult
import org.json.JSONArray
import org.json.JSONObject

class JsonReportFormatter : ReportFormatter {

    override fun format(result: ValidationResult): String {
        val summary = JSONObject()
            .put("machineCount", result.summary.machineCount)
            .put("contextCount", result.summary.contextCount)
            .put("formulaCount", result.summary.formulaCount)
            .put("errorCount", result.summary.errorCount)
            .put("warningCount", result.summary.warningCount)
            .put("infoCount", result.summary.infoCount)

        result.summary.proofSummary?.let { ps ->
            summary.put(
                "proofSummary",
                JSONObject()
                    .put("total", ps.total)
                    .put("discharged", ps.discharged)
                    .put("reviewed", ps.reviewed)
                    .put("pending", ps.pending)
                    .put("unattempted", ps.unattempted)
                    .put("broken", ps.broken)
                    .put("manualDischarged", ps.manualDischarged),
            )
        }

        val errors = JSONArray()
        for (error in result.errors) {
            errors.put(
                JSONObject()
                    .put("file", error.filePath)
                    .put("severity", error.severity.name)
                    .put("message", error.message)
                    .put("element", error.element ?: JSONObject.NULL)
                    .put("formula", error.formula ?: JSONObject.NULL)
                    .put("ruleId", error.ruleId ?: JSONObject.NULL),
            )
        }

        return JSONObject()
            .put("valid", result.isValid)
            .put("summary", summary)
            .put("errors", errors)
            .toString()
    }
}
