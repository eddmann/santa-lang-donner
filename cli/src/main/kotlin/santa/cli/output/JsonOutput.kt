package santa.cli.output

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * JSON output data classes per LANG.txt Section 16.
 *
 * Value Representation: All values are serialized as strings.
 * This includes nil (as "nil"), numbers, lists (e.g., "[1, 2, 3]"),
 * and all other types using their display format.
 */

// =============================================================================
// Version output
// =============================================================================

/**
 * JSON output for version information.
 */
@Serializable
data class JsonVersionOutput(
    val reindeer: String,
    val version: String
)

// =============================================================================
// Error structures (Section 16.3.5)
// =============================================================================

/**
 * Error location with 1-indexed line and column.
 */
@Serializable
data class ErrorLocation(
    val line: Int,
    val column: Int
)

/**
 * Stack frame for error traces.
 * - Anonymous functions use "<lambda>"
 * - Top-level code uses "<top-level>"
 */
@Serializable
data class StackFrame(
    val function: String,
    val line: Int,
    val column: Int
)

/**
 * JSON output for errors (Section 16.3.5).
 * Replaces entire output when an error occurs in JSON mode.
 */
@Serializable
data class JsonErrorOutput(
    val type: String = "error",
    val message: String,
    val location: ErrorLocation,
    val stack: List<StackFrame>
)

// =============================================================================
// Part result (used by solution)
// =============================================================================

/**
 * Result of executing a single part (part_one or part_two).
 */
@Serializable
data class JsonPartResult(
    val status: String,
    val value: String,
    val duration_ms: Long
)

// =============================================================================
// Solution output (Section 16.3.1)
// =============================================================================

/**
 * JSON output for solution execution.
 *
 * If a solution only defines part_one (no part_two), the part_two field
 * is omitted entirely from the output, and vice versa.
 */
@Serializable
data class JsonSolutionOutput(
    val type: String = "solution",
    val status: String,
    @SerialName("part_one")
    val partOne: JsonPartResult? = null,
    @SerialName("part_two")
    val partTwo: JsonPartResult? = null,
    val console: List<ConsoleEntry>
)

// =============================================================================
// Script output (Section 16.3.2)
// =============================================================================

/**
 * JSON output for script execution.
 * The value is the result of the last expression.
 */
@Serializable
data class JsonScriptOutput(
    val type: String = "script",
    val status: String,
    val value: String,
    val duration_ms: Long,
    val console: List<ConsoleEntry>
)

// =============================================================================
// Test output (Section 16.3.3)
// =============================================================================

/**
 * Test part result with pass/fail information.
 */
@Serializable
data class JsonTestPartResult(
    val passed: Boolean,
    val expected: String,
    val actual: String
)

/**
 * Individual test case result.
 *
 * Test status values:
 * - "complete": Test ran successfully (may have passed or failed assertions)
 * - "skipped": Test marked with @slow and -s flag not provided
 */
@Serializable
data class JsonTestCase(
    val index: Int,
    val slow: Boolean,
    val status: String,
    @SerialName("part_one")
    val partOne: JsonTestPartResult? = null,
    @SerialName("part_two")
    val partTwo: JsonTestPartResult? = null
)

/**
 * Test summary counts.
 * A test is counted as passed only if ALL its parts pass.
 */
@Serializable
data class TestSummary(
    val total: Int,
    val passed: Int,
    val failed: Int,
    val skipped: Int
)

/**
 * JSON output for test execution.
 */
@Serializable
data class JsonTestOutput(
    val type: String = "test",
    val status: String,
    val success: Boolean,
    val summary: TestSummary,
    val tests: List<JsonTestCase>,
    val console: List<ConsoleEntry>
)
