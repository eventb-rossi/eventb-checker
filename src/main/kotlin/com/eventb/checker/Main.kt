package com.eventb.checker

import com.eventb.checker.report.InfoFormatter
import com.eventb.checker.report.JsonReportFormatter
import com.eventb.checker.report.SarifReportFormatter
import com.eventb.checker.report.TextReportFormatter
import com.eventb.checker.validation.ProjectValidator
import com.github.ajalt.clikt.core.CliktCommand
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

    /** Run `block`, reporting any failure to stderr and exiting with code 2. */
    protected fun <T> runOrExit(block: () -> T): T = try {
        block()
    } catch (e: IllegalArgumentException) {
        echo("Error: ${e.message}", err = true)
        exitProcess(2)
    } catch (e: Exception) {
        echo("Unexpected error: ${e.message}", err = true)
        exitProcess(2)
    }
}

class CheckCommand : ModelCommand(name = "check") {
    override fun help(context: Context) = "Validate an Event-B model (.zip archive, directory, or .eventb file)"
    private val format by option("--format", "-f", help = "Output format")
        .choice("text", "json", "sarif").default("text")
    private val showInfo by option("--show-info", help = "Include INFO-severity findings in output").flag()
    private val proofs by option("--proofs", "-p", help = "Check proof status from .bpr/.bpo/.bps files").flag()

    override fun run() {
        val validator = ProjectValidator(checkProofs = proofs)
        val result = runOrExit { validator.validate(modelPath) }

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

    override fun run() {
        if (!types) {
            throw UsageError("Specify at least one kind of information to show (e.g. --types)")
        }
        val dump = runOrExit { ProjectValidator().dumpTypes(modelPath) }
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
