package com.eventb.checker.validation

data class RuleDescriptor(val id: String, val shortDescription: String, val fullDescription: String)

object ValidationRules {

    val XML_PARSE_ERROR = RuleDescriptor(
        "EB001",
        "XML parse error",
        "The Rodin XML file (.bum/.buc) is not well-formed XML and could not be parsed.",
    )

    val UNEXPECTED_XML_ROOT = RuleDescriptor(
        "EB002",
        "Unexpected XML root element",
        "The root element of the XML file does not match the expected Rodin element for its file type.",
    )

    val MISSING_XML_ATTRIBUTE = RuleDescriptor(
        "EB003",
        "Missing required XML attribute",
        "A Rodin XML element is missing a required attribute (e.g. identifier, predicate, expression).",
    )

    val CAMILLE_PARSE_ERROR = RuleDescriptor(
        "EB004",
        "Camille parse error",
        "The .eventb file could not be parsed using the Camille textual notation grammar.",
    )

    val FORMULA_PARSE_ERROR = RuleDescriptor(
        "EB005",
        "Formula parse error",
        "A predicate, expression, or assignment formula has invalid syntax according to the Rodin AST grammar.",
    )

    val TYPE_ERROR = RuleDescriptor(
        "EB006",
        "Type error",
        "A formula failed type checking against the inferred type environment.",
    )

    val CIRCULAR_EXTENDS = RuleDescriptor(
        "EB007",
        "Circular EXTENDS dependency",
        "A context EXTENDS chain forms a cycle, which is not allowed in Event-B.",
    )

    val CIRCULAR_REFINES = RuleDescriptor(
        "EB008",
        "Circular REFINES dependency",
        "A machine REFINES chain forms a cycle, which is not allowed in Event-B.",
    )

    val CROSS_REFERENCE_NOT_FOUND = RuleDescriptor(
        "EB009",
        "Cross-reference target not found",
        "A SEES, REFINES, or EXTENDS clause references a machine or context that does not exist in the project.",
    )

    val WELL_DEFINEDNESS = RuleDescriptor(
        "EB010",
        "Well-definedness condition",
        "A formula has a non-trivial well-definedness condition (e.g. division by zero, function application domain).",
    )

    val DEAD_VARIABLE = RuleDescriptor(
        "EB011",
        "Dead variable",
        "A machine variable is declared but never referenced in any invariant, guard, action, or witness formula.",
    )

    val UNMODIFIED_VARIABLE = RuleDescriptor(
        "EB012",
        "Unmodified variable",
        "A machine variable is referenced in formulas but never assigned by any event action.",
    )

    val DEAD_CONSTANT = RuleDescriptor(
        "EB013",
        "Dead constant",
        "A context constant is declared but never referenced in any axiom formula.",
    )

    val INCOMPLETE_INITIALISATION = RuleDescriptor(
        "EB014",
        "Incomplete INITIALISATION",
        "The INITIALISATION event does not assign all declared machine variables.",
    )

    val UNDISCHARGED_PROOF = RuleDescriptor(
        "EB015",
        "Undischarged proof obligation",
        "A proof obligation has not been fully discharged (it is pending, reviewed, or unattempted).",
    )

    val BROKEN_PROOF = RuleDescriptor(
        "EB016",
        "Broken proof",
        "A proof obligation is marked as broken, meaning its proof script is no longer valid.",
    )

    val PROOF_FILE_PARSE_ERROR = RuleDescriptor(
        "EB017",
        "Proof file parse error",
        "A proof-related file (.bpr/.bpo/.bps) could not be parsed as XML.",
    )

    val DUPLICATE_COMPONENT = RuleDescriptor(
        "EB019",
        "Duplicate component definition",
        "Multiple files define the same machine or context, so lower-priority definitions are ignored.",
    )

    val ALL: List<RuleDescriptor> = listOf(
        XML_PARSE_ERROR, UNEXPECTED_XML_ROOT, MISSING_XML_ATTRIBUTE,
        CAMILLE_PARSE_ERROR, FORMULA_PARSE_ERROR,
        TYPE_ERROR, CIRCULAR_EXTENDS, CIRCULAR_REFINES,
        CROSS_REFERENCE_NOT_FOUND, WELL_DEFINEDNESS,
        DEAD_VARIABLE, UNMODIFIED_VARIABLE, DEAD_CONSTANT,
        INCOMPLETE_INITIALISATION,
        UNDISCHARGED_PROOF, BROKEN_PROOF, PROOF_FILE_PARSE_ERROR, DUPLICATE_COMPONENT,
    )
}
