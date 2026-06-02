package com.eventb.checker.report

import com.eventb.checker.validation.TypeDump
import org.json.JSONObject

/**
 * Renders the output of the `info` command. Each requested fact becomes its own
 * keyed section, so additional facts (stats, components, …) can be added without
 * changing the shape of the existing ones.
 */
object InfoFormatter {
    fun json(types: TypeDump?): String {
        val root = JSONObject()
        if (types != null) {
            root.put("types", TypeDumpFormatter.toJson(types))
        }
        return root.toString(2)
    }

    fun text(types: TypeDump?): String = listOfNotNull(
        types?.let { TypeDumpFormatter.toText(it) },
    ).joinToString("\n\n")
}
