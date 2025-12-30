package santa.compiler.lexer

class LexingException(
    message: String,
    val position: SourcePosition,
) : RuntimeException(message)
