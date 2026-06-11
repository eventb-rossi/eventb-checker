package com.eventb.checker.validation

import com.eventb.checker.ModelContents
import com.eventb.checker.ModelEntry
import com.eventb.checker.model.ProofConfidence
import com.eventb.checker.model.ProofObligation
import com.eventb.checker.model.ProofStatusSummary
import com.eventb.checker.xml.XmlConstants
import com.eventb.checker.xml.boolAttribute
import com.eventb.checker.xml.childElements
import com.eventb.checker.xml.intAttribute
import com.eventb.checker.xml.parseXmlDocument

data class ProofStatusResult(val obligations: List<ProofObligation>, val summary: ProofStatusSummary, val errors: List<ValidationError>)

class ProofStatusChecker {

    fun check(contents: ModelContents): ProofStatusResult {
        val errors = mutableListOf<ValidationError>()

        val prData = parseProofFiles(contents.proofFiles, errors)
        val poData = parsePoFiles(contents.proofObligationFiles, errors)
        val psData = parsePsFiles(contents.proofStatusFiles, errors)

        val obligations = buildObligations(prData, poData, psData)

        val summary = ProofStatusSummary(
            total = obligations.size,
            discharged = obligations.count { it.confidence == ProofConfidence.DISCHARGED },
            reviewed = obligations.count { it.confidence == ProofConfidence.REVIEWED },
            pending = obligations.count { it.confidence == ProofConfidence.PENDING },
            unattempted = obligations.count { it.confidence == ProofConfidence.UNATTEMPTED },
            broken = obligations.count { it.broken },
            manualDischarged = obligations.count { it.confidence == ProofConfidence.DISCHARGED && it.manual },
        )

        // Broken POs are reported once, by the broken-proof warning below.
        val undischarged = obligations.filter { it.confidence != ProofConfidence.DISCHARGED && !it.broken }
        for (po in undischarged) {
            errors.add(
                ValidationError(
                    filePath = po.component,
                    severity = ValidationSeverity.WARNING,
                    message = "Proof obligation not discharged: ${po.name} (${po.confidence.name.lowercase()})",
                    element = po.name,
                    ruleId = ValidationRules.UNDISCHARGED_PROOF.id,
                ),
            )
        }

        val brokenProofs = obligations.filter { it.broken }
        for (po in brokenProofs) {
            errors.add(
                ValidationError(
                    filePath = po.component,
                    severity = ValidationSeverity.WARNING,
                    message = "Broken proof: ${po.name}",
                    element = po.name,
                    ruleId = ValidationRules.BROKEN_PROOF.id,
                ),
            )
        }

        return ProofStatusResult(obligations, summary, errors)
    }

    private data class PrEntry(val component: String, val name: String, val confidence: Int?)

    private data class PoEntry(val component: String, val name: String, val description: String?)

    private data class PsEntry(val component: String, val name: String, val confidence: Int?, val manual: Boolean, val broken: Boolean)

    private fun <T> parseXmlEntries(
        files: List<ModelEntry>,
        suffix: String,
        tag: String,
        errors: MutableList<ValidationError>,
        extract: (component: String, child: org.w3c.dom.Element) -> T?,
    ): List<T> {
        val result = mutableListOf<T>()
        for (entry in files) {
            val component = entry.path.substringAfterLast('/').removeSuffix(suffix)
            val doc = parseXml(entry, errors) ?: continue
            for (child in doc.documentElement.childElements()) {
                if (child.tagName == tag) {
                    extract(component, child)?.let { result.add(it) }
                }
            }
        }
        return result
    }

    private fun parseProofFiles(files: List<ModelEntry>, errors: MutableList<ValidationError>): List<PrEntry> =
        parseXmlEntries(files, XmlConstants.EXT_PROOF, XmlConstants.PR_PROOF, errors) { component, child ->
            val name = child.getAttribute(XmlConstants.ATTR_NAME).ifEmpty { return@parseXmlEntries null }
            PrEntry(component, name, child.intAttribute(XmlConstants.ATTR_CONFIDENCE))
        }

