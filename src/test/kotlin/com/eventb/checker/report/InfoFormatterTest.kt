package com.eventb.checker.report

import com.eventb.checker.validation.TypeDump
import org.assertj.core.api.Assertions.assertThat
import org.json.JSONObject
import org.junit.jupiter.api.Test

class InfoFormatterTest {

    private val dump = TypeDump(
        contexts = mapOf("ctx" to mapOf("c" to "ℤ")),
        machines = emptyMap(),
    )

    @Test
    fun `json nests the type dump under a types key`() {
        val json = JSONObject(InfoFormatter.json(dump))

        assertThat(json.keySet()).containsExactly("types")
        assertThat(json.getJSONObject("types").getJSONObject("contexts").getJSONObject("ctx").getString("c"))
            .isEqualTo("ℤ")
    }

    @Test
    fun `json without any facts is an empty object`() {
        assertThat(JSONObject(InfoFormatter.json(null)).length()).isZero()
    }

    @Test
    fun `text renders the type section`() {
        assertThat(InfoFormatter.text(dump)).contains("constant c: ℤ")
    }
}
