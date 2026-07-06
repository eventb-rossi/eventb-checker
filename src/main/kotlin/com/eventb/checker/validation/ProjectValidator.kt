package com.eventb.checker.validation

import com.eventb.checker.ModelContents
import com.eventb.checker.ModelEntry
import com.eventb.checker.ModelImporter
import com.eventb.checker.camille.CamilleParser
import com.eventb.checker.model.EventBProject
import com.eventb.checker.model.ProofStatusSummary
import com.eventb.checker.xml.RodinXmlParser
import com.eventb.checker.xml.childElements
import com.eventb.checker.xml.parseXmlDocument
import org.eventb.core.ast.FormulaFactory

class ProjectValidator(private val checkProofs: Boolean = false) {

    private val xmlParser = RodinXmlParser()
    private val camilleParser = CamilleParser()
    private val formulaValidator = FormulaValidator()
    private val crossRefValidator = CrossReferenceValidator()
    private val typeChecker = TypeChecker(FormulaFactory.getDefault())
    private val wdChecker = WellDefinednessChecker()
    private val inheritanceResolver = MachineInheritanceResolver()
    private val identifierAnalyzer = IdentifierAnalyzer()
    private val eventCompletenessChecker = EventCompletenessChecker()
    private val duplicateNameChecker = DuplicateNameChecker()
    private val shadowedNameChecker = ShadowedNameChecker()
    private val refinementActionChecker = RefinementActionChecker()
    private val proofStatusChecker = ProofStatusChecker()

    fun validate(modelPath: String): ValidationResult {
        val contents = ModelImporter().import(modelPath)
        // A Rodin "Archive File" export may bundle several top-level project directories
        // into one archive. Each is a self-contained, project-local Event-B project, so we
        // validate them independently and merge the results rather than flattening them
        // into a single namespace (which would cross-wire same-named components like M0/C0).
        val projects = partitionByPrefix(contents)
        if (projects.size <= 1) return validate(contents)
        return projects.values.map { validate(it) }.merged()
    }

    fun validate(contents: ModelContents): ValidationResult {
        val allErrors = mutableListOf<ValidationError>()
        val project = parseProject(contents, allErrors)

        val formulaChecks = formulaValidator.collectFormulas(project)
        val formulaErrors = formulaChecks.flatMap { formulaValidator.validate(it) }
        allErrors.addAll(formulaErrors)

        val typeCheckResult = typeChecker.checkProjectFull(project)
        allErrors.addAll(typeCheckResult.errors)

        allErrors.addAll(wdChecker.check(typeCheckResult.checkedFormulas))
        val inheritance = inheritanceResolver.resolve(project)
        allErrors.addAll(identifierAnalyzer.analyze(project, inheritance))
        allErrors.addAll(eventCompletenessChecker.check(project, inheritance))
        allErrors.addAll(duplicateNameChecker.check(project))
        allErrors.addAll(shadowedNameChecker.check(project))
        allErrors.addAll(refinementActionChecker.check(project))

        val crossRefErrors = crossRefValidator.validate(project)
        allErrors.addAll(crossRefErrors)

        var proofSummary: com.eventb.checker.model.ProofStatusSummary? = null
        if (checkProofs) {
            val proofResult = proofStatusChecker.check(contents)
            allErrors.addAll(proofResult.errors)
            proofSummary = proofResult.summary
        }

        val summary = ValidationSummary(
            machineCount = project.machines.size,
            contextCount = project.contexts.size,
            formulaCount = formulaChecks.size,
            errorCount = allErrors.count { it.severity == ValidationSeverity.ERROR },
            warningCount = allErrors.count { it.severity == ValidationSeverity.WARNING },
            infoCount = allErrors.count { it.severity == ValidationSeverity.INFO },
            proofSummary = proofSummary,
        )

        return ValidationResult(allErrors, summary)
    }

    /**
     * Type-check the model at `modelPath` and return the inferred types of its
     * declared constants, variables, and event parameters (no other validation).
     */
    fun dumpTypes(modelPath: String): TypeDump {
        val contents = ModelImporter().import(modelPath)
        val projects = partitionByPrefix(contents)
        if (projects.size <= 1) return dumpTypes(contents)

        // Across multiple projects, identically-named components (M0/C0) would collide in the
        // bare-name-keyed TypeDump, so qualify each with its project prefix to keep them all.
        val contexts = linkedMapOf<String, Map<String, String>>()
        val machines = linkedMapOf<String, MachineTypeDump>()
        for ((prefix, project) in projects) {
            val dump = dumpTypes(project)
            for ((name, types) in dump.contexts) contexts["$prefix/$name"] = types
            for ((name, types) in dump.machines) machines["$prefix/$name"] = types
        }
        return TypeDump(contexts, machines)
    }

    private fun dumpTypes(contents: ModelContents): TypeDump = typeChecker.dumpTypes(parseProject(contents, mutableListOf()))

