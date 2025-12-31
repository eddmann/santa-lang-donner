package santa.compiler.lexer

import santa.compiler.error.SantaException

class LexingException(
    message: String,
    position: SourcePosition,
) : SantaException(message, position, "LexError")
