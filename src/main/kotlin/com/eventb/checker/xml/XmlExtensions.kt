package com.eventb.checker.xml

import com.eventb.checker.validation.ValidationError
import com.eventb.checker.validation.ValidationRules
import com.eventb.checker.validation.ValidationSeverity
import org.w3c.dom.Document
import org.w3c.dom.Element
import java.io.InputStream
import javax.xml.parsers.DocumentBuilder
import javax.xml.parsers.DocumentBuilderFactory

internal fun createSecureDocumentBuilder(): DocumentBuilder {
    val factory = DocumentBuilderFactory.newInstance().apply {
        setFeature("http://apache.org/xml/features/disallow-doctype-decl", true)
        setFeature("http://xml.org/sax/features/external-general-entities", false)
        setFeature("http://xml.org/sax/features/external-parameter-entities", false)
        // JDK 24+ defaults jdk.xml.maxElementDepth to 100, which large Rodin proof
        // trees exceed; 0 lifts the limit (inputs are local files, DOCTYPE is disabled).
        setAttribute("jdk.xml.maxElementDepth", 0)
    }
    return factory.newDocumentBuilder()
}

internal fun parseXmlDocument(input: InputStream): Pair<Document?, String?> = try {
    createSecureDocumentBuilder().parse(input) to null
} catch (e: Exception) {
    null to e.message
}

internal fun Element.childElements(): List<Element> {
    val result = mutableListOf<Element>()
    val nodes = childNodes
    for (i in 0 until nodes.length) {
        val node = nodes.item(i)
        if (node is Element) result.add(node)
    }
    return result
}

internal fun Element.intAttribute(attrName: String): Int? = getAttribute(attrName).toIntOrNull()

internal fun Element.boolAttribute(attrName: String): Boolean = getAttribute(attrName) == "true"

internal fun Element.getAttrOrError(attrName: String, filePath: String, errors: MutableList<ValidationError>): String {
    val value = getAttribute(attrName)
    if (value.isEmpty()) {
        errors.add(
            ValidationError(
                filePath = filePath,
                severity = ValidationSeverity.WARNING,
                message = "Element '$tagName' missing required attribute '$attrName'",
                element = getAttribute(XmlConstants.ATTR_LABEL).ifEmpty {
                    getAttribute(XmlConstants.ATTR_NAME)
                },
                ruleId = ValidationRules.MISSING_XML_ATTRIBUTE.id,
            ),
        )
    }
    return value
}
