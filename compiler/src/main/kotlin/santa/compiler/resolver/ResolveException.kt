package santa.compiler.resolver

import santa.compiler.lexer.SourcePosition

class ResolveException(
    message: String,
    val position: SourcePosition,
) : RuntimeException(message)
