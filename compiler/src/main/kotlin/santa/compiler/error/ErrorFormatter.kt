package santa.compiler.error

import santa.compiler.lexer.SourcePosition

/**
 * Formats error messages with source context for clear, actionable output.
 *
 * Produces errors in the format:
 * ```
 * [ErrorType]: message
 *   --> line X, column Y
 *     |
 *   X | source line content
 *     |        ^
 * ```
 */
object ErrorFormatter {

    /**
     * Format an error message with optional source context.
     *
     * @param message The error message
     * @param position The source position where the error occurred
     * @param source Optional source code for context display
     * @param errorType Optional error type prefix (e.g., "SyntaxError", "RuntimeError")
     * @return Formatted error string
     */
    fun format(
        message: String,
        position: SourcePosition?,
        source: String? = null,
        errorType: String? = null
    ): String = buildString {
        // Error type and message
        if (errorType != null) {
            append(errorType)
            append(": ")
        }
        appendLine(message)

        // Position information
        if (position != null) {
            append("  --> line ")
            append(position.line)
            append(", column ")
            appendLine(position.column)

            // Source context
            if (source != null) {
                val sourceLine = extractLine(source, position.line)
                if (sourceLine != null) {
                    val lineNumWidth = position.line.toString().length
                    val gutter = " ".repeat(lineNumWidth)

                    appendLine("$gutter |")
                    appendLine("${position.line} | $sourceLine")
                    append("$gutter | ")
                    append(" ".repeat(maxOf(0, position.column - 1)))
                    appendLine("^")
                }
            }
        }
    }.trimEnd()

    /**
     * Extract a specific line from source code.
     *
     * @param source The source code
     * @param lineNumber 1-based line number
     * @return The line content, or null if line number is out of range
     */
    fun extractLine(source: String, lineNumber: Int): String? {
        if (lineNumber < 1) return null
        if (source.isEmpty()) return null

        // Normalize line endings
        val normalized = source.replace("\r\n", "\n").replace("\r", "\n")
        val lines = normalized.split("\n")

        return if (lineNumber <= lines.size) {
            lines[lineNumber - 1]
        } else {
            null
        }
    }
}
