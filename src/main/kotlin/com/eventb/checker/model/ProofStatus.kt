package com.eventb.checker.model

enum class ProofConfidence { DISCHARGED, REVIEWED, PENDING, UNATTEMPTED }

data class ProofObligation(
    val name: String,
    val component: String,
    val description: String?,
    val confidence: ProofConfidence,
    val manual: Boolean,
    val broken: Boolean,
)

data class ProofStatusSummary(
    val total: Int,
    val discharged: Int,
    val reviewed: Int,
    val pending: Int,
    val unattempted: Int,
    val broken: Int,
    val manualDischarged: Int,
) {
    /** Field-wise sum, used to merge per-project summaries from a multi-project archive. */
    operator fun plus(other: ProofStatusSummary) = ProofStatusSummary(
        total = total + other.total,
        discharged = discharged + other.discharged,
        reviewed = reviewed + other.reviewed,
        pending = pending + other.pending,
        unattempted = unattempted + other.unattempted,
        broken = broken + other.broken,
        manualDischarged = manualDischarged + other.manualDischarged,
    )
}