    private fun parseProject(contents: ModelContents, errors: MutableList<ValidationError>): EventBProject {
        val hasXmlInputs = contents.machines.isNotEmpty() || contents.contexts.isNotEmpty()
        val machines = contents.machines.mapNotNull { entry ->
            val result = xmlParser.parseMachine(entry.inputStream(), entry.path)
            errors.addAll(result.errors)
            result.machine
        }.toMutableList()

        val contexts = contents.contexts.mapNotNull { entry ->
            val result = xmlParser.parseContext(entry.inputStream(), entry.path)
            errors.addAll(result.errors)
            result.context
        }.toMutableList()

        if (!hasXmlInputs) {
            for (entry in contents.eventbFiles) {
                val result = camilleParser.parseFile(String(entry.data, Charsets.UTF_8), entry.path)
                errors.addAll(result.errors)
                machines.addAll(result.machines)
                contexts.addAll(result.contexts)
            }
        }

        val resolvedMachines = resolveDuplicateMachines(machines, errors)
        val resolvedContexts = resolveDuplicateContexts(contexts, errors)

        val projectName = readProjectName(contents.projectFiles)
            ?: resolvedMachines.firstOrNull()?.filePath?.substringBefore("/")
            ?: resolvedContexts.firstOrNull()?.filePath?.substringBefore("/")
            ?: contents.eventbFiles.firstOrNull()?.path?.substringBefore("/")?.takeIf { !hasXmlInputs }
            ?: "unknown"

        return EventBProject(
            name = projectName,
            machines = resolvedMachines,
            contexts = resolvedContexts,
        )
    }

    private fun resolveDuplicateMachines(
        machines: List<com.eventb.checker.model.Machine>,
        errors: MutableList<ValidationError>,
    ): List<com.eventb.checker.model.Machine> = resolveDuplicateComponents(
        kind = "machine",
        components = machines,
        nameOf = { it.name },
        filePathOf = { it.filePath },
        errors = errors,
    )

    private fun resolveDuplicateContexts(
        contexts: List<com.eventb.checker.model.Context>,
        errors: MutableList<ValidationError>,
    ): List<com.eventb.checker.model.Context> = resolveDuplicateComponents(
        kind = "context",
        components = contexts,
        nameOf = { it.name },
        filePathOf = { it.filePath },
        errors = errors,
    )

    private fun <T> resolveDuplicateComponents(
        kind: String,
        components: List<T>,
        nameOf: (T) -> String,
        filePathOf: (T) -> String,
        errors: MutableList<ValidationError>,
    ): List<T> {
        val sortedComponents = components.sortedBy { filePathOf(it) }
        val selectedByName = linkedMapOf<String, T>()
        val selectedPathByName = mutableMapOf<String, String>()

        for (component in sortedComponents) {
            val name = nameOf(component)
            val path = filePathOf(component)
            val selectedPath = selectedPathByName[name]

            if (selectedPath == null) {
                selectedByName[name] = component
                selectedPathByName[name] = path
                continue
            }

            errors.add(
                ValidationError(
                    filePath = path,
                    severity = ValidationSeverity.ERROR,
                    message = "Ignoring duplicate $kind '$name' from $path; using $selectedPath",
                    element = name,
                    ruleId = ValidationRules.DUPLICATE_COMPONENT.id,
                ),
            )
        }

        return selectedByName.values.sortedBy { filePathOf(it) }
    }

    /**
     * Group [contents] by top-level archive directory (the path segment before the first `/`),
     * keyed by that prefix. A single-project archive, a directory, a single `.eventb` file, a
     * flat archive (no directory), and an empty input all yield at most one prefix; only a
     * genuine multi-project archive (more than one top-level directory) yields several. Each
     * entry's path is scanned once, via a single [groupBy] per category.
     */
    private fun partitionByPrefix(contents: ModelContents): Map<String, ModelContents> {
        val grouped = contents.entryLists().map { entries -> entries.groupBy { prefixOf(it.path) } }
        val prefixes = grouped.flatMapTo(sortedSetOf<String>()) { it.keys }
        return prefixes.associateWith { prefix ->
            ModelContents.fromEntryLists(grouped.map { it[prefix].orEmpty() })
        }
    }

    private fun prefixOf(path: String): String {
        val slash = path.indexOf('/')
        return if (slash >= 0) path.substring(0, slash) else ""
    }

    /** Combine the per-project results (always two or more) into one report: concatenate
     *  errors, sum the summary counts. */
    private fun List<ValidationResult>.merged(): ValidationResult {
        val proofSummaries = mapNotNull { it.summary.proofSummary }
        val summary = ValidationSummary(
            machineCount = sumOf { it.summary.machineCount },
            contextCount = sumOf { it.summary.contextCount },
            formulaCount = sumOf { it.summary.formulaCount },
            errorCount = sumOf { it.summary.errorCount },
            warningCount = sumOf { it.summary.warningCount },
            infoCount = sumOf { it.summary.infoCount },
            proofSummary = if (proofSummaries.isEmpty()) null else proofSummaries.reduce(ProofStatusSummary::plus),
        )
        return ValidationResult(flatMap { it.errors }, summary)
    }

    /**
     * The project name from the top-level Eclipse/Rodin `.project` descriptor, or null if
     * absent/empty. Reads only the `<name>` that is a direct child of `<projectDescription>`
     * — never a nested `<buildCommand><name>` (a builder id) — and ignores nested `.project`
     * files.
     */
    private fun readProjectName(projectFiles: List<ModelEntry>): String? {
        val descriptor = projectFiles.firstOrNull {
            it.path.substringAfterLast('/') == ".project" && it.path.count { c -> c == '/' } <= 1
        } ?: return null

        val (doc, _) = parseXmlDocument(descriptor.inputStream())
        val nameElement = doc?.documentElement?.childElements()?.firstOrNull { it.tagName == "name" } ?: return null
        return nameElement.textContent?.trim()?.ifEmpty { null }
    }
}
