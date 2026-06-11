package com.eventb.checker.validation

import com.eventb.checker.model.Context
import com.eventb.checker.model.Event
import com.eventb.checker.model.EventBProject
import com.eventb.checker.model.Machine
import org.eventb.core.ast.Assignment
import org.eventb.core.ast.Formula
import org.eventb.core.ast.FormulaFactory
import org.eventb.core.ast.IParseResult
import org.eventb.core.ast.ITypeEnvironment
import org.eventb.core.ast.ITypeEnvironmentBuilder
import org.eventb.core.ast.ProblemKind

class TypeChecker(private val ff: FormulaFactory = FormulaFactory.getDefault()) {

    private data class IdentifierScope(val identifiers: Set<String>, val primedIdentifiers: Set<String> = emptySet()) {
        fun contains(identifier: String): Boolean {
            if (identifier in identifiers) {
                return true
            }

            val baseIdentifier = identifier.removeSuffix("'")
            return baseIdentifier != identifier && baseIdentifier in primedIdentifiers
        }
    }

    private data class MachineScope(
        val contextIdentifiers: Set<String>,
        val concreteVariables: Set<String>,
        val abstractVariables: Set<String>,
    ) {
        val allVariables: Set<String> = abstractVariables + concreteVariables
        val invariantScope: IdentifierScope = IdentifierScope(contextIdentifiers + allVariables)
        val concreteScope: IdentifierScope = IdentifierScope(contextIdentifiers + concreteVariables)
    }

    private val parsePredicate: (String) -> IParseResult = { ff.parsePredicate(it, null) }
    private val extractPredicate: (IParseResult) -> Formula<*> = { it.parsedPredicate }
    private val parseAssignment: (String) -> IParseResult = { ff.parseAssignment(it, null) }
    private val extractAssignment: (IParseResult) -> Formula<*> = { it.parsedAssignment }
    private val parseExpression: (String) -> IParseResult = { ff.parseExpression(it, null) }
    private val extractExpression: (IParseResult) -> Formula<*> = { it.parsedExpression }

    /** When non-null, the env-building traversal records inferred types into it. */
    private var dump: MutableTypeDump? = null

    fun checkProject(project: EventBProject): List<ValidationError> = checkProjectFull(project).errors

    /**
     * Type-check `project` purely to collect the inferred types of its declared
     * constants, variables, and event parameters. Reuses the same environment
     * construction as [checkProjectFull], so the types match Rodin exactly; an
     * identifier Rodin leaves untyped is simply absent.
     */
    fun dumpTypes(project: EventBProject): TypeDump {
        val sink = MutableTypeDump()
        dump = sink
        try {
            checkProjectFull(project)
        } finally {
            dump = null
        }
        return sink.build()
    }

    fun checkProjectFull(project: EventBProject): TypeCheckResult {
        val errors = mutableListOf<ValidationError>()
        val parsedFormulas = mutableListOf<ParsedFormula>()
        val checkedFormulas = mutableListOf<TypeCheckedFormula>()

        val contextsByName = project.contexts.associateBy { it.name }
        val contextEnvs = mutableMapOf<String, ITypeEnvironmentBuilder>()
        val contextDeclaredIdentifiers = mutableMapOf<String, Set<String>>()

        for (ctx in project.contexts) {
            if (ctx.name !in contextEnvs) {
                buildContextEnv(
                    ctx,
                    contextsByName,
                    contextEnvs,
                    contextDeclaredIdentifiers,
                    errors,
                    parsedFormulas,
                    checkedFormulas,
                    mutableSetOf(),
                )
            }
        }

        val machinesByName = project.machines.associateBy { it.name }
        val machineEnvs = mutableMapOf<String, ITypeEnvironmentBuilder>()
        val machineScopes = mutableMapOf<String, MachineScope>()

        for (machine in project.machines) {
            if (machine.name !in machineEnvs) {
                checkMachine(
                    machine,
                    contextEnvs,
                    contextDeclaredIdentifiers,
                    machinesByName,
                    machineEnvs,
                    machineScopes,
                    errors,
                    parsedFormulas,
                    checkedFormulas,
                    mutableSetOf(),
                )
            }
        }

        return TypeCheckResult(errors, parsedFormulas, checkedFormulas)
    }

