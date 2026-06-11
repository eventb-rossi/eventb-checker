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
)
