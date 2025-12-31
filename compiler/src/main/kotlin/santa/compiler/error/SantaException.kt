package santa.compiler.error

import santa.compiler.lexer.SourcePosition

/**
 * Base exception class for all santa-lang errors.
 *
 * Provides unified error formatting with source context, position tracking,
 * and consistent error type labeling.
 *
 * @param message The error message
 * @param position Source position where the error occurred (may be null for runtime errors)
 * @param errorType Label for the type of error (e.g., "SyntaxError", "RuntimeError")
 */
open class SantaException(
    override val message: String,
    val position: SourcePosition?,
    val errorType: String = "Error"
) : RuntimeException(message) {

    /**
     * Format this exception with optional source context.
     *
     * @param source The source code for context display
     * @return Formatted error string with source location and context
     */
    fun formatWithSource(source: String?): String =
        ErrorFormatter.format(message, position, source, errorType)
}

/**
 * Exception for lexer errors (unterminated strings, invalid characters, etc.)
 */
class SantaLexingException(
    message: String,
    position: SourcePosition
) : SantaException(message, position, "LexError")

/**
 * Exception for parser errors (syntax errors, unexpected tokens, etc.)
 */
class SantaParseException(
    message: String,
    position: SourcePosition
) : SantaException(message, position, "SyntaxError")

/**
 * Exception for resolver errors (undefined identifiers, shadowing built-ins, etc.)
 */
class SantaResolveException(
    message: String,
    position: SourcePosition
) : SantaException(message, position, "ResolveError")

/**
 * Exception for runtime errors (type mismatches, division by zero, etc.)
 *
 * Runtime errors may not have a source position when the error occurs
 * deep in the runtime (e.g., in a built-in function).
 */
class SantaRuntimeError(
    message: String,
    position: SourcePosition? = null
) : SantaException(message, position, "RuntimeError")
