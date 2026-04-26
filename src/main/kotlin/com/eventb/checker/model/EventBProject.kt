package com.eventb.checker.model

data class EventBProject(val name: String, val machines: List<Machine>, val contexts: List<Context>)

data class Machine(
    val name: String,
    val filePath: String,
    val seesContexts: List<String>,
    val refinesMachine: String?,
    val variables: List<Variable>,
    val invariants: List<Invariant>,
    val variant: Variant?,
    val events: List<Event>,
)

data class Context(
    val name: String,
    val filePath: String,
    val extendsContexts: List<String>,
    val carrierSets: List<CarrierSet>,
    val constants: List<Constant>,
    val axioms: List<Axiom>,
)

enum class Convergence(val code: Int) {
    ORDINARY(0),
    CONVERGENT(1),
    ANTICIPATED(2),
    ;

    companion object {
        fun fromCode(code: Int): Convergence = entries.find { it.code == code } ?: ORDINARY
    }
}

data class Event(
    val label: String,
    val convergence: Convergence,
    val extended: Boolean,
    val refinesEvents: List<String>,
    val parameters: List<Parameter>,
    val guards: List<Guard>,
    val actions: List<Action>,
    val witnesses: List<Witness>,
)

data class Variable(val identifier: String, val label: String)
data class Invariant(val label: String, val predicate: String, val theorem: Boolean)
data class Variant(val label: String, val expression: String)
data class Guard(val label: String, val predicate: String, val theorem: Boolean)
data class Action(val label: String, val assignment: String)
data class Witness(val label: String, val predicate: String)
data class Parameter(val identifier: String, val label: String)
data class CarrierSet(val identifier: String, val label: String)
data class Constant(val identifier: String, val label: String)
data class Axiom(val label: String, val predicate: String, val theorem: Boolean)
