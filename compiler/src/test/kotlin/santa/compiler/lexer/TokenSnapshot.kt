package santa.compiler.lexer

object TokenSnapshot {
    fun render(tokens: List<Token>): String = tokens.joinToString("\n") { token ->
        val lexeme = token.lexeme
            .replace("\\", "\\\\")
            .replace("\n", "\\n")
            .replace("\t", "\\t")
            .replace("\r", "\\r")

        "${token.type} \"$lexeme\""
    }
}
