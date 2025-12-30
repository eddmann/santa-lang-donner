package santa.compiler.parser

import santa.compiler.lexer.SourcePosition

class ParseException(
    message: String,
    val position: SourcePosition,
) : RuntimeException(message)
