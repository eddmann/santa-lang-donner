package santa.compiler.lexer

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

class LexerCommentTest {
    @Test
    fun lex_omits_comments_by_default() {
        val source = """
            // comment
            let x = 1
        """.trimIndent()

        val tokens = Lexer(source).lex()

        tokens.any { it.type == TokenType.COMMENT } shouldBe false
    }

    @Test
    fun lex_including_comments_returns_comment_tokens() {
        val source = """
            // comment
            let x = 1
        """.trimIndent()

        val tokens = Lexer(source).lexIncludingComments()

        tokens.any { it.type == TokenType.COMMENT } shouldBe true
    }
}
