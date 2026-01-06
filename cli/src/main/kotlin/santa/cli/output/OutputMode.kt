package santa.cli.output

/**
 * CLI output mode as defined in LANG.txt Section 16.
 */
enum class OutputMode {
    /** Human-readable output with ANSI colors (default) */
    Text,

    /** Single JSON object after execution completes */
    Json,

    /** Real-time streaming with JSON Lines */
    Jsonl
}
