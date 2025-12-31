package santa.compiler.error

import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import santa.compiler.lexer.SourcePosition

/**
 * Tests for ErrorFormatter which produces clear, actionable error messages
 * with source context including the problematic line and position indicator.
 */
class ErrorFormatterTest {

    @Nested
    inner class FormatWithSource {
        @Test
        fun `formats error with source line and position indicator`() {
            val source = "let x = 1 + \"hello\""
            val position = SourcePosition(line = 1, column = 13)
            val message = "Cannot add Integer and String"

            val result = ErrorFormatter.format(message, position, source)

            result shouldContain "line 1, column 13"
            result shouldContain message
            result shouldContain "let x = 1 + \"hello\""
            result shouldContain "^" // Position indicator
        }

        @Test
        fun `positions caret correctly at start of line`() {
            val source = "x"
            val position = SourcePosition(line = 1, column = 1)
            val message = "Undefined identifier 'x'"

            val result = ErrorFormatter.format(message, position, source)

            result shouldContain "x"
            result shouldContain "^"
        }

        @Test
        fun `positions caret correctly in middle of line`() {
            val source = "let x = undefined_var"
            val position = SourcePosition(line = 1, column = 9)
            val message = "Undefined identifier 'undefined_var'"

            val result = ErrorFormatter.format(message, position, source)

            val lines = result.lines()
            val sourceLine = lines.find { it.contains("let x = undefined_var") }!!
            val caretLine = lines[lines.indexOf(sourceLine) + 1]

            // Caret line format: "1 | " (4 chars for line 1) + 8 spaces (column 9 - 1) + "^"
            // So caret should be at index 12: "1 | " (4 chars) + 8 spaces = index 12
            caretLine.indexOf('^') shouldBe 12
        }

        @Test
        fun `handles multiline source correctly`() {
            val source = """
                let a = 1
                let b = 2 / 0
                let c = 3
            """.trimIndent()
            val position = SourcePosition(line = 2, column = 13)
            val message = "Division by zero"

            val result = ErrorFormatter.format(message, position, source)

            result shouldContain "line 2, column 13"
            result shouldContain "let b = 2 / 0"
            result shouldContain message
        }

        @Test
        fun `handles position beyond source gracefully`() {
            val source = "let x = 1"
            val position = SourcePosition(line = 5, column = 1)
            val message = "Some error"

            val result = ErrorFormatter.format(message, position, source)

            result shouldContain "line 5, column 1"
            result shouldContain message
            // Should not crash, just omit source context
        }

        @Test
        fun `handles empty source gracefully`() {
            val source = ""
            val position = SourcePosition(line = 1, column = 1)
            val message = "Unexpected end of input"

            val result = ErrorFormatter.format(message, position, source)

            result shouldContain "line 1, column 1"
            result shouldContain message
        }

        @Test
        fun `includes error type prefix when provided`() {
            val source = "let x = 1 + \"hello\""
            val position = SourcePosition(line = 1, column = 13)
            val message = "Cannot add Integer and String"

            val result = ErrorFormatter.format(message, position, source, errorType = "RuntimeError")

            result shouldContain "RuntimeError"
            result shouldContain message
        }
    }

    @Nested
    inner class FormatWithoutSource {
        @Test
        fun `formats error with position but no source`() {
            val position = SourcePosition(line = 3, column = 7)
            val message = "Type mismatch"

            val result = ErrorFormatter.format(message, position)

            result shouldContain "line 3, column 7"
            result shouldContain message
        }

        @Test
        fun `formats error with null position`() {
            val message = "Internal error"

            val result = ErrorFormatter.format(message, position = null)

            result shouldContain message
            // Should not crash with null position
        }
    }

    @Nested
    inner class LineExtraction {
        @Test
        fun `extracts correct line from source`() {
            val source = "line1\nline2\nline3"

            ErrorFormatter.extractLine(source, 1) shouldBe "line1"
            ErrorFormatter.extractLine(source, 2) shouldBe "line2"
            ErrorFormatter.extractLine(source, 3) shouldBe "line3"
        }

        @Test
        fun `returns null for out of range line numbers`() {
            val source = "line1\nline2"

            ErrorFormatter.extractLine(source, 0) shouldBe null
            ErrorFormatter.extractLine(source, 5) shouldBe null
        }

        @Test
        fun `handles single line source`() {
            val source = "only one line"

            ErrorFormatter.extractLine(source, 1) shouldBe "only one line"
            ErrorFormatter.extractLine(source, 2) shouldBe null
        }

        @Test
        fun `handles empty source`() {
            val source = ""

            ErrorFormatter.extractLine(source, 1) shouldBe null
        }

        @Test
        fun `handles windows line endings`() {
            val source = "line1\r\nline2\r\nline3"

            ErrorFormatter.extractLine(source, 1) shouldBe "line1"
            ErrorFormatter.extractLine(source, 2) shouldBe "line2"
            ErrorFormatter.extractLine(source, 3) shouldBe "line3"
        }
    }
}
