package com.eventb.checker.report

import com.eventb.checker.validation.TypeDump
import org.json.JSONObject

/**
 * Renders the inferred identifier types of a model. [toJson] produces the
 * structured form consumed by rossi's type-oracle diff harness:
 *
 *   {
 *     "contexts": { "<ctx>": { "<constant>": "<type>" } },
 *     "machines": { "<m>": {
 *         "variables": { "<var>": "<type>" },
 *         "events": { "<event>": { "<param>": "<type>" } }
 *     } }
 *   }
 *
 * Types are Rodin's canonical [org.eventb.core.ast.Type] strings (e.g. `ℙ(S×S)`).
 */
object TypeDumpFormatter {
    /** The inferred types as a JSON object (the type-oracle contract). */
    fun toJson(dump: TypeDump): JSONObject {
        val contexts = JSONObject()
        for ((ctx, constants) in dump.contexts) {
            contexts.put(ctx, toJson(constants))
        }
        val machines = JSONObject()
        for ((machine, body) in dump.machines) {
            val events = JSONObject()
            for ((event, params) in body.events) {
                events.put(event, toJson(params))
            }
            machines.put(
                machine,
                JSONObject()
                    .put("variables", toJson(body.variables))
                    .put("events", events),
            )
        }
        return JSONObject()
            .put("contexts", contexts)
            .put("machines", machines)
    }

    /** A human-readable rendering; components with no typed identifiers are omitted. */
    fun toText(dump: TypeDump): String {
        val sb = StringBuilder("=== Inferred Types ===")
        for ((ctx, constants) in dump.contexts) {
            if (constants.isEmpty()) continue
            sb.append("\n\ncontext ").append(ctx)
            for ((name, type) in constants) {
                sb.append("\n  constant ").append(name).append(": ").append(type)
            }
        }
        for ((machine, body) in dump.machines) {
            val typedEvents = body.events.filterValues { it.isNotEmpty() }
            if (body.variables.isEmpty() && typedEvents.isEmpty()) continue
            sb.append("\n\nmachine ").append(machine)
            for ((name, type) in body.variables) {
                sb.append("\n  variable ").append(name).append(": ").append(type)
            }
            for ((event, params) in typedEvents) {
                sb.append("\n  event ").append(event)
                for ((name, type) in params) {
                    sb.append("\n    parameter ").append(name).append(": ").append(type)
                }
            }
        }
        return sb.toString()
    }

    private fun toJson(types: Map<String, String>): JSONObject {
        val obj = JSONObject()
        for ((name, type) in types) {
            obj.put(name, type)
        }
        return obj
    }
}
