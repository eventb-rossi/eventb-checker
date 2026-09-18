package com.eventb.checker.camille

data class CamilleChunk(val text: String, val componentName: String)

class CamilleFileSplitter {

    fun split(input: String): List<CamilleChunk> {
        val chunks = mutableListOf<CamilleChunk>()
        var depth = 0
        var currentLines = mutableListOf<String>()
        var currentName = ""

        // [maskComments] preserves length and line terminators, so the two line lists pair up
        // exactly: structure is read off the masked line, the original is kept for the chunk text.
        for ((line, masked) in input.lines().zip(maskComments(input).lines())) {
            val effective = masked.trim()

            if (depth == 0) {
                val topLevel = matchTopLevel(effective)
                if (topLevel != null) {
                    // Start a new component
                    currentLines = mutableListOf(line)
                    currentName = topLevel
                    depth = 1
                    continue
                }
                // Outside any component — skip blank/whitespace/comment lines
                continue
            }

            currentLines.add(line)

            // Count depth changes within a component
            depth += countDepthChange(effective)

            if (depth <= 0) {
                chunks.add(CamilleChunk(currentLines.joinToString("\n"), currentName))
                depth = 0
                currentLines = mutableListOf()
                currentName = ""
            }
        }

        // If we still have an open component (missing final `end`), include it as-is
        if (depth > 0 && currentLines.isNotEmpty()) {
            chunks.add(CamilleChunk(currentLines.joinToString("\n"), currentName))
        }

        // Single component: return original input as-is for backward compatibility
        if (chunks.size <= 1) {
            return listOf(CamilleChunk(input, chunks.firstOrNull()?.componentName ?: ""))
        }

        return chunks
    }

    private fun matchTopLevel(effective: String): String? {
        val match = TOP_LEVEL_REGEX.matchEntire(effective) ?: return null
        return match.groupValues[2]
    }

    private fun countDepthChange(effective: String): Int {
        if (effective.isEmpty()) return 0
        var delta = 0

        // +1 for `event <name>` (but not `events` section header)
        if (EVENT_REGEX.containsMatchIn(effective)) {
            delta++
        }

        // -1 for standalone `end`
        if (END_REGEX.matches(effective)) {
            delta--
        }

        return delta
    }

    companion object {
        private val TOP_LEVEL_REGEX = Regex("""^(machine|context)\s+(\S+).*""", RegexOption.IGNORE_CASE)
        private val EVENT_REGEX = Regex("""(?:^|(?:convergent|anticipated)\s+)event\s+\S+""", RegexOption.IGNORE_CASE)
        private val END_REGEX = Regex("""^end\s*$""", RegexOption.IGNORE_CASE)
    }
}
