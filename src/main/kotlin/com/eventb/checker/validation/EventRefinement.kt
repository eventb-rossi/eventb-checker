package com.eventb.checker.validation

import com.eventb.checker.model.Event
import com.eventb.checker.model.Machine

/** Rodin's reserved label for the initialisation event. */
internal const val INITIALISATION = "INITIALISATION"

/**
 * The abstract events [event] refines: its explicit REFINES targets, or its own label for an
 * extended event without any (the implicit match Rodin-XML import produces for self-closing
 * extended events). Shared by the refinement-aware validators so they cannot disagree
 * about what an event refines.
 */
internal fun refinedEventLabels(event: Event): List<String> =
    event.refinesEvents.ifEmpty { if (event.extended) listOf(event.label) else emptyList() }

/** Resolves [event]'s abstract-event targets within its parent [machine]. */
internal fun abstractEventsOf(machine: Machine, event: Event): List<Event> =
    refinedEventLabels(event).mapNotNull { label -> machine.events.find { it.label == label } }
