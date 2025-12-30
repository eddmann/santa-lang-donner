package santa.compiler.lexer

data class Span(
    val start: SourcePosition,
    val end: SourcePosition,
)
