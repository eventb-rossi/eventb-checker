package com.eventb.checker.report

import com.eventb.checker.validation.MachineTypeDump
import com.eventb.checker.validation.TypeDump
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class TypeDumpFormatterTest {

    private val dump = TypeDump(
        contexts = mapOf("ctx" to mapOf("c" to "ℙ(S×S)", "n" to "ℤ")),
        machines = mapOf(
            "m" to MachineTypeDump(
                variables = mapOf("v" to "ℙ(S)"),
                events = mapOf("evt" to mapOf("p" to "S")),
            ),
        ),
    )

    @Test
    fun `toJson renders contexts, machine variables and event parameters`() {
        val json = TypeDumpFormatter.toJson(dump)

        val constants = json.getJSONObject("contexts").getJSONObject("ctx")
        assertThat(constants.getString("c")).isEqualTo("ℙ(S×S)")
        assertThat(constants.getString("n")).isEqualTo("ℤ")

        val machine = json.getJSONObject("machines").getJSONObject("m")
        assertThat(machine.getJSONObject("variables").getString("v")).isEqualTo("ℙ(S)")
        assertThat(machine.getJSONObject("events").getJSONObject("evt").getString("p")).isEqualTo("S")
    }

    @Test
    fun `toJson of an empty dump renders empty objects`() {
        val json = TypeDumpFormatter.toJson(TypeDump(contexts = emptyMap(), machines = emptyMap()))

        assertThat(json.getJSONObject("contexts").length()).isZero()
        assertThat(json.getJSONObject("machines").length()).isZero()
    }

    @Test
    fun `toText lists each declared identifier with its type`() {
        val text = TypeDumpFormatter.toText(dump)

        assertThat(text).contains("context ctx")
        assertThat(text).contains("constant c: ℙ(S×S)")
        assertThat(text).contains("constant n: ℤ")
        assertThat(text).contains("machine m")
        assertThat(text).contains("variable v: ℙ(S)")
        assertThat(text).contains("event evt")
        assertThat(text).contains("parameter p: S")
    }
}