    private fun buildContextEnv(
        ctx: Context,
        contextsByName: Map<String, Context>,
        contextEnvs: MutableMap<String, ITypeEnvironmentBuilder>,
        contextDeclaredIdentifiers: MutableMap<String, Set<String>>,
        errors: MutableList<ValidationError>,
        parsedFormulas: MutableList<ParsedFormula>,
        checkedFormulas: MutableList<TypeCheckedFormula>,
        visiting: MutableSet<String>,
    ): ITypeEnvironmentBuilder {
        contextEnvs[ctx.name]?.let { return it }

        if (!visiting.add(ctx.name)) {
            errors.add(
                ValidationError(
                    filePath = ctx.filePath,
                    severity = ValidationSeverity.WARNING,
                    message = "Circular EXTENDS dependency detected involving context '${ctx.name}'",
                    element = ctx.name,
                    ruleId = ValidationRules.CIRCULAR_EXTENDS.id,
                ),
            )
            return ff.makeTypeEnvironment()
        }

        val env = ff.makeTypeEnvironment()
        val declaredIdentifiers = linkedSetOf<String>()

        for (extName in ctx.extendsContexts) {
            val extCtx = contextsByName[extName] ?: continue
            val extEnv = buildContextEnv(
                extCtx,
                contextsByName,
                contextEnvs,
                contextDeclaredIdentifiers,
                errors,
                parsedFormulas,
                checkedFormulas,
                visiting,
            )
            env.addAll(extEnv)
            declaredIdentifiers.addAll(contextDeclaredIdentifiers[extName].orEmpty())
        }

        for (set in ctx.carrierSets) {
            env.addGivenSet(set.identifier)
            declaredIdentifiers.add(set.identifier)
        }

        for (constant in ctx.constants) {
            declaredIdentifiers.add(constant.identifier)
        }

        for (axiom in ctx.axioms) {
            typeCheckFormula(
                axiom.predicate, env, ctx.filePath, axiom.label, errors, parsedFormulas, checkedFormulas,
                FormulaKind.PREDICATE, parsePredicate, extractPredicate,
                scope = IdentifierScope(declaredIdentifiers),
            )
        }

        contextEnvs[ctx.name] = env
        contextDeclaredIdentifiers[ctx.name] = declaredIdentifiers.toSet()
        dump?.recordContext(ctx, env)
        visiting.remove(ctx.name)
        return env
    }

    private fun checkMachine(
        machine: Machine,
        contextEnvs: Map<String, ITypeEnvironmentBuilder>,
        contextDeclaredIdentifiers: Map<String, Set<String>>,
        machinesByName: Map<String, Machine>,
        machineEnvs: MutableMap<String, ITypeEnvironmentBuilder>,
        machineScopes: MutableMap<String, MachineScope>,
        errors: MutableList<ValidationError>,
        parsedFormulas: MutableList<ParsedFormula>,
        checkedFormulas: MutableList<TypeCheckedFormula>,
        visiting: MutableSet<String>,
    ) {
        if (machine.name in machineEnvs) return
        if (!visiting.add(machine.name)) {
            errors.add(
                ValidationError(
                    filePath = machine.filePath,
                    severity = ValidationSeverity.WARNING,
                    message = "Circular REFINES dependency detected involving machine '${machine.name}'",
                    element = machine.name,
                    ruleId = ValidationRules.CIRCULAR_REFINES.id,
                ),
            )
            return
        }

        val env = ff.makeTypeEnvironment()
        val contextIdentifiers = linkedSetOf<String>()
        val abstractVariables = linkedSetOf<String>()
        val concreteVariables = machine.variables.mapTo(linkedSetOf()) { it.identifier }

        for (ctxName in machine.seesContexts) {
            contextEnvs[ctxName]?.let { env.addAll(it) }
            contextIdentifiers.addAll(contextDeclaredIdentifiers[ctxName].orEmpty())
        }

        machine.refinesMachine?.let { refName ->
            if (refName !in machineEnvs) {
                val refMachine = machinesByName[refName]
                if (refMachine != null) {
                    checkMachine(
                        refMachine,
                        contextEnvs,
                        contextDeclaredIdentifiers,
                        machinesByName,
                        machineEnvs,
                        machineScopes,
                        errors,
                        parsedFormulas,
                        checkedFormulas,
                        visiting,
                    )
                }
            }
            machineEnvs[refName]?.let { env.addAll(it) }
            machineScopes[refName]?.let { refScope ->
                contextIdentifiers.addAll(refScope.contextIdentifiers)
                abstractVariables.addAll(refScope.allVariables)
            }
        }

        val machineScope = MachineScope(
            contextIdentifiers = contextIdentifiers,
            concreteVariables = concreteVariables,
            abstractVariables = abstractVariables,
        )

        for (inv in machine.invariants) {
            typeCheckFormula(
                inv.predicate, env, machine.filePath, inv.label, errors, parsedFormulas, checkedFormulas,
                FormulaKind.PREDICATE, parsePredicate, extractPredicate,
                scope = machineScope.invariantScope,
            )
        }

        machine.variant?.let { variant ->
            typeCheckFormula(
                variant.expression, env, machine.filePath, variant.label, errors, parsedFormulas, checkedFormulas,
                FormulaKind.EXPRESSION, parseExpression, extractExpression,
                scope = machineScope.concreteScope,
            )
        }

        machineEnvs[machine.name] = env
        machineScopes[machine.name] = machineScope
        dump?.recordMachineVariables(machine, env)
        visiting.remove(machine.name)

        for (event in machine.events) {
            checkEvent(
                event,
                machine,
                env,
                machine.filePath,
                machineScope,
                machinesByName,
                errors,
                parsedFormulas,
                checkedFormulas,
            )
        }
    }

