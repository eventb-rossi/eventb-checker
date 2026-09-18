package com.eventb.checker

import com.eventb.checker.report.InfoFormatter
import com.eventb.checker.report.JsonReportFormatter
import com.eventb.checker.report.SarifReportFormatter
import com.eventb.checker.report.TextReportFormatter
import com.eventb.checker.validation.ProjectValidator
import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.CliktError
import com.github.ajalt.clikt.core.Context
import com.github.ajalt.clikt.core.UsageError
import com.github.ajalt.clikt.core.main
import com.github.ajalt.clikt.core.subcommands
import com.github.ajalt.clikt.parameters.arguments.argument
import com.github.ajalt.clikt.parameters.options.default
import com.github.ajalt.clikt.parameters.options.flag
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.options.versionOption
import com.github.ajalt.clikt.parameters.types.choice
import kotlin.system.exitProcess

/** Top-level command; dispatches to the `check` and `info` subcommands. */
class EventBChecker : CliktCommand(name = "eventb-checker") {
    init {
        versionOption(Version.value, message = { "eventb-checker $it" })
    }

    override fun help(context: Context) = "Validate Event-B models or report their inferred identifier types"

    override fun run() = Unit
}

/** Shared base for subcommands that operate on a single model path. */
abstract class ModelCommand(name: String) : CliktCommand(name = name) {
    protected val modelPath by argument(help = "Path to a .zip archive, directory, or .eventb file")

    /**
     * The whole subcommand, including rendering the report: serialising a report with a very large
     * number of findings is itself somewhere an OutOfMemoryError lands, and the exit-code contract
     * belongs to this class rather than to each subcommand remembering to ask for it.
     */
    final override fun run() = runOrExit { execute() }

    protected abstract fun execute()

    /** Run `block`, reporting any failure to stderr and exiting with code 2. */
    private fun <T> runOrExit(block: () -> T): T = try {
        block()
    } catch (e: CliktError) {
        // Clikt's own control flow — usage errors, --help, ProgramResult. Not a failure to report.
        throw e
    } catch (e: IllegalArgumentException) {
        echo("Error: ${e.message}", err = true)
        exitProcess(2)
    } catch (e: Throwable) {
        // Throwable, not Exception: a StackOverflowError from a formula nested deeper than any
        // per-formula guard anticipated, or an OutOfMemoryError, is an Error and would otherwise
        // escape main and print a raw JVM stack trace with nothing on stdout. Exit 2 rather than
        // 1 — 1 means the model has findings and is paired with a report, 2 means the checker
        // could not do its job, which is what the CI wrappers key on. `$e` is Throwable.toString():
        // the type, plus the message when there is one, since an Error usually carries none.
        echo("Unexpected error: $e", err = true)
        exitProcess(2)
    }
}

class CheckCommand : ModelCommand(name = "check") {
    override fun help(context: Context) = "Validate an Event-B model (.zip archive, directory, or .eventb file)"
    private val format by option("--format", "-f", help = "Output format")
        .choice("text", "json", "sarif").default("text")
    private val showInfo by option("--show-info", help = "Include INFO-severity findings in output").flag()
    private val proofs by option("--proofs", "-p", help = "Check proof status from .bpr/.bpo/.bps files").flag()

    override fun execute() {
        val validator = ProjectValidator(checkProofs = proofs)
        val result = validator.validate(modelPath)

        val output = if (showInfo) result else result.withoutInfo()

        val formatter = when (format) {
            "json" -> JsonReportFormatter()
            "sarif" -> SarifReportFormatter()
            else -> TextReportFormatter()
        }
        echo(formatter.format(output))

        if (!result.isValid) {
            exitProcess(1)
        }
    }
}

class InfoCommand : ModelCommand(name = "info") {
    override fun help(context: Context) = "Report read-only information about a model (currently: inferred identifier types)"
    private val types by option("--types", help = "Include the inferred types of declared identifiers").flag()
    private val format by option("--format", "-f", help = "Output format")
        .choice("text", "json").default("text")

    override fun execute() {
        if (!types) {
            throw UsageError("Specify at least one kind of information to show (e.g. --types)")
        }
        val dump = ProjectValidator().dumpTypes(modelPath)
        echo(
            when (format) {
                "json" -> InfoFormatter.json(dump)
                else -> InfoFormatter.text(dump)
            },
        )
    }
}

fun main(args: Array<String>) = EventBChecker()
    .subcommands(CheckCommand(), InfoCommand())
    .main(args)
