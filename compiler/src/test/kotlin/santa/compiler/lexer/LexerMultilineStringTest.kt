package santa.compiler.lexer

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

class LexerMultilineStringTest {
    @Test
    fun `lexes multiline string literal`() {
        val source = """"line 1
line 2
line 3""""

        val tokens = Lexer(source).lex()

        tokens.size shouldBe 2 // STRING + EOF
        tokens[0].type shouldBe TokenType.STRING
        tokens[0].lexeme shouldBe "\"line 1\nline 2\nline 3\""
    }

    @Test
    fun `tracks spans correctly for multiline strings`() {
        val source = """let s = "hello
world"
let x = 1"""

        val tokens = Lexer(source).lex()

        // let = tokens[0]
        tokens[0].span shouldBe span(1, 1, 1, 4)
        // s = tokens[1]
        tokens[1].span shouldBe span(1, 5, 1, 6)
        // = = tokens[2]
        tokens[2].span shouldBe span(1, 7, 1, 8)
        // "hello\nworld" = tokens[3], starts at (1, 9), ends at (2, 7)
        tokens[3].type shouldBe TokenType.STRING
        tokens[3].span shouldBe span(1, 9, 2, 7)
        // NEWLINE after the string = tokens[4], at (2, 7) to (3, 1)
        tokens[4].type shouldBe TokenType.NEWLINE
        tokens[4].span shouldBe span(2, 7, 3, 1)
        // let = tokens[5]
        tokens[5].span shouldBe span(3, 1, 3, 4)
    }

    @Test
    fun `allows empty lines in multiline strings`() {
        val source = """"1000
2000

4000""""

        val tokens = Lexer(source).lex()

        tokens[0].type shouldBe TokenType.STRING
        tokens[0].lexeme shouldBe "\"1000\n2000\n\n4000\""
    }

    @Test
    fun `combines escape sequences with literal newlines`() {
        // Input contains: quote, "line1", backslash, 'n', literal newline, "line2", quote
        val source = "\"line1\\n\nline2\""

        val tokens = Lexer(source).lex()

        tokens[0].type shouldBe TokenType.STRING
        // The lexeme preserves both: escaped \n and literal newline
        tokens[0].lexeme shouldBe "\"line1\\n\nline2\""
    }

    private fun span(startLine: Int, startColumn: Int, endLine: Int, endColumn: Int): Span {
        return Span(
            SourcePosition(startLine, startColumn),
            SourcePosition(endLine, endColumn),
        )
    }
}