    private fun checkEvent(
        event: Event,
        machine: Machine,
        machineEnv: ITypeEnvironmentBuilder,
        filePath: String,
        machineScope: MachineScope,
        machinesByName: Map<String, Machine>,
        errors: MutableList<ValidationError>,
        parsedFormulas: MutableList<ParsedFormula>,
        checkedFormulas: MutableList<TypeCheckedFormula>,
    ) {
        val eventEnv = ff.makeTypeEnvironment()
        eventEnv.addAll(machineEnv)
        val concreteParameters = effectiveEventParameterNames(machine, event, machinesByName)
        val eventScope = IdentifierScope(
            identifiers = machineScope.contextIdentifiers + machineScope.concreteVariables + concreteParameters,
            primedIdentifiers = machineScope.concreteVariables,
        )

        for (guard in event.guards) {
            typeCheckFormula(
                guard.predicate, eventEnv, filePath, "${event.label}/${guard.label}", errors, parsedFormulas, checkedFormulas,
                FormulaKind.PREDICATE, parsePredicate, extractPredicate,
                scope = eventScope,
            )
        }

        for (action in event.actions) {
            typeCheckFormula(
                action.assignment, eventEnv, filePath, "${event.label}/${action.label}", errors, parsedFormulas, checkedFormulas,
                FormulaKind.ASSIGNMENT, parseAssignment, extractAssignment,
                scope = eventScope,
            )
        }

        val abstractParameters = abstractEventParameterNames(machine, event, machinesByName)
        for (witness in event.witnesses) {
            val witnessDeclaredIdentifiers =
                machineScope.contextIdentifiers +
                    machineScope.allVariables +
                    concreteParameters +
                    validWitnessTarget(witness.label, abstractParameters, machineScope.abstractVariables)
            val witnessScope = IdentifierScope(
                identifiers = witnessDeclaredIdentifiers,
                primedIdentifiers = machineScope.concreteVariables,
            )
            typeCheckFormula(
                witness.predicate, eventEnv, filePath, "${event.label}/${witness.label}", errors, parsedFormulas, checkedFormulas,
                FormulaKind.PREDICATE, parsePredicate, extractPredicate,
                scope = witnessScope,
            )
        }

        dump?.recordEvent(machine, event, eventEnv)
    }

    private fun effectiveEventParameterNames(
        machine: Machine,
        event: Event,
        machinesByName: Map<String, Machine>,
        visiting: MutableSet<Pair<String, String>> = mutableSetOf(),
    ): Set<String> {
        val parameterNames = linkedSetOf<String>()
        val key = machine.name to event.label
        if (!visiting.add(key)) {
            return event.parameters.mapTo(linkedSetOf()) { it.identifier }
        }

        if (event.extended) {
            val refMachine = machine.refinesMachine?.let { machinesByName[it] }
            if (refMachine != null) {
                for (label in refinedEventLabels(event)) {
                    val refEvent = refMachine.events.find { it.label == label }
                    if (refEvent != null) {
                        parameterNames.addAll(effectiveEventParameterNames(refMachine, refEvent, machinesByName, visiting))
                    }
                }
            }
        }

        event.parameters.mapTo(parameterNames) { it.identifier }
        visiting.remove(key)
        return parameterNames
    }

    private fun abstractEventParameterNames(machine: Machine, event: Event, machinesByName: Map<String, Machine>): Set<String> {
        val refMachine = machine.refinesMachine?.let { machinesByName[it] } ?: return emptySet()
        val parameterNames = linkedSetOf<String>()
        for (label in refinedEventLabels(event)) {
            val refEvent = refMachine.events.find { it.label == label }
            if (refEvent != null) {
                parameterNames.addAll(effectiveEventParameterNames(refMachine, refEvent, machinesByName))
            }
        }
        return parameterNames
    }

    private fun refinedEventLabels(event: Event): List<String> =
        event.refinesEvents.ifEmpty { if (event.extended) listOf(event.label) else emptyList() }

    private fun validWitnessTarget(label: String, abstractParameters: Set<String>, abstractVariables: Set<String>): Set<String> {
        if (label in abstractParameters) {
            return setOf(label)
        }

        val baseLabel = label.removeSuffix("'")
        return if (baseLabel != label && baseLabel in abstractVariables) {
            setOf(label)
        } else {
            emptySet()
        }
    }

