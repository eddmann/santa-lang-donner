package santa.compiler.error

import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import santa.compiler.lexer.SourcePosition

/**
 * Tests for SantaException base class and its subclasses.
 */
class SantaExceptionTest {

    @Nested
    inner class SantaExceptionBase {
        @Test
        fun `creates exception with message and position`() {
            val position = SourcePosition(line = 5, column = 10)
            val exception = SantaException("Test error", position)

            exception.message shouldBe "Test error"
            exception.position shouldBe position
        }

        @Test
        fun `creates exception with null position`() {
            val exception = SantaException("Test error", null)

            exception.message shouldBe "Test error"
            exception.position shouldBe null
        }

        @Test
        fun `formats error with source context`() {
            val position = SourcePosition(line = 1, column = 5)
            val source = "let x = bad"
            val exception = SantaException("Invalid expression", position)

            val formatted = exception.formatWithSource(source)

            formatted shouldContain "Invalid expression"
            formatted shouldContain "line 1, column 5"
            formatted shouldContain "let x = bad"
        }

        @Test
        fun `formats error without source context`() {
            val position = SourcePosition(line = 1, column = 5)
            val exception = SantaException("Invalid expression", position)

            val formatted = exception.formatWithSource(null)

            formatted shouldContain "Invalid expression"
            formatted shouldContain "line 1, column 5"
        }

        @Test
        fun `formats error with error type`() {
            val position = SourcePosition(line = 1, column = 5)
            val exception = SantaException("Invalid expression", position, errorType = "SyntaxError")

            val formatted = exception.formatWithSource("let x = bad")

            formatted shouldContain "SyntaxError"
            formatted shouldContain "Invalid expression"
        }
    }

    @Nested
    inner class LexingExceptionFormat {
        @Test
        fun `formats lexing error with source`() {
            val position = SourcePosition(line = 2, column = 8)
            val source = "let x = 1\nlet y = \"unclosed"

            val exception = SantaLexingException("Unterminated string literal", position)
            val formatted = exception.formatWithSource(source)

            formatted shouldContain "LexError"
            formatted shouldContain "Unterminated string literal"
            formatted shouldContain "line 2, column 8"
        }
    }

    @Nested
    inner class ParseExceptionFormat {
        @Test
        fun `formats parse error with source`() {
            val position = SourcePosition(line = 1, column = 10)
            val source = "let x = 1 +"

            val exception = SantaParseException("Expected expression", position)
            val formatted = exception.formatWithSource(source)

            formatted shouldContain "SyntaxError"
            formatted shouldContain "Expected expression"
            formatted shouldContain "line 1, column 10"
        }
    }

    @Nested
    inner class ResolveExceptionFormat {
        @Test
        fun `formats resolve error with source`() {
            val position = SourcePosition(line = 1, column = 1)
            val source = "undefined_var"

            val exception = SantaResolveException("Undefined identifier 'undefined_var'", position)
            val formatted = exception.formatWithSource(source)

            formatted shouldContain "ResolveError"
            formatted shouldContain "Undefined identifier"
            formatted shouldContain "line 1, column 1"
        }
    }

    @Nested
    inner class RuntimeExceptionFormat {
        @Test
        fun `formats runtime error with source`() {
            val position = SourcePosition(line = 3, column = 5)
            val source = "let a = 1\nlet b = 2\nlet c = a / 0"

            val exception = SantaRuntimeError("Division by zero", position)
            val formatted = exception.formatWithSource(source)

            formatted shouldContain "RuntimeError"
            formatted shouldContain "Division by zero"
            formatted shouldContain "line 3, column 5"
            formatted shouldContain "let c = a / 0"
        }

        @Test
        fun `formats runtime error without position`() {
            val exception = SantaRuntimeError("Type mismatch: cannot add Integer and String", null)
            val formatted = exception.formatWithSource(null)

            formatted shouldContain "RuntimeError"
            formatted shouldContain "Type mismatch"
        }
    }
}
