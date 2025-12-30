package santa.compiler.lexer

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

class LexerSpanTest {
    @Test
    fun tracks_line_and_column_spans() {
        val source = """
            let x = 10
            x = x + 2
            // ok
            x>=12
        """.trimIndent()

        val tokens = Lexer(source).lexIncludingComments()

        tokens[0].span shouldBe span(1, 1, 1, 4)
        tokens[3].span shouldBe span(1, 9, 1, 11)
        tokens[4].span shouldBe span(1, 11, 2, 1)
        tokens[11].span shouldBe span(3, 1, 3, 6)
        tokens[14].span shouldBe span(4, 2, 4, 4)
        tokens[15].span shouldBe span(4, 4, 4, 6)
    }

    private fun span(startLine: Int, startColumn: Int, endLine: Int, endColumn: Int): Span {
        return Span(
            SourcePosition(startLine, startColumn),
            SourcePosition(endLine, endColumn),
        )
    }
}