    private fun parsePoFiles(files: List<ModelEntry>, errors: MutableList<ValidationError>): List<PoEntry> =
        parseXmlEntries(files, XmlConstants.EXT_PROOF_OBLIGATIONS, XmlConstants.PO_SEQUENT, errors) { component, child ->
            val name = child.getAttribute(XmlConstants.ATTR_NAME).ifEmpty { return@parseXmlEntries null }
            PoEntry(component, name, child.getAttribute(XmlConstants.ATTR_PO_DESC).ifEmpty { null })
        }

    private fun parsePsFiles(files: List<ModelEntry>, errors: MutableList<ValidationError>): List<PsEntry> =
        parseXmlEntries(files, XmlConstants.EXT_PROOF_STATUS, XmlConstants.PS_STATUS, errors) { component, child ->
            val name = child.getAttribute(XmlConstants.ATTR_NAME).ifEmpty { return@parseXmlEntries null }
            PsEntry(
                component,
                name,
                child.intAttribute(XmlConstants.ATTR_CONFIDENCE),
                child.boolAttribute(XmlConstants.ATTR_PS_MANUAL),
                child.boolAttribute(XmlConstants.ATTR_PS_BROKEN),
            )
        }

    private fun buildObligations(prData: List<PrEntry>, poData: List<PoEntry>, psData: List<PsEntry>): List<ProofObligation> {
        val prMap = prData.associateBy { poKey(it.component, it.name) }
        val psMap = psData.associateBy { poKey(it.component, it.name) }

        data class ObligationSource(val name: String, val component: String, val description: String?)

        // The PO list comes from .bpo when present, then from the .bps status entries
        // (one per live PO), and only as a last resort from the stored .bpr proof trees,
        // which may include stale proofs for obligations that no longer exist.
        val sources = when {
            poData.isNotEmpty() -> poData.map { ObligationSource(it.name, it.component, it.description) }
            psData.isNotEmpty() -> psData.map { ObligationSource(it.name, it.component, null) }
            else -> prData.map { ObligationSource(it.name, it.component, null) }
        }

        return sources.map { src ->
            val key = poKey(src.component, src.name)
            val ps = psMap[key]
            // The .bps status reflects whether the proof replays against the current PO;
            // the confidence stored in .bpr belongs to the (possibly stale) proof tree.
            // Rodin serializes UNATTEMPTED as an absent confidence attribute, so a present
            // status entry without one means unattempted, not "use the proof tree".
            val confidence = if (ps != null) ps.confidence else prMap[key]?.confidence
            val broken = ps?.broken ?: false
            ProofObligation(
                name = src.name,
                component = src.component,
                description = src.description,
                confidence = classifyConfidence(confidence, broken),
                manual = ps?.manual ?: false,
                broken = broken,
            )
        }
    }

    private fun poKey(component: String, name: String) = "$component/$name"

    private fun classifyConfidence(confidence: Int?, broken: Boolean): ProofConfidence {
        val classified = when {
            confidence == null -> ProofConfidence.UNATTEMPTED
            confidence > CONFIDENCE_DISCHARGED -> ProofConfidence.DISCHARGED
            confidence >= CONFIDENCE_REVIEWED_MIN -> ProofConfidence.REVIEWED
            confidence >= 0 -> ProofConfidence.PENDING
            else -> ProofConfidence.UNATTEMPTED
        }
        // A broken proof no longer replays against the current PO, so a closed
        // (discharged or reviewed) confidence does not count; Rodin shows these as pending.
        val closed = classified == ProofConfidence.DISCHARGED || classified == ProofConfidence.REVIEWED
        return if (broken && closed) ProofConfidence.PENDING else classified
    }

    companion object {
        private const val CONFIDENCE_DISCHARGED = 500
        private const val CONFIDENCE_REVIEWED_MIN = 101
    }

    private fun parseXml(entry: ModelEntry, errors: MutableList<ValidationError>): org.w3c.dom.Document? {
        val (doc, errorDetail) = parseXmlDocument(entry.inputStream())
        if (doc == null) {
            errors.add(
                ValidationError(
                    filePath = entry.path,
                    severity = ValidationSeverity.WARNING,
                    message = if (errorDetail != null) "Failed to parse proof file: $errorDetail" else "Failed to parse proof file",
                    ruleId = ValidationRules.PROOF_FILE_PARSE_ERROR.id,
                ),
            )
        }
        return doc
    }
}
