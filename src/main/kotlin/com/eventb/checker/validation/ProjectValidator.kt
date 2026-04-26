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

        if (formulaErrors.isEmpty()) {
            val typeCheckResult = typeChecker.checkProjectFull(project)
            allErrors.addAll(typeCheckResult.errors)

            val checkedFormulas = typeCheckResult.checkedFormulas
            allErrors.addAll(wdChecker.check(checkedFormulas))
            allErrors.addAll(identifierAnalyzer.analyze(project, checkedFormulas))
            allErrors.addAll(eventCompletenessChecker.check(project, checkedFormulas))
        }

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

    private fun parseProject(contents: ModelContents, errors: MutableList<ValidationError>): EventBProject {
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

        val hasXmlFiles = contents.machines.isNotEmpty() || contents.contexts.isNotEmpty()
        if (!hasXmlFiles) {
            for (entry in contents.eventbFiles) {
                val result = camilleParser.parseFile(String(entry.data, Charsets.UTF_8), entry.path)
                errors.addAll(result.errors)
                machines.addAll(result.machines)
                contexts.addAll(result.contexts)
            }
        }

        val projectName = contents.machines.firstOrNull()?.path?.substringBefore("/")
            ?: contents.contexts.firstOrNull()?.path?.substringBefore("/")
            ?: contents.eventbFiles.firstOrNull()?.path?.substringBefore("/")
            ?: "unknown"

        return EventBProject(
            name = projectName,
            machines = machines,
            contexts = contexts,
        )
    }
}
