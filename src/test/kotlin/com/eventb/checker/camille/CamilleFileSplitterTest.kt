package com.eventb.checker.camille

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class CamilleFileSplitterTest {

    private val splitter = CamilleFileSplitter()

    @Test
    fun `single machine returns input as-is`() {
        val input = """
            machine M
            variables x
            invariants
              @inv1 x ∈ ℕ
            end
        """.trimIndent()

        val chunks = splitter.split(input)

        assertThat(chunks).hasSize(1)
        assertThat(chunks[0].text).isEqualTo(input)
        assertThat(chunks[0].componentName).isEqualTo("M")
    }

    @Test
    fun `single context returns input as-is`() {
        val input = """
            context Ctx
            sets S
            end
        """.trimIndent()

        val chunks = splitter.split(input)

        assertThat(chunks).hasSize(1)
        assertThat(chunks[0].text).isEqualTo(input)
        assertThat(chunks[0].componentName).isEqualTo("Ctx")
    }

    @Test
    fun `context and machine in one file`() {
        val input = """
            context Limits
            constants lim
            axioms
              @axm1 lim ∈ ℕ
            end

            machine Counter
            sees Limits
            variables n
            invariants
              @inv1 n ∈ ℕ
            events
              event INITIALISATION
              then
                @act1 n ≔ 0
              end
            end
        """.trimIndent()

        val chunks = splitter.split(input)

        assertThat(chunks).hasSize(2)
        assertThat(chunks[0].componentName).isEqualTo("Limits")
        assertThat(chunks[0].text).startsWith("context Limits")
        assertThat(chunks[0].text).endsWith("end")

        assertThat(chunks[1].componentName).isEqualTo("Counter")
        assertThat(chunks[1].text).startsWith("machine Counter")
        assertThat(chunks[1].text).endsWith("end")
    }

    @Test
    fun `multiple machines`() {
        val input = """
            machine Base
            end

            machine Refined
            refines Base
            end
        """.trimIndent()

        val chunks = splitter.split(input)

        assertThat(chunks).hasSize(2)
        assertThat(chunks[0].componentName).isEqualTo("Base")
        assertThat(chunks[1].componentName).isEqualTo("Refined")
    }

    @Test
    fun `handles events with nested end keywords`() {
        val input = """
            context Ctx
            sets S
            end

            machine M
            sees Ctx
            variables x
            invariants
              @inv1 x ∈ S
            events
              event INITIALISATION
              then
                @act1 x :∈ S
              end
              event update
                any y
                where
                  @grd1 y ∈ S
                then
                  @act1 x ≔ y
              end
            end
        """.trimIndent()

        val chunks = splitter.split(input)

        assertThat(chunks).hasSize(2)
        assertThat(chunks[0].componentName).isEqualTo("Ctx")
        assertThat(chunks[1].componentName).isEqualTo("M")
        assertThat(chunks[1].text).contains("event INITIALISATION")
        assertThat(chunks[1].text).contains("event update")
    }

    @Test
    fun `comments containing keywords are ignored`() {
        val input = """
            // machine Fake
            context Ctx
            // this context does not define a machine
            sets S
            end

            /* context AnotherFake
               machine AlsoFake */
            machine M
            sees Ctx
            end
        """.trimIndent()

        val chunks = splitter.split(input)

        assertThat(chunks).hasSize(2)
        assertThat(chunks[0].componentName).isEqualTo("Ctx")
        assertThat(chunks[1].componentName).isEqualTo("M")
    }

    @Test
    fun `blank lines and whitespace between components`() {
        val input = """
            context A
            end



            context B
            end
        """.trimIndent()

        val chunks = splitter.split(input)

        assertThat(chunks).hasSize(2)
        assertThat(chunks[0].componentName).isEqualTo("A")
        assertThat(chunks[1].componentName).isEqualTo("B")
    }

    @Test
    fun `convergent and anticipated events count depth correctly`() {
        val input = """
            context Ctx
            end

            machine M
            events
              convergent event dec
              then
                @act1 skip
              end
              anticipated event wait
              then
                @act1 skip
              end
            end
        """.trimIndent()

        val chunks = splitter.split(input)

        assertThat(chunks).hasSize(2)
        assertThat(chunks[0].componentName).isEqualTo("Ctx")
        assertThat(chunks[1].componentName).isEqualTo("M")
        assertThat(chunks[1].text).contains("convergent event dec")
        assertThat(chunks[1].text).contains("anticipated event wait")
    }

    @Test
    fun `empty input returns single empty chunk`() {
        val chunks = splitter.split("")

        assertThat(chunks).hasSize(1)
        assertThat(chunks[0].text).isEmpty()
    }

    @Test
    fun `uppercase keywords are recognized`() {
        val input = """
            CONTEXT Ctx
            SETS S
            END

            MACHINE M
            SEES Ctx
            EVENTS
              EVENT INITIALISATION
              THEN
                @act1 skip
              END
            END
        """.trimIndent()

        val chunks = splitter.split(input)

        assertThat(chunks).hasSize(2)
        assertThat(chunks[0].componentName).isEqualTo("Ctx")
        assertThat(chunks[1].componentName).isEqualTo("M")
    }

    @Test
    fun `block comment spanning multiple lines hides keywords`() {
        val input = """
            /*
            machine Hidden
            end
            */
            context Visible
            end
        """.trimIndent()

        val chunks = splitter.split(input)

        assertThat(chunks).hasSize(1)
        assertThat(chunks[0].componentName).isEqualTo("Visible")
    }
}
