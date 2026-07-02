package com.eventb.checker.validation

import com.eventb.checker.model.EventBProject

class EventCompletenessChecker {

    fun check(project: EventBProject, inheritance: Map<String, MachineInheritance>): List<ValidationError> {
        val findings = mutableListOf<ValidationError>()

        for (machine in project.machines) {
            if (machine.variables.isEmpty()) continue

            val initEvent = machine.events.find { it.label == INITIALISATION } ?: continue

            // The materialized set is null when an extended INITIALISATION's inherited chain
            // cannot be resolved (missing parent, cycle, ancestor without an INITIALISATION,
            // unparseable inherited action); those defects are reported elsewhere
            // (EB005/EB008/EB009), so completeness is not judged here.
            val assignedIdentifiers = inheritance.getValue(machine.name).initAssignedIdentifiers ?: continue

            for (variable in machine.variables) {
                if (variable.identifier !in assignedIdentifiers) {
                    findings.add(
                        ValidationError(
                            filePath = machine.filePath,
                            severity = ValidationSeverity.WARNING,
                            message = "INITIALISATION does not assign variable '${variable.identifier}'",
                            element = initEvent.label,
                            ruleId = ValidationRules.INCOMPLETE_INITIALISATION.id,
                        ),
                    )
                }
            }
        }

        return findings
    }
}
