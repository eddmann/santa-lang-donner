package santa.cli.output

import kotlinx.serialization.Serializable

/**
 * Console output entry from puts() calls.
 *
 * Per LANG.txt Section 16.3.4:
 * - timestamp_ms: Milliseconds (whole numbers) since execution started
 * - message: The string representation of all values passed to puts(), space-separated
 */
@Serializable
data class ConsoleEntry(
    val timestamp_ms: Long,
    val message: String
)

/**
 * Console capture infrastructure for JSON/JSONL output modes.
 *
 * When enabled, puts() output is captured to a buffer instead of
 * being printed to stdout. Each entry includes a timestamp relative
 * to when capture was enabled.
 */
object ConsoleCapture {
    private var buffer: MutableList<ConsoleEntry>? = null
    private var startTimeMs: Long = 0

    /**
     * Enable console capture mode.
     * Clears any previous buffer and starts the timestamp clock.
     */
    fun enable() {
        startTimeMs = System.currentTimeMillis()
        buffer = mutableListOf()
    }

    /**
     * Disable console capture and return captured entries.
     * Returns an empty list if capture was not enabled.
     */
    fun disable(): List<ConsoleEntry> {
        val entries = buffer?.toList() ?: emptyList()
        buffer = null
        return entries
    }

    /**
     * Check if console capture is currently enabled.
     */
    fun isEnabled(): Boolean = buffer != null

    /**
     * Add a console entry if capture is enabled.
     * Called by the puts() hook when capture is active.
     *
     * Per spec, if puts() is called with no arguments, no event is emitted.
     * This should be handled by the caller.
     */
    fun add(message: String) {
        buffer?.add(
            ConsoleEntry(
                timestamp_ms = System.currentTimeMillis() - startTimeMs,
                message = message
            )
        )
    }

    /**
     * Get the current timestamp in milliseconds since capture started.
     * Useful for other timing purposes.
     */
    fun getTimestampMs(): Long = System.currentTimeMillis() - startTimeMs

    /**
     * Get the start time for external timing calculations.
     */
    fun getStartTimeMs(): Long = startTimeMs
}
