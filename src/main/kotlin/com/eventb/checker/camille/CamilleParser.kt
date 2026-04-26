package com.eventb.checker.camille

import com.eventb.checker.model.Action
import com.eventb.checker.model.Axiom
import com.eventb.checker.model.CarrierSet
import com.eventb.checker.model.Constant
import com.eventb.checker.model.Context
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
import de.be4.eventb.core.parser.BException
import de.be4.eventb.core.parser.EventBParser
import de.be4.eventb.core.parser.node.AAction
import de.be4.eventb.core.parser.node.AAnticipatedConvergence
import de.be4.eventb.core.parser.node.AAxiom
import de.be4.eventb.core.parser.node.ACarrierSet
import de.be4.eventb.core.parser.node.AConstant
import de.be4.eventb.core.parser.node.AContextParseUnit
import de.be4.eventb.core.parser.node.AConvergentConvergence
import de.be4.eventb.core.parser.node.ADerivedAxiom
import de.be4.eventb.core.parser.node.ADerivedGuard
import de.be4.eventb.core.parser.node.ADerivedInvariant
import de.be4.eventb.core.parser.node.AEvent
import de.be4.eventb.core.parser.node.AExtendedEventRefinement
import de.be4.eventb.core.parser.node.AGuard
import de.be4.eventb.core.parser.node.AInvariant
import de.be4.eventb.core.parser.node.AMachineParseUnit
import de.be4.eventb.core.parser.node.AParameter
import de.be4.eventb.core.parser.node.ARefinesEventRefinement
import de.be4.eventb.core.parser.node.AVariable
import de.be4.eventb.core.parser.node.AVariant
import de.be4.eventb.core.parser.node.AWitness
import de.be4.eventb.core.parser.node.PAxiom
import de.be4.eventb.core.parser.node.PGuard
import de.be4.eventb.core.parser.node.PInvariant

data class CamilleParseResult(val machine: Machine?, val context: Context?, val errors: List<ValidationError>)

data class CamilleFileResult(val machines: List<Machine>, val contexts: List<Context>, val errors: List<ValidationError>)

open class CamilleParser {

    companion object {
        private const val VARIANT_LABEL = "vrn"

        private val CAMILLE_KEYWORDS = setOf(
            "MACHINE", "CONTEXT", "REFINES", "SEES", "EXTENDS",
            "VARIABLES", "INVARIANTS", "VARIANT", "EVENTS", "EVENT",
            "SETS", "CONSTANTS", "AXIOMS", "END",
            "ANY", "WHERE", "WHEN", "THEN", "WITH",
            "CONVERGENT", "ANTICIPATED", "THEOREM",
        )

        private val KEYWORD_PATTERN = Regex("\\b(${CAMILLE_KEYWORDS.joinToString("|")})\\b")

        /** Lines that contain only identifiers, commas, and whitespace (no formulas/operators). */
        private val IDENT_LIST_LINE = Regex("[\\w,\\s]+")

        /**
         * Matches `@label theorem predicate` — the file puts the label before the keyword,
         * but Camille expects `theorem @label predicate`.
         */
        private val LABEL_THEOREM_PATTERN = Regex("""^(\s*)(@\w+)\s+theorem\s+(.*)$""")

        fun normalizeKeywords(input: String): String = input.replace(KEYWORD_PATTERN) { it.value.lowercase() }

        /**
         * Replaces commas with spaces on lines that are pure identifier lists
         * (e.g. `file, parent, name` in an `any` block or `sees A, B`).
         * Formula lines starting with `@` and lines containing mathematical
         * operators are left untouched.
         */
        fun normalizeCommaLists(input: String): String = input.lines().joinToString("\n") { line ->
            val trimmed = line.trim()
            if (trimmed.isNotEmpty() && !trimmed.startsWith("@") && IDENT_LIST_LINE.matches(trimmed)) {
                line.replace(",", " ")
            } else {
                line
            }
        }

        /**
         * Swaps `@label theorem predicate` to `theorem @label predicate`.
         * Some Event-B editors place the label before the `theorem` keyword,
         * but the Camille grammar expects `theorem` first.
         */
        fun normalizeTheoremOrder(input: String): String = input.lines().joinToString("\n") { line ->
            val match = LABEL_THEOREM_PATTERN.matchEntire(line)
            if (match != null) {
                val (indent, label, predicate) = match.destructured
                "${indent}theorem $label $predicate"
            } else {
                line
            }
        }

        fun normalize(input: String): String = normalizeTheoremOrder(normalizeCommaLists(normalizeKeywords(input)))
    }

    fun parse(input: String, filePath: String): CamilleParseResult = parseNormalized(normalize(input), filePath)

    private fun parseNormalized(normalized: String, filePath: String): CamilleParseResult {
        val parser = EventBParser()
        val start = try {
            parser.parse(normalized, false)
        } catch (e: BException) {
            return CamilleParseResult(
                machine = null,
                context = null,
                errors = listOf(
                    ValidationError(
                        filePath = filePath,
                        severity = ValidationSeverity.ERROR,
                        message = "Camille parse error: ${e.message}",
                        ruleId = ValidationRules.CAMILLE_PARSE_ERROR.id,
                    ),
                ),
            )
        }

        return try {
            when (val unit = start.pParseUnit) {
                is AMachineParseUnit -> CamilleParseResult(
                    machine = convertMachine(unit, filePath),
                    context = null,
                    errors = emptyList(),
                )
                is AContextParseUnit -> CamilleParseResult(
                    machine = null,
                    context = convertContext(unit, filePath),
                    errors = emptyList(),
                )
                else -> CamilleParseResult(
                    machine = null,
                    context = null,
                    errors = listOf(
                        ValidationError(
                            filePath = filePath,
                            severity = ValidationSeverity.ERROR,
                            message = "Camille parse error: unknown parse unit type",
                            ruleId = ValidationRules.CAMILLE_PARSE_ERROR.id,
                        ),
                    ),
                )
            }
        } catch (e: RuntimeException) {
            CamilleParseResult(
                machine = null,
                context = null,
                errors = listOf(
                    ValidationError(
                        filePath = filePath,
                        severity = ValidationSeverity.ERROR,
                        message = "Camille parse error: failed to convert parse tree: ${e.message ?: e.javaClass.simpleName}",
                        ruleId = ValidationRules.CAMILLE_PARSE_ERROR.id,
                    ),
                ),
            )
        }
    }

