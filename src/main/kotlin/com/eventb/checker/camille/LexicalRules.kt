package com.eventb.checker.camille

import com.eventb.checker.validation.ValidationError
import com.eventb.checker.validation.ValidationRules
import com.eventb.checker.validation.ValidationSeverity

/**
 * What Camille's lexer does with a character, and where that differs from Rodin's math lexer.
 *
 * Both halves live together on purpose: the comment rules and the layout rules are one body of
 * knowledge about the same grammar, and a change to either has to be weighed against the other.
 */

/**
 * Camille's `layout_char`, transcribed from its `EventBParser.scc` grammar (and from rossi's
 * `keywords.rs::camille_layout_char`, which carries the same table):
 * `[[0 .. 32] + [127..160]] + [[8206 .. 8207] + [8232 .. 8233]]`.
 */
private fun Char.isCamilleLayout(): Boolean = this in '\u0000'..'\u0020' ||
    this in '\u007F'..'\u00A0' ||
    this in '\u200E'..'\u200F' ||
    this in '\u2028'..'\u2029'

/**
 * Whitespace as Rodin's math lexer knows it — `Character.isWhitespace(cp) ||
 * FormulaFactory.isEventBWhiteSpace(cp)` — the 28 code points rossi's `keywords.rs::is_whitespace`
 * spells out. Note U+001C..U+001F and U+00A0: both lexers read them as ordinary separators, and
 * U+00A0 in particular is the separator real Rodin XML contains.
 */
private fun Char.isRodinWhitespace(): Boolean = this in '\u0009'..'\u000D' ||
    this in '\u001C'..'\u001F' ||
    this == '\u0020' ||
    this == '\u00A0' ||
    this == '\u1680' ||
    this in '\u2000'..'\u200A' ||
    this == '\u2028' ||
    this == '\u2029' ||
    this == '\u202F' ||
    this == '\u205F' ||
    this == '\u3000'

/**
 * A character Camille swallows as layout although Rodin's math lexer does not read it as
 * whitespace: U+0000..U+0008, U+000E..U+001B, U+007F..U+009F and the bidi marks U+200E..U+200F.
 *
 * This is the rule as the difference between the two lexers rather than a third hand-kept table,
 * so it cannot drift from either. rossi reports the opposite difference — separators it reads and
 * Camille does not — as EB031; this is that composition taken the other way round.
 */
private fun Char.isSilentlyEaten(): Boolean = isCamilleLayout() && !isRodinWhitespace()

/** Individually reported offenders before [silentlyEatenCharacters] switches to a single summary. */
private const val MAX_REPORTED_CONTROL_CHARACTERS = 100

/**
 * One finding per character of [input] that Camille would silently eat, or empty when there is
 * none.
 *
 * `context C\u0000` lexes exactly like `context C`: the file parses clean, yields a context named
 * `C`, and means something other than what it reads as. Nothing downstream can notice, because by
 * then the character is gone — so the file is refused here, before [CamilleParser.normalize] and
 * [CamilleFileSplitter] carry it any further, rather than reporting on a file the lexer has
 * rewritten. Comments are exempt: Camille's comment rules swallow any character.
 *
 * Reported as EB004, the code rossi's parser already answers `context C\u0000` with. Every
 * occurrence is reported so one pass over the output covers the whole file, up to a cap — a file
 * that is nothing but control characters would otherwise serialise one finding per byte.
 */
internal fun silentlyEatenCharacters(input: String, filePath: String): List<ValidationError> {
    // Scanned before masking, and on the raw text: masking only ever substitutes a space, which is
    // whitespace to both lexers, so a clean input cannot mask to a dirty one. Clean files — every
    // real one — therefore skip building the masked copy altogether.
    if (input.none { it.isSilentlyEaten() }) return emptyList()

    val findings = mutableListOf<ValidationError>()
    var total = 0
    var line = 1
    var column = 1
    for (char in maskComments(input)) {
        if (char.isSilentlyEaten()) {
            total++
            if (total <= MAX_REPORTED_CONTROL_CHARACTERS) {
                findings.add(controlCharacterError(filePath, line, column, char))
            }
        }
        if (char == '\n') {
            line++
            column = 1
        } else {
            column++
        }
    }

    if (total > MAX_REPORTED_CONTROL_CHARACTERS) {
        findings.add(unlistedControlCharactersError(filePath, total))
    }
    return findings
}

private fun controlCharacterError(filePath: String, line: Int, column: Int, char: Char) = camilleParseError(
    filePath,
    "[$line,$column] control character " + "U+%04X".format(char.code) +
        "; Camille's lexer reads it as layout and drops it, so the surrounding token is silently " +
        "split or truncated, while Rodin's math lexer does not — remove it",
)

private fun unlistedControlCharactersError(filePath: String, total: Int) = camilleParseError(
    filePath,
    "${total - MAX_REPORTED_CONTROL_CHARACTERS} further control character(s) in this file are not " +
        "listed individually ($total in total); remove them all",
)

/** EB004 for [filePath], reading `Camille parse error: <detail>`. */
internal fun camilleParseError(filePath: String, detail: String) = ValidationError(
    filePath = filePath,
    severity = ValidationSeverity.ERROR,
    message = "Camille parse error: $detail",
    ruleId = ValidationRules.CAMILLE_PARSE_ERROR.id,
)

/**
 * [text] with every comment character replaced by a space, preserving length and line
 * structure so positions computed on the result also hold in the original.
 *
 * Camille's comment rules — `// …` to end of line, `/* … */` across lines — swallow any
 * character, so callers that reason about source characters (the top-level component scan in
 * [CamilleFileSplitter], the control-character guard in [CamilleParser]) must not see what a
 * comment contains. Masking rather than deleting is what keeps a comment acting as the token
 * separator it is: `context/*x*/C` masks to `context     C`, two tokens, as Camille reads it.
 */
internal fun maskComments(text: String): String {
    val masked = StringBuilder(text.length)
    var index = 0
    var inBlockComment = false
    var inLineComment = false

    while (index < text.length) {
        val char = text[index]
        when {
            char == '\n' || char == '\r' -> {
                // Line terminators are kept verbatim: they end a line comment, never a block
                // comment, and every line-based caller depends on the line count surviving.
                masked.append(char)
                inLineComment = false
                index++
            }
            inBlockComment -> {
                if (char == '*' && text.getOrNull(index + 1) == '/') {
                    masked.append("  ")
                    index += 2
                    inBlockComment = false
                } else {
                    masked.append(' ')
                    index++
                }
            }
            inLineComment -> {
                masked.append(' ')
                index++
            }
            char == '/' && text.getOrNull(index + 1) == '/' -> {
                masked.append("  ")
                index += 2
                inLineComment = true
            }
            char == '/' && text.getOrNull(index + 1) == '*' -> {
                masked.append("  ")
                index += 2
                inBlockComment = true
            }
            else -> {
                masked.append(char)
                index++
            }
        }
    }

    return masked.toString()
}
