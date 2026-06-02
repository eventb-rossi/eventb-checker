package com.eventb.checker.validation

/**
 * Inferred types of the declared identifiers of a project, as produced by
 * [TypeChecker.dumpTypes]. Only identifiers Rodin could actually type appear;
 * an identifier whose type variable stays unsolved is omitted (it has no entry).
 */
data class TypeDump(
    /** context name -> (constant name -> canonical type string). */
    val contexts: Map<String, Map<String, String>>,
    /** machine name -> its variables and per-event parameters. */
    val machines: Map<String, MachineTypeDump>,
)

data class MachineTypeDump(
    /** variable name -> canonical type string. */
    val variables: Map<String, String>,
    /** event label -> (parameter name -> canonical type string). */
    val events: Map<String, Map<String, String>>,
)
