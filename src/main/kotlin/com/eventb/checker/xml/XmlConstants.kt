package com.eventb.checker.xml

object XmlConstants {
    // Root elements
    const val MACHINE_FILE = "org.eventb.core.machineFile"
    const val CONTEXT_FILE = "org.eventb.core.contextFile"

    // Machine child elements
    const val VARIABLE = "org.eventb.core.variable"
    const val INVARIANT = "org.eventb.core.invariant"
    const val VARIANT = "org.eventb.core.variant"
    const val EVENT = "org.eventb.core.event"
    const val SEES_CONTEXT = "org.eventb.core.seesContext"
    const val REFINES_MACHINE = "org.eventb.core.refinesMachine"

    // Event child elements
    const val GUARD = "org.eventb.core.guard"
    const val ACTION = "org.eventb.core.action"
    const val WITNESS = "org.eventb.core.witness"
    const val PARAMETER = "org.eventb.core.parameter"
    const val REFINES_EVENT = "org.eventb.core.refinesEvent"

    // Context child elements
    const val CARRIER_SET = "org.eventb.core.carrierSet"
    const val CONSTANT = "org.eventb.core.constant"
    const val AXIOM = "org.eventb.core.axiom"
    const val EXTENDS_CONTEXT = "org.eventb.core.extendsContext"

    // Attributes
    const val ATTR_IDENTIFIER = "org.eventb.core.identifier"
    const val ATTR_PREDICATE = "org.eventb.core.predicate"
    const val ATTR_EXPRESSION = "org.eventb.core.expression"
    const val ATTR_ASSIGNMENT = "org.eventb.core.assignment"
    const val ATTR_TARGET = "org.eventb.core.target"
    const val ATTR_LABEL = "org.eventb.core.label"
    const val ATTR_CONVERGENCE = "org.eventb.core.convergence"
    const val ATTR_EXTENDED = "org.eventb.core.extended"
    const val ATTR_THEOREM = "org.eventb.core.theorem"

    // Rodin internal attribute
    const val ATTR_NAME = "name"

    // Proof file elements
    const val PR_FILE = "org.eventb.core.prFile"
    const val PR_PROOF = "org.eventb.core.prProof"
    const val PO_FILE = "org.eventb.core.poFile"
    const val PO_SEQUENT = "org.eventb.core.poSequent"
    const val PS_FILE = "org.eventb.core.psFile"
    const val PS_STATUS = "org.eventb.core.psStatus"

    // Proof attributes
    const val ATTR_CONFIDENCE = "org.eventb.core.confidence"
    const val ATTR_PO_DESC = "org.eventb.core.poDesc"
    const val ATTR_PS_MANUAL = "org.eventb.core.psManual"
    const val ATTR_PS_BROKEN = "org.eventb.core.psBroken"

    // File extensions
    const val EXT_MACHINE = ".bum"
    const val EXT_CONTEXT = ".buc"
    const val EXT_PROOF = ".bpr"
    const val EXT_PROOF_OBLIGATIONS = ".bpo"
    const val EXT_PROOF_STATUS = ".bps"

    // Eclipse/Rodin project descriptor
    const val EXT_PROJECT = ".project"
}
