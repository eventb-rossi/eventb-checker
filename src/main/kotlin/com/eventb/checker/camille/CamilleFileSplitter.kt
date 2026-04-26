package com.eventb.checker.camille

data class CamilleChunk(val text: String, val componentName: String)

class CamilleFileSplitter {

    fun split(input: String): List<CamilleChunk> {
        val lines = input.lines()
        val chunks = mutableListOf<CamilleChunk>()
        var depth = 0
        var currentLines = mutableListOf<String>()
        var currentName = ""
        var inBlockComment = false

        for (line in lines) {
            val stripped = stripComments(line, inBlockComment)
            inBlockComment = stripped.second
            val effective = stripped.first.trim()

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

    private fun stripComments(line: String, inBlock: Boolean): Pair<String, Boolean> {
        val sb = StringBuilder()
        var i = 0
        var block = inBlock

        while (i < line.length) {
            if (block) {
                val closeIdx = line.indexOf("*/", i)
                if (closeIdx == -1) {
                    // Rest of line is inside block comment
                    return Pair(sb.toString(), true)
                }
                i = closeIdx + 2
                block = false
            } else {
                if (i + 1 < line.length && line[i] == '/' && line[i + 1] == '/') {
                    // Rest of line is a line comment
                    return Pair(sb.toString(), false)
                }
                if (i + 1 < line.length && line[i] == '/' && line[i + 1] == '*') {
                    block = true
                    i += 2
                } else {
                    sb.append(line[i])
                    i++
                }
            }
        }

        return Pair(sb.toString(), block)
    }

    companion object {
        private val TOP_LEVEL_REGEX = Regex("""^(machine|context)\s+(\S+).*""", RegexOption.IGNORE_CASE)
        private val EVENT_REGEX = Regex("""(?:^|(?:convergent|anticipated)\s+)event\s+\S+""", RegexOption.IGNORE_CASE)
        private val END_REGEX = Regex("""^end\s*$""", RegexOption.IGNORE_CASE)
    }
}
