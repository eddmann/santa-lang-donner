package santa.compiler.parser

import santa.compiler.error.SantaException
import santa.compiler.lexer.SourcePosition

class ParseException(
    message: String,
    position: SourcePosition,
) : SantaException(message, position, "SyntaxError")
