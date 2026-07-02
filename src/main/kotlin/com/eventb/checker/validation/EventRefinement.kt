package com.eventb.checker.validation

import com.eventb.checker.model.Event

/** Rodin's reserved label for the initialisation event. */
internal const val INITIALISATION = "INITIALISATION"

/**
 * The abstract events [event] refines: its explicit REFINES targets, or its own label for an
 * extended event without any (the implicit match Rodin-XML import produces for self-closing
 * extended events). Shared by [TypeChecker], [MachineInheritanceResolver], and
 * [RefinementActionChecker] so they cannot disagree about what an event refines.
 */
internal fun refinedEventLabels(event: Event): List<String> =
    event.refinesEvents.ifEmpty { if (event.extended) listOf(event.label) else emptyList() }