    private fun typeCheckFormula(
        formula: String,
        env: ITypeEnvironmentBuilder,
        filePath: String,
        elementLabel: String,
        errors: MutableList<ValidationError>,
        parsedFormulas: MutableList<ParsedFormula>,
        checkedFormulas: MutableList<TypeCheckedFormula>,
        kind: FormulaKind,
        parse: (String) -> IParseResult,
        extract: (IParseResult) -> Formula<*>,
        scope: IdentifierScope,
    ) {
        val parseResult = parse(formula)
        if (parseResult.hasProblem()) return

        val parsed = extract(parseResult)
        parsedFormulas.add(ParsedFormula(parsed, formula, filePath, elementLabel, kind))
        val undeclaredIdentifiers = collectUndeclaredIdentifiers(parsed, kind, scope)
        if (undeclaredIdentifiers.isNotEmpty()) {
            for (identifier in undeclaredIdentifiers) {
                errors.add(
                    ValidationError(
                        filePath = filePath,
                        severity = ValidationSeverity.ERROR,
                        message = "Undeclared identifier: '$identifier' is not declared in scope",
                        element = elementLabel,
                        formula = formula,
                        ruleId = ValidationRules.UNDECLARED_IDENTIFIER.id,
                    ),
                )
            }
        }

        val tcResult = parsed.typeCheck(env)

        if (tcResult.isSuccess) {
            env.addAll(tcResult.inferredEnvironment)
            if (undeclaredIdentifiers.isEmpty()) {
                checkedFormulas.add(TypeCheckedFormula(parsed, formula, filePath, elementLabel, kind))
            }
        } else if (undeclaredIdentifiers.isEmpty()) {
            for (problem in tcResult.problems) {
                // TypeUnknown (and TypeCheckFailure, its variant for type variables without
                // a source location) reflects constructs whose types the checker cannot
                // infer (e.g. primed witness variables), not a defect in the model itself.
                val unknownType = problem.message == ProblemKind.TypeUnknown ||
                    problem.message == ProblemKind.TypeCheckFailure
                val (severity, rule, message) = if (unknownType) {
                    Triple(ValidationSeverity.WARNING, ValidationRules.UNKNOWN_TYPE, "$problem")
                } else {
                    Triple(ValidationSeverity.ERROR, ValidationRules.TYPE_ERROR, "Type error: $problem")
                }
                errors.add(
                    ValidationError(
                        filePath = filePath,
                        severity = severity,
                        message = message,
                        element = elementLabel,
                        formula = formula,
                        ruleId = rule.id,
                    ),
                )
            }
        }
    }

    private fun collectUndeclaredIdentifiers(parsed: Formula<*>, kind: FormulaKind, scope: IdentifierScope): List<String> {
        val identifiers = linkedSetOf<String>()
        identifiers.addAll(parsed.freeIdentifiers.map { it.name })
        if (kind == FormulaKind.ASSIGNMENT) {
            identifiers.addAll((parsed as Assignment).assignedIdentifiers.map { it.name })
        }

        return identifiers
            .filterNot { scope.contains(it) }
            .sorted()
    }

    /**
     * Accumulates inferred types of declared identifiers as the env-building
     * traversal visits each context/machine/event. A declared identifier whose
     * type was never solved (`env.getType` returns null) is left out.
     */
    private class MutableTypeDump {
        private val contexts = linkedMapOf<String, MutableMap<String, String>>()
        private val machineVariables = linkedMapOf<String, MutableMap<String, String>>()
        private val machineEvents = linkedMapOf<String, MutableMap<String, MutableMap<String, String>>>()

        fun recordContext(ctx: Context, env: ITypeEnvironment) {
            val types = contexts.getOrPut(ctx.name) { linkedMapOf() }
            for (constant in ctx.constants) {
                env.getType(constant.identifier)?.let { types[constant.identifier] = it.toString() }
            }
        }

        fun recordMachineVariables(machine: Machine, env: ITypeEnvironment) {
            val types = machineVariables.getOrPut(machine.name) { linkedMapOf() }
            for (variable in machine.variables) {
                env.getType(variable.identifier)?.let { types[variable.identifier] = it.toString() }
            }
        }

        fun recordEvent(machine: Machine, event: Event, env: ITypeEnvironment) {
            val events = machineEvents.getOrPut(machine.name) { linkedMapOf() }
            val types = events.getOrPut(event.label) { linkedMapOf() }
            for (parameter in event.parameters) {
                env.getType(parameter.identifier)?.let { types[parameter.identifier] = it.toString() }
            }
        }

        fun build(): TypeDump {
            // Every machine is recorded in `machineVariables` before any of its
            // events, so its keys are a superset of `machineEvents`' keys.
            val machines = machineVariables.mapValues { (name, variables) ->
                MachineTypeDump(
                    variables = variables,
                    events = machineEvents[name].orEmpty(),
                )
            }
            return TypeDump(contexts = contexts, machines = machines)
        }
    }
}
