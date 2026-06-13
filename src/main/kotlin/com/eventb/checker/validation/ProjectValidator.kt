package com.eventb.checker.validation

import com.eventb.checker.ModelContents
import com.eventb.checker.ModelImporter
import com.eventb.checker.camille.CamilleParser
import com.eventb.checker.model.EventBProject
import com.eventb.checker.xml.RodinXmlParser
import org.eventb.core.ast.FormulaFactory

class ProjectValidator(private val checkProofs: Boolean = false) {

    private val xmlParser = RodinXmlParser()
    private val camilleParser = CamilleParser()
    private val formulaValidator = FormulaValidator()
    private val crossRefValidator = CrossReferenceValidator()
    private val typeChecker = TypeChecker(FormulaFactory.getDefault())
    private val wdChecker = WellDefinednessChecker()
    private val identifierAnalyzer = IdentifierAnalyzer()
    private val eventCompletenessChecker = EventCompletenessChecker()
    private val duplicateNameChecker = DuplicateNameChecker()
    private val proofStatusChecker = ProofStatusChecker()

    fun validate(modelPath: String): ValidationResult {
        val importer = ModelImporter()
        val contents = importer.import(modelPath)
        return validate(contents)
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
        allErrors.addAll(identifierAnalyzer.analyze(project, typeCheckResult.parsedFormulas))
        allErrors.addAll(eventCompletenessChecker.check(project, typeCheckResult.parsedFormulas))
        allErrors.addAll(duplicateNameChecker.check(project))

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
        val project = parseProject(contents, mutableListOf())
        return typeChecker.dumpTypes(project)
    }

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

        val projectName = resolvedMachines.firstOrNull()?.filePath?.substringBefore("/")
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
                    severity = ValidationSeverity.WARNING,
                    message = "Ignoring duplicate $kind '$name' from $path; using $selectedPath",
                    element = name,
                    ruleId = ValidationRules.DUPLICATE_COMPONENT.id,
                ),
            )
        }

        return selectedByName.values.sortedBy { filePathOf(it) }
    }
}
