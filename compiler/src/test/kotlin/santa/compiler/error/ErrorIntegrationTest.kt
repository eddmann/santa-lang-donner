package santa.compiler.error

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.string.shouldContain
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import santa.compiler.codegen.Compiler
import santa.compiler.lexer.LexingException
import santa.compiler.parser.ParseException
import santa.compiler.resolver.ResolveException
import santa.runtime.SantaRuntimeException

/**
 * Integration tests for error handling across all compilation phases.
 *
 * These tests verify that:
 * 1. Errors include spans and stack traces
 * 2. Runtime error vs nil behavior is correct
 * 3. Error messages are clear and actionable
 */
class ErrorIntegrationTest {

    @Nested
    inner class LexerErrors {
        @Test
        fun `unterminated string literal has correct position`() {
            val exception = shouldThrow<LexingException> {
                Compiler.compile("let x = \"unterminated")
            }
            exception.message shouldContain "Unterminated string"
            exception.position shouldNotBe null
            exception.position!!.line shouldBe 1
            exception.position!!.column shouldBe 9
        }

        @Test
        fun `invalid escape sequence has correct position`() {
            val exception = shouldThrow<LexingException> {
                Compiler.compile("let x = \"bad\\q\"")
            }
            exception.message shouldContain "Invalid escape"
        }

        @Test
        fun `unexpected character has correct position`() {
            val exception = shouldThrow<LexingException> {
                Compiler.compile("let x = 1 & 2")
            }
            exception.message shouldContain "Unexpected character"
            exception.position!!.column shouldBe 11
        }

        @Test
        fun `error on second line has correct line number`() {
            val source = """
                let a = 1
                let b = "unterminated
            """.trimIndent()
            val exception = shouldThrow<LexingException> {
                Compiler.compile(source)
            }
            exception.position!!.line shouldBe 2
        }
    }

    @Nested
    inner class ParserErrors {
        @Test
        fun `missing expression has correct position`() {
            val exception = shouldThrow<ParseException> {
                Compiler.compile("let x = ")
            }
            exception.message shouldContain "Expected expression"
        }

        @Test
        fun `missing closing paren has correct position`() {
            val exception = shouldThrow<ParseException> {
                Compiler.compile("(1 + 2")
            }
            exception.message shouldContain ")"
        }

        @Test
        fun `missing closing brace has correct position`() {
            val exception = shouldThrow<ParseException> {
                Compiler.compile("{ let x = 1")
            }
            exception.message shouldContain "}"
        }

        @Test
        fun `missing equals in let binding has correct message`() {
            val exception = shouldThrow<ParseException> {
                Compiler.compile("let x 5")
            }
            exception.message shouldContain "="
        }
    }

    @Nested
    inner class ResolverErrors {
        @Test
        fun `undefined identifier has correct position`() {
            val exception = shouldThrow<ResolveException> {
                Compiler.compile("undefined_var")
            }
            exception.message shouldContain "Undefined identifier"
            exception.message shouldContain "undefined_var"
            exception.position!!.line shouldBe 1
            exception.position!!.column shouldBe 1
        }

        @Test
        fun `shadowing builtin has correct message`() {
            val exception = shouldThrow<ResolveException> {
                Compiler.compile("let map = 5")
            }
            exception.message shouldContain "shadow"
            exception.message shouldContain "map"
        }

        @Test
        fun `undefined identifier on later line has correct line`() {
            val source = """
                let a = 1
                let b = 2
                unknown_var
            """.trimIndent()
            val exception = shouldThrow<ResolveException> {
                Compiler.compile(source)
            }
            exception.position!!.line shouldBe 3
        }
    }

    @Nested
    inner class RuntimeErrors {
        @Test
        fun `type mismatch in addition has clear message`() {
            val exception = shouldThrow<SantaRuntimeException> {
                Compiler.compile("1 + \"hello\"").execute()
            }
            exception.message shouldContain "Cannot add"
            exception.message shouldContain "Integer"
            exception.message shouldContain "String"
        }

        @Test
        fun `type mismatch in subtraction has clear message`() {
            val exception = shouldThrow<SantaRuntimeException> {
                Compiler.compile("1 - \"hello\"").execute()
            }
            exception.message shouldContain "subtract"
        }

        @Test
        fun `type mismatch in comparison has clear message`() {
            val exception = shouldThrow<SantaRuntimeException> {
                Compiler.compile("1 < \"hello\"").execute()
            }
            exception.message shouldContain "compare"
        }

        @Test
        fun `negating non-numeric has clear message`() {
            val exception = shouldThrow<SantaRuntimeException> {
                Compiler.compile("-\"hello\"").execute()
            }
            exception.message shouldContain "negate"
        }
    }

    @Nested
    inner class NilBehaviorVsErrors {
        // According to LANG.txt 15.5, these return nil rather than errors

        @Test
        fun `out of bounds list access returns nil`() {
            val result = Compiler.compile("[1, 2, 3][10]").execute()
            result.toString() shouldContain "Nil"
        }

        @Test
        fun `missing dictionary key returns nil`() {
            val result = Compiler.compile("#{\"a\": 1}[\"b\"]").execute()
            result.toString() shouldContain "Nil"
        }

        @Test
        fun `negative index out of bounds returns nil`() {
            val result = Compiler.compile("[1, 2, 3][-10]").execute()
            result.toString() shouldContain "Nil"
        }
    }

    @Nested
    inner class ErrorFormatting {
        @Test
        fun `SantaException formats with source context`() {
            val exception = shouldThrow<SantaException> {
                Compiler.compile("undefined_var")
            }
            val formatted = exception.formatWithSource("undefined_var")

            formatted shouldContain "ResolveError"
            formatted shouldContain "Undefined identifier"
            formatted shouldContain "line 1, column 1"
            formatted shouldContain "undefined_var"
            formatted shouldContain "^"
        }

        @Test
        fun `multiline source shows correct line in context`() {
            val source = """
                let a = 1
                let b = unknown
                let c = 3
            """.trimIndent()

            val exception = shouldThrow<SantaException> {
                Compiler.compile(source)
            }
            val formatted = exception.formatWithSource(source)

            formatted shouldContain "line 2"
            formatted shouldContain "let b = unknown"
        }
    }
}
