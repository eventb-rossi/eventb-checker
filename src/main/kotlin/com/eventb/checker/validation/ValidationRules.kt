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
        "A formula has a definite type conflict (e.g. mismatched operand types) against the inferred type environment.",
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
        "A machine variable that nothing references outside typing-shaped invariants (e.g. 'v ∈ ℤ') and " +
            "that no event assigns: it serves no purpose. A write-only variable (assigned but never read) " +
            "is exempt, as it is an output.",
    )

    val UNMODIFIED_VARIABLE = RuleDescriptor(
        "EB012",
        "Unmodified variable",
        "A machine variable that INITIALISATION assigns but no event ever modifies, yet is referenced — " +
            "a constant in disguise. Consider declaring it a CONSTANT with the initial value as an axiom.",
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

    val UNDECLARED_IDENTIFIER = RuleDescriptor(
        "EB018",
        "Undeclared identifier",
        "A formula references an identifier that is not declared in the surrounding Event-B scope.",
    )

    val DUPLICATE_COMPONENT = RuleDescriptor(
        "EB019",
        "Duplicate component definition",
        "Multiple files define the same machine or context, so lower-priority definitions are ignored.",
    )

    val UNKNOWN_TYPE = RuleDescriptor(
        "EB020",
        "Unknown type",
        "An identifier's type could not be inferred from the available declarations; this often reflects " +
            "constructs the checker does not fully model (e.g. primed witness variables) rather than a model defect.",
    )

    val DUPLICATE_IDENTIFIER = RuleDescriptor(
        "EB021",
        "Duplicate identifier",
        "An identifier (variable, constant, carrier set, or event parameter) is declared more than once " +
            "within the same scope.",
    )

    val DUPLICATE_LABEL = RuleDescriptor(
        "EB022",
        "Duplicate label",
        "A label (invariant, event, guard, action, axiom, or witness) is used more than once within the same scope.",
    )

    val SHADOWED_NAME = RuleDescriptor(
        "EB023",
        "Shadowed name",
        "A declared identifier (variable, constant, carrier set, or event parameter) has a spelling that " +
            "collides with a reserved Event-B operator or keyword token, so it cannot be used as an identifier.",
    )

    val NEW_EVENT_ASSIGNS_INHERITED_VARIABLE = RuleDescriptor(
        "EB024",
        "New event assigns inherited variable",
        "A new event (one that does not refine an abstract event and is not extended) assigns a variable that is " +
            "inherited from an abstract machine and retained in this refinement. A new event refines skip and must " +
            "not modify inherited state.",
    )

    val DISAPPEARED_VARIABLE = RuleDescriptor(
        "EB025",
        "Disappeared variable",
        "A guard or action references a variable that was declared in an abstract machine but dropped in this " +
            "refinement (data-refined away), so it is no longer in scope here.",
    )

    val ASSIGNMENT_IN_PREDICATE = RuleDescriptor(
        "EB026",
        "Assignment operator in predicate",
        "An invariant, guard, witness, or axiom uses an assignment operator (':=', ':∈', or ':|') where a " +
            "predicate is required; the intended operator is most likely '=' for equality.",
    )

    val ALL: List<RuleDescriptor> = listOf(
        XML_PARSE_ERROR, UNEXPECTED_XML_ROOT, MISSING_XML_ATTRIBUTE,
        CAMILLE_PARSE_ERROR, FORMULA_PARSE_ERROR,
        TYPE_ERROR, CIRCULAR_EXTENDS, CIRCULAR_REFINES,
        CROSS_REFERENCE_NOT_FOUND, WELL_DEFINEDNESS,
        DEAD_VARIABLE, UNMODIFIED_VARIABLE, DEAD_CONSTANT,
        INCOMPLETE_INITIALISATION,
        UNDISCHARGED_PROOF, BROKEN_PROOF, PROOF_FILE_PARSE_ERROR,
        UNDECLARED_IDENTIFIER, DUPLICATE_COMPONENT, UNKNOWN_TYPE,
        DUPLICATE_IDENTIFIER, DUPLICATE_LABEL,
        SHADOWED_NAME, NEW_EVENT_ASSIGNS_INHERITED_VARIABLE,
        DISAPPEARED_VARIABLE, ASSIGNMENT_IN_PREDICATE,
    )
}
