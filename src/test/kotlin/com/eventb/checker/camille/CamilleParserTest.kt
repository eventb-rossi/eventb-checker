package com.eventb.checker.camille

import com.eventb.checker.model.Convergence
import com.eventb.checker.model.Machine
import com.eventb.checker.validation.ValidationSeverity
import de.be4.eventb.core.parser.node.AMachineParseUnit
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class CamilleParserTest {

    private val parser = CamilleParser()

    @Test
    fun `parses minimal machine`() {
        val input = """
            machine MyMachine
            end
        """.trimIndent()

        val result = parser.parse(input, "project/MyMachine.eventb")

        assertThat(result.machine).isNotNull
        assertThat(result.context).isNull()
        assertThat(result.errors).isEmpty()
        assertThat(result.machine!!.name).isEqualTo("MyMachine")
        assertThat(result.machine.filePath).isEqualTo("project/MyMachine.eventb")
    }

    @Test
    fun `parses machine with sees and refines`() {
        val input = """
            machine Refined
            refines Base
            sees MyCtx
            end
        """.trimIndent()

        val result = parser.parse(input, "project/Refined.eventb")
        val m = result.machine!!

        assertThat(m.refinesMachine).isEqualTo("Base")
        assertThat(m.seesContexts).containsExactly("MyCtx")
    }

    @Test
    fun `parses machine with variables and invariants`() {
        val input = """
            machine Counter
            variables n
            invariants
              @inv1 n ∈ ℕ
            end
        """.trimIndent()

        val result = parser.parse(input, "project/Counter.eventb")
        val m = result.machine!!

        assertThat(m.variables).hasSize(1)
        assertThat(m.variables[0].identifier).isEqualTo("n")
        assertThat(m.invariants).hasSize(1)
        assertThat(m.invariants[0].label).isEqualTo("inv1")
        assertThat(m.invariants[0].predicate).isEqualTo("n ∈ ℕ")
        assertThat(m.invariants[0].theorem).isFalse()
    }

    @Test
    fun `parses theorem invariant`() {
        val input = """
            machine M
            variables x
            invariants
              @inv1 x ∈ ℕ
              theorem @thm1 x ≥ 0
            end
        """.trimIndent()

        val result = parser.parse(input, "M.eventb")
        val m = result.machine!!

        assertThat(m.invariants).hasSize(2)
        assertThat(m.invariants[0].theorem).isFalse()
        assertThat(m.invariants[1].theorem).isTrue()
        assertThat(m.invariants[1].label).isEqualTo("thm1")
    }

    @Test
    fun `parses machine with variant`() {
        val input = """
            machine M
            variables n
            invariants
              @inv1 n ∈ ℕ
            variant n
            events
              event dec
              then
                @act1 n ≔ n − 1
              end
            end
        """.trimIndent()

        val result = parser.parse(input, "M.eventb")
        val m = result.machine!!

        assertThat(m.variant).isNotNull
        assertThat(m.variant!!.expression).isEqualTo("n")
    }

    @Test
    fun `parses events with parameters guards and actions`() {
        val input = """
            machine M
            variables n
            invariants
              @inv1 n ∈ ℕ
            events
              event INITIALISATION
              then
                @act1 n ≔ 0
              end
              event increment
                any x
                where
                  @grd1 x ∈ ℕ
                then
                  @act1 n ≔ n + x
              end
            end
        """.trimIndent()

        val result = parser.parse(input, "M.eventb")
        val m = result.machine!!

        assertThat(m.events).hasSize(2)

        val init = m.events[0]
        assertThat(init.label).isEqualTo("INITIALISATION")
        assertThat(init.actions).hasSize(1)
        assertThat(init.actions[0].label).isEqualTo("act1")
        assertThat(init.actions[0].assignment).isEqualTo("n ≔ 0")

        val inc = m.events[1]
        assertThat(inc.label).isEqualTo("increment")
        assertThat(inc.parameters).hasSize(1)
        assertThat(inc.parameters[0].identifier).isEqualTo("x")
        assertThat(inc.guards).hasSize(1)
        assertThat(inc.guards[0].label).isEqualTo("grd1")
        assertThat(inc.guards[0].predicate).isEqualTo("x ∈ ℕ")
        assertThat(inc.actions).hasSize(1)
    }

    @Test
    fun `parses event with witnesses`() {
        val input = """
            machine M
            refines Base
            events
              event foo
              refines bar
                with
                  @wit1 y = x + 1
                then
                  @act1 x ≔ x + 1
              end
            end
        """.trimIndent()

        val result = parser.parse(input, "M.eventb")
        val m = result.machine!!

        val evt = m.events[0]
        assertThat(evt.refinesEvents).containsExactly("bar")
        assertThat(evt.witnesses).hasSize(1)
        assertThat(evt.witnesses[0].label).isEqualTo("wit1")
        assertThat(evt.witnesses[0].predicate).isEqualTo("y = x + 1")
    }

    @Test
    fun `parses convergent event`() {
        val input = """
            machine M
            events
              convergent event dec
              then
                @act1 skip
              end
            end
        """.trimIndent()

        val result = parser.parse(input, "M.eventb")
        val evt = result.machine!!.events[0]

        assertThat(evt.convergence).isEqualTo(Convergence.CONVERGENT)
    }

    @Test
    fun `parses anticipated event`() {
        val input = """
            machine M
            events
              anticipated event wait
              then
                @act1 skip
              end
            end
        """.trimIndent()

        val result = parser.parse(input, "M.eventb")
        val evt = result.machine!!.events[0]

        assertThat(evt.convergence).isEqualTo(Convergence.ANTICIPATED)
    }

    @Test
    fun `parses extended event`() {
        val input = """
            machine M
            refines Base
            events
              event foo
              extends bar
              end
            end
        """.trimIndent()

        val result = parser.parse(input, "M.eventb")
        val evt = result.machine!!.events[0]

        assertThat(evt.extended).isTrue()
        assertThat(evt.refinesEvents).containsExactly("bar")
    }

    @Test
    fun `parses theorem guard`() {
        val input = """
            machine M
            variables x
            invariants
              @inv1 x ∈ ℕ
            events
              event foo
                where
                  @grd1 x > 0
                  theorem @grd2 x ≥ 1
                then
                  @act1 x ≔ x − 1
              end
            end
        """.trimIndent()

        val result = parser.parse(input, "M.eventb")
        val evt = result.machine!!.events[0]

        assertThat(evt.guards).hasSize(2)
        assertThat(evt.guards[0].theorem).isFalse()
        assertThat(evt.guards[1].theorem).isTrue()
    }

    @Test
    fun `parses minimal context`() {
        val input = """
            context MyCtx
            end
        """.trimIndent()

        val result = parser.parse(input, "project/MyCtx.eventb")

        assertThat(result.context).isNotNull
        assertThat(result.machine).isNull()
        assertThat(result.errors).isEmpty()
        assertThat(result.context!!.name).isEqualTo("MyCtx")
        assertThat(result.context.filePath).isEqualTo("project/MyCtx.eventb")
    }

    @Test
    fun `parses context with extends sets constants axioms`() {
        val input = """
            context ExtCtx
            extends BaseCtx
            sets S
            constants c
            axioms
              @axm1 c ∈ S
            end
        """.trimIndent()

        val result = parser.parse(input, "ExtCtx.eventb")
        val ctx = result.context!!

        assertThat(ctx.extendsContexts).containsExactly("BaseCtx")
        assertThat(ctx.carrierSets).hasSize(1)
        assertThat(ctx.carrierSets[0].identifier).isEqualTo("S")
        assertThat(ctx.constants).hasSize(1)
        assertThat(ctx.constants[0].identifier).isEqualTo("c")
        assertThat(ctx.axioms).hasSize(1)
        assertThat(ctx.axioms[0].label).isEqualTo("axm1")
        assertThat(ctx.axioms[0].predicate).isEqualTo("c ∈ S")
        assertThat(ctx.axioms[0].theorem).isFalse()
    }

    @Test
    fun `parses theorem axiom`() {
        val input = """
            context Ctx
            constants c
            axioms
              @axm1 c ∈ ℕ
              theorem @thm1 c ≥ 0
            end
        """.trimIndent()

        val result = parser.parse(input, "Ctx.eventb")
        val ctx = result.context!!

        assertThat(ctx.axioms).hasSize(2)
        assertThat(ctx.axioms[0].theorem).isFalse()
        assertThat(ctx.axioms[1].theorem).isTrue()
        assertThat(ctx.axioms[1].label).isEqualTo("thm1")
    }

    @Test
    fun `conversion failures are reported as parse errors`() {
        val parser = object : CamilleParser() {
            override fun convertMachine(node: AMachineParseUnit, filePath: String): Machine = error("boom")
        }

        val result = parser.parse(
            """
            machine M
            end
            """.trimIndent(),
            "M.eventb",
        )

        assertThat(result.machine).isNull()
        assertThat(result.context).isNull()
        assertThat(result.errors).hasSize(1)
        assertThat(result.errors[0].severity).isEqualTo(ValidationSeverity.ERROR)
        assertThat(result.errors[0].ruleId).isEqualTo("EB004")
        assertThat(result.errors[0].message).contains("failed to convert parse tree").contains("boom")
    }

    @Test
    fun `parseFile with single component returns same result`() {
        val input = """
            machine M
            variables x
            invariants
              @inv1 x ∈ ℕ
            end
        """.trimIndent()

        val result = parser.parseFile(input, "project/M.eventb")

        assertThat(result.errors).isEmpty()
        assertThat(result.machines).hasSize(1)
        assertThat(result.contexts).isEmpty()
        assertThat(result.machines[0].name).isEqualTo("M")
        assertThat(result.machines[0].filePath).isEqualTo("project/M.eventb")
    }

    @Test
    fun `parseFile with context and machine returns both`() {
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

        val result = parser.parseFile(input, "project/models.eventb")

        assertThat(result.errors).isEmpty()
        assertThat(result.contexts).hasSize(1)
        assertThat(result.machines).hasSize(1)
        assertThat(result.contexts[0].name).isEqualTo("Limits")
        assertThat(result.contexts[0].filePath).isEqualTo("project/models.eventb[Limits]")
        assertThat(result.machines[0].name).isEqualTo("Counter")
        assertThat(result.machines[0].filePath).isEqualTo("project/models.eventb[Counter]")
    }

    @Test
    fun `parseFile reports errors per chunk`() {
        val input = """
            context Good
            sets S
            end

            machine Bad {invalid
            end
        """.trimIndent()

        val result = parser.parseFile(input, "project/mixed.eventb")

        assertThat(result.contexts).hasSize(1)
        assertThat(result.contexts[0].name).isEqualTo("Good")
        assertThat(result.errors).isNotEmpty
        assertThat(result.errors[0].filePath).isEqualTo("project/mixed.eventb[Bad]")
    }

    @Test
    fun `reports error for invalid syntax`() {
        val input = "this is not valid eventb syntax {"

        val result = parser.parse(input, "bad.eventb")

        assertThat(result.machine).isNull()
        assertThat(result.context).isNull()
        assertThat(result.errors).hasSize(1)
        assertThat(result.errors[0].severity).isEqualTo(ValidationSeverity.ERROR)
        assertThat(result.errors[0].filePath).isEqualTo("bad.eventb")
        assertThat(result.errors[0].message).contains("Camille parse error")
    }

    @Test
    fun `parses uppercase keywords`() {
        val input = """
            CONTEXT Ctx
            SETS S
            CONSTANTS c
            AXIOMS
              @axm1 c ∈ S
            END
        """.trimIndent()

        val result = parser.parse(input, "Ctx.eventb")

        assertThat(result.errors).isEmpty()
        assertThat(result.context).isNotNull
        assertThat(result.context!!.name).isEqualTo("Ctx")
        assertThat(result.context.carrierSets).hasSize(1)
        assertThat(result.context.constants).hasSize(1)
        assertThat(result.context.axioms).hasSize(1)
    }

    @Test
    fun `parses uppercase machine with events`() {
        val input = """
            MACHINE M
            VARIABLES n
            INVARIANTS
              @inv1 n ∈ ℕ
            EVENTS
              EVENT INITIALISATION
              THEN
                @act1 n ≔ 0
              END
              EVENT increment
                ANY x
                WHERE
                  @grd1 x ∈ ℕ
                THEN
                  @act1 n ≔ n + x
              END
            END
        """.trimIndent()

        val result = parser.parse(input, "M.eventb")

        assertThat(result.errors).isEmpty()
        assertThat(result.machine).isNotNull
        assertThat(result.machine!!.name).isEqualTo("M")
        assertThat(result.machine.variables).hasSize(1)
        assertThat(result.machine.events).hasSize(2)
    }

    @Test
    fun `parseFile with uppercase multi-unit file`() {
        val input = """
            CONTEXT Limits
            CONSTANTS lim
            AXIOMS
              @axm1 lim ∈ ℕ
            END

            MACHINE Counter
            SEES Limits
            VARIABLES n
            INVARIANTS
              @inv1 n ∈ ℕ
            EVENTS
              EVENT INITIALISATION
              THEN
                @act1 n ≔ 0
              END
            END
        """.trimIndent()

        val result = parser.parseFile(input, "project/models.eventb")

        assertThat(result.errors).isEmpty()
        assertThat(result.contexts).hasSize(1)
        assertThat(result.machines).hasSize(1)
        assertThat(result.contexts[0].name).isEqualTo("Limits")
        assertThat(result.machines[0].name).isEqualTo("Counter")
    }

    @Test
    fun `rejects comma-separated parameters in any block`() {
        // Comma-separated declaration lists are not valid in any real Event-B tool
        // (Camille/CamilleX/Rodin use whitespace separation). The checker must report
        // a parse error rather than silently rewriting the commas.
        val input = """
            MACHINE M
            VARIABLES n
            INVARIANTS
              @inv1 n ∈ ℕ
            EVENTS
              EVENT INITIALISATION
              THEN
                @act1 n ≔ 0
              END
              EVENT add
                ANY x, y
                WHERE
                  @grd1 x ∈ ℕ
                  @grd2 y ∈ ℕ
                THEN
                  @act1 n ≔ n + x + y
              END
            END
        """.trimIndent()

        val result = parser.parse(input, "M.eventb")

        assertThat(result.machine).isNull()
        assertThat(result.errors).isNotEmpty
        assertThat(result.errors[0].severity).isEqualTo(ValidationSeverity.ERROR)
        assertThat(result.errors[0].message).contains("Camille parse error")
    }

    @Test
    fun `normalizeTheoremOrder swaps label and theorem keyword`() {
        val input = "        @grd27 theorem ∀i·i ∈ ℕ ⇒ x > 0"
        assertThat(CamilleParser.normalizeTheoremOrder(input))
            .isEqualTo("        theorem @grd27 ∀i·i ∈ ℕ ⇒ x > 0")
    }

    @Test
    fun `normalizeTheoremOrder leaves non-theorem lines unchanged`() {
        val input = "        @grd1 x ∈ ℕ"
        assertThat(CamilleParser.normalizeTheoremOrder(input)).isEqualTo(input)
    }

    @Test
    fun `parses theorem guard with label-first notation`() {
        val input = """
            MACHINE M
            VARIABLES x
            INVARIANTS
              @inv1 x ∈ ℕ
            EVENTS
              EVENT foo
                WHERE
                  @grd1 x > 0
                  @grd2 THEOREM x ≥ 1
                THEN
                  @act1 x ≔ x − 1
              END
            END
        """.trimIndent()

        val result = parser.parse(input, "M.eventb")

        assertThat(result.errors).isEmpty()
        val evt = result.machine!!.events[0]
        assertThat(evt.guards).hasSize(2)
        assertThat(evt.guards[0].theorem).isFalse()
        assertThat(evt.guards[1].theorem).isTrue()
        assertThat(evt.guards[1].label).isEqualTo("grd2")
    }

    @Test
    fun `handles line comments correctly`() {
        val input = """
            machine M // this is a comment
            variables n
            invariants
              @inv1 n ∈ ℕ // type constraint
            end
        """.trimIndent()

        val result = parser.parse(input, "M.eventb")

        assertThat(result.errors).isEmpty()
        assertThat(result.machine).isNotNull
        assertThat(result.machine!!.variables).hasSize(1)
    }

    @Test
    fun `handles block comments correctly`() {
        val input = """
            machine M /* block comment */
            variables n /* declared here */
            invariants
              @inv1 n ∈ ℕ /* type constraint */
            end
        """.trimIndent()

        val result = parser.parse(input, "M.eventb")

        assertThat(result.errors).isEmpty()
        assertThat(result.machine).isNotNull
        assertThat(result.machine!!.variables).hasSize(1)
    }
}
