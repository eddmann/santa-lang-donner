package santa.cli.output

import io.kotest.matchers.shouldBe
import kotlinx.collections.immutable.persistentListOf
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import santa.compiler.codegen.Compiler
import santa.runtime.Builtins
import santa.runtime.value.BoolValue
import santa.runtime.value.IntValue
import santa.runtime.value.ListValue
import santa.runtime.value.NilValue
import santa.runtime.value.StringValue

/**
 * Unit tests for internal edge cases that are not easily tested via integration tests.
 * These test specific formatting behaviors and internal state management.
 */
class JsonOutputEdgeCasesTest {

    @BeforeEach
    fun setUp() {
        ConsoleCapture.enable()
        Builtins.putsHook = { message -> ConsoleCapture.add(message) }
    }

    @AfterEach
    fun tearDown() {
        Builtins.putsHook = null
        ConsoleCapture.disable()
    }

    @Nested
    inner class PutsEdgeCases {

        @Test
        fun `puts with multiple values produces single message`() {
            val compiled = Compiler.compile("""puts(1, nil, [1, 2]); 0""")
            compiled.execute()
            val console = ConsoleCapture.disable()

            console.size shouldBe 1
            console[0].message shouldBe "1 nil [1, 2]"
        }

        @Test
        fun `puts with no arguments emits nothing`() {
            val compiled = Compiler.compile("""puts(); 42""")
            compiled.execute()
            val console = ConsoleCapture.disable()

            console.size shouldBe 0
        }
    }

    @Nested
    inner class ConsoleCaptureEdgeCases {

        @Test
        fun `console entries have timestamps in increasing order`() {
            val compiled = Compiler.compile("""
                puts("first");
                puts("second");
                1
            """.trimIndent())
            compiled.execute()
            val console = ConsoleCapture.disable()

            console.size shouldBe 2
            console[0].message shouldBe "first"
            console[1].message shouldBe "second"
            (console[1].timestamp_ms >= console[0].timestamp_ms) shouldBe true
        }

        @Test
        fun `console disabled returns empty list`() {
            ConsoleCapture.disable()
            val console = ConsoleCapture.disable()

            console.size shouldBe 0
        }
    }

    @Nested
    inner class ValueFormatting {

        @Test
        fun `format integer`() {
            JsonFormatter.formatValue(IntValue(42)) shouldBe "42"
        }

        @Test
        fun `format nil as string`() {
            JsonFormatter.formatValue(NilValue) shouldBe "nil"
        }

        @Test
        fun `format list as string`() {
            val list = ListValue(
                persistentListOf(
                    IntValue(1),
                    IntValue(2),
                    IntValue(3)
                )
            )
            JsonFormatter.formatValue(list) shouldBe "[1, 2, 3]"
        }

        @Test
        fun `format string without extra quotes`() {
            JsonFormatter.formatValue(StringValue("hello")) shouldBe "hello"
        }

        @Test
        fun `format boolean`() {
            JsonFormatter.formatValue(BoolValue(true)) shouldBe "true"
            JsonFormatter.formatValue(BoolValue(false)) shouldBe "false"
        }
    }
}
