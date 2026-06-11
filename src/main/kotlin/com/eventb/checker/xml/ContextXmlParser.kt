package com.eventb.checker.xml

import com.eventb.checker.model.Axiom
import com.eventb.checker.model.CarrierSet
import com.eventb.checker.model.Constant
import com.eventb.checker.model.Context
import com.eventb.checker.validation.ValidationError
import com.eventb.checker.validation.ValidationRules
import com.eventb.checker.validation.ValidationSeverity
import org.w3c.dom.Document

class ContextXmlParser {

    data class ParseResult(val context: Context, val errors: List<ValidationError>)

    fun parse(doc: Document, filePath: String): ParseResult {
        val errors = mutableListOf<ValidationError>()
        val root = doc.documentElement

        if (root.tagName != XmlConstants.CONTEXT_FILE) {
            errors.add(
                ValidationError(
                    filePath = filePath,
                    severity = ValidationSeverity.ERROR,
                    message = "Expected root element '${XmlConstants.CONTEXT_FILE}' but found '${root.tagName}'",
                    ruleId = ValidationRules.UNEXPECTED_XML_ROOT.id,
                ),
            )
        }

        val contextName = root.getAttribute(XmlConstants.ATTR_NAME).ifEmpty {
            filePath.substringAfterLast("/").removeSuffix(XmlConstants.EXT_CONTEXT)
        }

        val extendsContexts = mutableListOf<String>()
        val carrierSets = mutableListOf<CarrierSet>()
        val constants = mutableListOf<Constant>()
        val axioms = mutableListOf<Axiom>()

        for (child in root.childElements()) {
            when (child.tagName) {
                XmlConstants.EXTENDS_CONTEXT -> {
                    val target = child.getAttrOrError(XmlConstants.ATTR_TARGET, filePath, errors)
                    if (target.isNotEmpty()) extendsContexts.add(target)
                }
                XmlConstants.CARRIER_SET -> {
                    val id = child.getAttrOrError(XmlConstants.ATTR_IDENTIFIER, filePath, errors)
                    val label = child.getAttribute(XmlConstants.ATTR_LABEL).ifEmpty { id }
                    if (id.isNotEmpty()) carrierSets.add(CarrierSet(id, label))
                }
                XmlConstants.CONSTANT -> {
                    val id = child.getAttrOrError(XmlConstants.ATTR_IDENTIFIER, filePath, errors)
                    val label = child.getAttribute(XmlConstants.ATTR_LABEL).ifEmpty { id }
                    if (id.isNotEmpty()) constants.add(Constant(id, label))
                }
                XmlConstants.AXIOM -> {
                    val label = child.getAttribute(XmlConstants.ATTR_LABEL)
                    val predicate = child.getAttrOrError(XmlConstants.ATTR_PREDICATE, filePath, errors)
                    val theorem = child.boolAttribute(XmlConstants.ATTR_THEOREM)
                    if (predicate.isNotEmpty()) axioms.add(Axiom(label, predicate, theorem))
                }
            }
        }

        val context = Context(
            name = contextName,
            filePath = filePath,
            extendsContexts = extendsContexts,
            carrierSets = carrierSets,
            constants = constants,
            axioms = axioms,
        )
        return ParseResult(context, errors)
    }
}
