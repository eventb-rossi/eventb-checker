package com.eventb.checker

import com.eventb.checker.model.Action
import com.eventb.checker.model.Axiom
import com.eventb.checker.model.CarrierSet
import com.eventb.checker.model.Constant
import com.eventb.checker.model.Context
import com.eventb.checker.model.Convergence
import com.eventb.checker.model.Event
import com.eventb.checker.model.EventBProject
import com.eventb.checker.model.Guard
import com.eventb.checker.model.Invariant
import com.eventb.checker.model.Machine
import com.eventb.checker.model.Parameter
import com.eventb.checker.model.Variable
import com.eventb.checker.model.Variant
import com.eventb.checker.model.Witness
import com.eventb.checker.validation.MachineInheritance
import com.eventb.checker.validation.MachineInheritanceResolver
import com.eventb.checker.validation.ParsedFormula
import com.eventb.checker.validation.TypeCheckedFormula
import com.eventb.checker.validation.TypeChecker

object TestModelBuilders {

    fun project(machines: List<Machine> = emptyList(), contexts: List<Context> = emptyList()) = EventBProject(
        name = "test",
        machines = machines,
        contexts = contexts,
    )

    fun machine(
        name: String,
        seesContexts: List<String> = emptyList(),
        refinesMachine: String? = null,
        variables: List<Variable> = emptyList(),
        invariants: List<Invariant> = emptyList(),
        variant: Variant? = null,
        events: List<Event> = emptyList(),
    ) = Machine(
        name = name,
        filePath = "$name.bum",
        seesContexts = seesContexts,
        refinesMachine = refinesMachine,
        variables = variables,
        invariants = invariants,
        variant = variant,
        events = events,
    )

    fun context(
        name: String,
        extendsContexts: List<String> = emptyList(),
        carrierSets: List<CarrierSet> = emptyList(),
        constants: List<Constant> = emptyList(),
        axioms: List<Axiom> = emptyList(),
    ) = Context(
        name = name,
        filePath = "$name.buc",
        extendsContexts = extendsContexts,
        carrierSets = carrierSets,
        constants = constants,
        axioms = axioms,
    )

    fun parsedFormulas(project: EventBProject): List<ParsedFormula> = TypeChecker().checkProjectFull(project).parsedFormulas

    fun inheritance(project: EventBProject): Map<String, MachineInheritance> = MachineInheritanceResolver().resolve(project)

    fun checkedFormulas(project: EventBProject): List<TypeCheckedFormula> = TypeChecker().checkProjectFull(project).checkedFormulas

    fun event(
        label: String,
        extended: Boolean = false,
        refinesEvents: List<String> = emptyList(),
        parameters: List<Parameter> = emptyList(),
        guards: List<Guard> = emptyList(),
        actions: List<Action> = emptyList(),
        witnesses: List<Witness> = emptyList(),
    ) = Event(
        label = label,
        convergence = Convergence.ORDINARY,
        extended = extended,
        refinesEvents = refinesEvents,
        parameters = parameters,
        guards = guards,
        actions = actions,
        witnesses = witnesses,
    )
}
