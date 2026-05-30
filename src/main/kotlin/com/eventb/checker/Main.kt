package com.eventb.checker

import com.eventb.checker.report.JsonReportFormatter
import com.eventb.checker.report.SarifReportFormatter
import com.eventb.checker.report.TextReportFormatter
import com.eventb.checker.validation.ProjectValidator
import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.Context
import com.github.ajalt.clikt.core.main
import com.github.ajalt.clikt.parameters.arguments.argument
import com.github.ajalt.clikt.parameters.options.default
import com.github.ajalt.clikt.parameters.options.flag
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.options.versionOption
import com.github.ajalt.clikt.parameters.types.choice
import kotlin.system.exitProcess

class CheckCommand :
    CliktCommand(
        name = "eventb-checker",
    ) {
    init {
        versionOption(Version.value, message = { "eventb-checker $it" })
    }

    override fun help(context: Context) = "Validate an Event-B model (.zip archive, directory, or .eventb file)"
    private val modelPath by argument(help = "Path to a .zip archive, directory, or .eventb file")
    private val format by option("--format", "-f", help = "Output format")
        .choice("text", "json", "sarif").default("text")
    private val showInfo by option("--show-info", help = "Include INFO-severity findings in output").flag()
    private val proofs by option("--proofs", "-p", help = "Check proof status from .bpr/.bpo/.bps files").flag()

    override fun run() {
        val validator = ProjectValidator(checkProofs = proofs)
        val result = try {
            validator.validate(modelPath)
        } catch (e: IllegalArgumentException) {
            echo("Error: ${e.message}", err = true)
            exitProcess(2)
        } catch (e: Exception) {
            echo("Unexpected error: ${e.message}", err = true)
            exitProcess(2)
        }

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

fun main(args: Array<String>) = CheckCommand().main(args)
