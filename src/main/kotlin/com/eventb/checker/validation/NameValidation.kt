package com.eventb.checker.validation

internal fun duplicateNonBlankNameCounts(names: Iterable<String>): Map<String, Int> = names
    .filter { it.isNotBlank() }
    .groupingBy { it }
    .eachCount()
    .filterValues { it > 1 }
