package santa.compiler.lexer

data class Token(
    val type: TokenType,
    val lexeme: String,
    val span: Span,
)
