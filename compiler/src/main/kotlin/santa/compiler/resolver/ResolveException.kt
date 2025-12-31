package santa.compiler.resolver

import santa.compiler.error.SantaException
import santa.compiler.lexer.SourcePosition

class ResolveException(
    message: String,
    position: SourcePosition,
) : SantaException(message, position, "ResolveError")
