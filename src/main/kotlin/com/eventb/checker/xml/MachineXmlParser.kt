package com.eventb.checker.xml

import com.eventb.checker.model.Action
import com.eventb.checker.model.Convergence
import com.eventb.checker.model.Event
import com.eventb.checker.model.Guard
import com.eventb.checker.model.Invariant
import com.eventb.checker.model.Machine
import com.eventb.checker.model.Parameter
import com.eventb.checker.model.Variable
import com.eventb.checker.model.Variant
import com.eventb.checker.model.Witness
import com.eventb.checker.validation.ValidationError
import com.eventb.checker.validation.ValidationRules
import com.eventb.checker.validation.ValidationSeverity
import org.w3c.dom.Document
import org.w3c.dom.Element

class MachineXmlParser {

    data class ParseResult(val machine: Machine, val errors: List<ValidationError>)

    fun parse(doc: Document, filePath: String): ParseResult {
        val errors = mutableListOf<ValidationError>()
        val root = doc.documentElement

        if (root.tagName != XmlConstants.MACHINE_FILE) {
            errors.add(
                ValidationError(
                    filePath = filePath,
                    severity = ValidationSeverity.ERROR,
                    message = "Expected root element '${XmlConstants.MACHINE_FILE}' but found '${root.tagName}'",
                    ruleId = ValidationRules.UNEXPECTED_XML_ROOT.id,
                ),
            )
        }

        val machineName = root.getAttribute(XmlConstants.ATTR_NAME).ifEmpty {
            filePath.substringAfterLast("/").removeSuffix(XmlConstants.EXT_MACHINE)
        }

        val variables = mutableListOf<Variable>()
        val invariants = mutableListOf<Invariant>()
        var variant: Variant? = null
        val events = mutableListOf<Event>()
        val seesContexts = mutableListOf<String>()
        var refinesMachine: String? = null

        val children = root.childElements()
        for (child in children) {
            when (child.tagName) {
                XmlConstants.VARIABLE -> {
                    val id = child.getAttrOrError(XmlConstants.ATTR_IDENTIFIER, filePath, errors)
                    val label = child.getAttribute(XmlConstants.ATTR_LABEL).ifEmpty { id }
                    if (id.isNotEmpty()) variables.add(Variable(id, label))
                }
                XmlConstants.INVARIANT -> {
                    val label = child.getAttribute(XmlConstants.ATTR_LABEL)
                    val predicate = child.getAttrOrError(XmlConstants.ATTR_PREDICATE, filePath, errors)
                    val theorem = child.boolAttribute(XmlConstants.ATTR_THEOREM)
                    if (predicate.isNotEmpty()) invariants.add(Invariant(label, predicate, theorem))
                }
                XmlConstants.VARIANT -> {
                    val label = child.getAttribute(XmlConstants.ATTR_LABEL)
                    val expression = child.getAttrOrError(XmlConstants.ATTR_EXPRESSION, filePath, errors)
                    if (expression.isNotEmpty()) variant = Variant(label, expression)
                }
                XmlConstants.EVENT -> {
                    val event = parseEvent(child, filePath, errors)
                    events.add(event)
                }
                XmlConstants.SEES_CONTEXT -> {
                    val target = child.getAttrOrError(XmlConstants.ATTR_TARGET, filePath, errors)
                    if (target.isNotEmpty()) seesContexts.add(target)
                }
                XmlConstants.REFINES_MACHINE -> {
                    val target = child.getAttrOrError(XmlConstants.ATTR_TARGET, filePath, errors)
                    if (target.isNotEmpty()) refinesMachine = target
                }
            }
        }

        val machine = Machine(
            name = machineName,
            filePath = filePath,
            seesContexts = seesContexts,
            refinesMachine = refinesMachine,
            variables = variables,
            invariants = invariants,
            variant = variant,
            events = events,
        )
        return ParseResult(machine, errors)
    }

    private fun parseEvent(element: Element, filePath: String, errors: MutableList<ValidationError>): Event {
        val label = element.getAttribute(XmlConstants.ATTR_LABEL).ifEmpty {
            element.getAttribute(XmlConstants.ATTR_NAME)
        }
        val convergence = Convergence.fromCode(element.intAttribute(XmlConstants.ATTR_CONVERGENCE) ?: 0)
        val extended = element.boolAttribute(XmlConstants.ATTR_EXTENDED)

        val refinesEvents = mutableListOf<String>()
        val parameters = mutableListOf<Parameter>()
        val guards = mutableListOf<Guard>()
        val actions = mutableListOf<Action>()
        val witnesses = mutableListOf<Witness>()

        for (child in element.childElements()) {
            when (child.tagName) {
                XmlConstants.REFINES_EVENT -> {
                    val target = child.getAttrOrError(XmlConstants.ATTR_TARGET, filePath, errors)
                    if (target.isNotEmpty()) refinesEvents.add(target)
                }
                XmlConstants.PARAMETER -> {
                    val id = child.getAttrOrError(XmlConstants.ATTR_IDENTIFIER, filePath, errors)
                    val paramLabel = child.getAttribute(XmlConstants.ATTR_LABEL).ifEmpty { id }
                    if (id.isNotEmpty()) parameters.add(Parameter(id, paramLabel))
                }
                XmlConstants.GUARD -> {
                    val guardLabel = child.getAttribute(XmlConstants.ATTR_LABEL)
                    val predicate = child.getAttrOrError(XmlConstants.ATTR_PREDICATE, filePath, errors)
                    val theorem = child.boolAttribute(XmlConstants.ATTR_THEOREM)
                    if (predicate.isNotEmpty()) guards.add(Guard(guardLabel, predicate, theorem))
                }
                XmlConstants.ACTION -> {
                    val actionLabel = child.getAttribute(XmlConstants.ATTR_LABEL)
                    val assignment = child.getAttrOrError(XmlConstants.ATTR_ASSIGNMENT, filePath, errors)
                    if (assignment.isNotEmpty()) actions.add(Action(actionLabel, assignment))
                }
                XmlConstants.WITNESS -> {
                    val witnessLabel = child.getAttribute(XmlConstants.ATTR_LABEL)
                    val predicate = child.getAttrOrError(XmlConstants.ATTR_PREDICATE, filePath, errors)
                    if (predicate.isNotEmpty()) witnesses.add(Witness(witnessLabel, predicate))
                }
            }
        }

        return Event(label, convergence, extended, refinesEvents, parameters, guards, actions, witnesses)
    }
}