    fun parseFile(input: String, filePath: String): CamilleFileResult {
        val normalized = normalize(input)
        val splitter = CamilleFileSplitter()
        val chunks = splitter.split(normalized)

        val machines = mutableListOf<Machine>()
        val contexts = mutableListOf<Context>()
        val errors = mutableListOf<ValidationError>()

        val multiUnit = chunks.size > 1

        for (chunk in chunks) {
            val chunkPath = if (multiUnit) "$filePath[${chunk.componentName}]" else filePath
            val result = parseNormalized(chunk.text, chunkPath)
            errors.addAll(result.errors)
            result.machine?.let { machines.add(it) }
            result.context?.let { contexts.add(it) }
        }

        return CamilleFileResult(machines = machines, contexts = contexts, errors = errors)
    }

    internal open fun convertMachine(node: AMachineParseUnit, filePath: String): Machine {
        val name = node.name.text
        val refinesNames = node.refinesNames.map { it.text }
        val seenNames = node.seenNames.map { it.text }

        val variables = node.variables.map { v ->
            val av = v as AVariable
            Variable(identifier = av.name.text, label = av.name.text)
        }

        val invariants = node.invariants.map { convertInvariant(it) }

        val variant = node.variant?.let { v ->
            val av = v as AVariant
            Variant(label = VARIANT_LABEL, expression = av.expression.text)
        }

        val events = node.events.map { e ->
            val ae = e as AEvent
            convertEvent(ae)
        }

        return Machine(
            name = name,
            filePath = filePath,
            seesContexts = seenNames,
            refinesMachine = refinesNames.firstOrNull(),
            variables = variables,
            invariants = invariants,
            variant = variant,
            events = events,
        )
    }

    private fun convertInvariant(node: PInvariant): Invariant = when (node) {
        is AInvariant -> Invariant(
            label = node.name.text,
            predicate = node.predicate.text,
            theorem = false,
        )
        is ADerivedInvariant -> Invariant(
            label = node.name.text,
            predicate = node.predicate.text,
            theorem = true,
        )
        else -> error("Unknown invariant type: ${node.javaClass}")
    }

    private fun convertEvent(node: AEvent): Event {
        val name = node.name.text

        val convergence = when (node.convergence) {
            is AConvergentConvergence -> Convergence.CONVERGENT
            is AAnticipatedConvergence -> Convergence.ANTICIPATED
            else -> Convergence.ORDINARY
        }

        val refinement = node.refinement
        val extended = refinement is AExtendedEventRefinement
        val refinesEvents = when (refinement) {
            is ARefinesEventRefinement -> refinement.names.map { it.text }
            is AExtendedEventRefinement -> listOf(refinement.name.text)
            else -> emptyList()
        }

        val parameters = node.parameters.map { p ->
            val ap = p as AParameter
            Parameter(identifier = ap.name.text, label = ap.name.text)
        }

        val guards = node.guards.map { convertGuard(it) }

        val witnesses = node.witnesses.map { w ->
            val aw = w as AWitness
            Witness(label = aw.name.text, predicate = aw.predicate.text)
        }

        val actions = node.actions.map { a ->
            val aa = a as AAction
            Action(label = aa.name.text, assignment = aa.action.text)
        }

        return Event(
            label = name,
            convergence = convergence,
            extended = extended,
            refinesEvents = refinesEvents,
            parameters = parameters,
            guards = guards,
            actions = actions,
            witnesses = witnesses,
        )
    }

    private fun convertGuard(node: PGuard): Guard = when (node) {
        is AGuard -> Guard(
            label = node.name.text,
            predicate = node.predicate.text,
            theorem = false,
        )
        is ADerivedGuard -> Guard(
            label = node.name.text,
            predicate = node.predicate.text,
            theorem = true,
        )
        else -> error("Unknown guard type: ${node.javaClass}")
    }

    internal open fun convertContext(node: AContextParseUnit, filePath: String): Context {
        val name = node.name.text
        val extendsNames = node.extendsNames.map { it.text }

        val sets = node.sets.map { s ->
            val cs = s as ACarrierSet
            CarrierSet(identifier = cs.name.text, label = cs.name.text)
        }

        val constants = node.constants.map { c ->
            val ac = c as AConstant
            Constant(identifier = ac.name.text, label = ac.name.text)
        }

        val axioms = node.axioms.map { convertAxiom(it) }

        return Context(
            name = name,
            filePath = filePath,
            extendsContexts = extendsNames,
            carrierSets = sets,
            constants = constants,
            axioms = axioms,
        )
    }

    private fun convertAxiom(node: PAxiom): Axiom = when (node) {
        is AAxiom -> Axiom(
            label = node.name.text,
            predicate = node.predicate.text,
            theorem = false,
        )
        is ADerivedAxiom -> Axiom(
            label = node.name.text,
            predicate = node.predicate.text,
            theorem = true,
        )
        else -> error("Unknown axiom type: ${node.javaClass}")
    }
}
