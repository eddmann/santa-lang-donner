package santa.cli.output

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.encodeToJsonElement

/**
 * JSONL streaming output per LANG.txt Section 16.4.
 *
 * Outputs JSON Lines for real-time progress updates:
 * - First line: Initial state with "status": "pending" fields
 * - Subsequent lines: RFC 6902 JSON Patch arrays
 */

// =============================================================================
// JSON Patch (RFC 6902)
// =============================================================================

/**
 * RFC 6902 JSON Patch operation.
 */
@Serializable
data class JsonPatch(
    val op: String,
    val path: String,
    val value: JsonElement
)

// =============================================================================
// Initial state structures
// =============================================================================

/**
 * Initial part state for JSONL streaming.
 * Fields are null until execution completes.
 */
@Serializable
data class JsonlPartInitial(
    val status: String = "pending",
    val value: String? = null,
    val duration_ms: Long? = null
)

/**
 * Initial solution state for JSONL streaming (Section 16.4.1).
 */
@Serializable
data class JsonlSolutionInitial(
    val type: String = "solution",
    val status: String = "pending",
    @SerialName("part_one")
    val partOne: JsonlPartInitial? = null,
    @SerialName("part_two")
    val partTwo: JsonlPartInitial? = null,
    val console: List<ConsoleEntry> = emptyList()
)

/**
 * Initial script state for JSONL streaming (Section 16.4.2).
 */
@Serializable
data class JsonlScriptInitial(
    val type: String = "script",
    val status: String = "pending",
    val value: String? = null,
    val duration_ms: Long? = null,
    val console: List<ConsoleEntry> = emptyList()
)

/**
 * Initial test case state for JSONL streaming.
 */
@Serializable
data class JsonlTestCaseInitial(
    val index: Int,
    val slow: Boolean,
    val status: String = "pending",
    @SerialName("part_one")
    val partOne: JsonTestPartResult? = null,
    @SerialName("part_two")
    val partTwo: JsonTestPartResult? = null
)

/**
 * Initial test state for JSONL streaming (Section 16.4.3).
 */
@Serializable
data class JsonlTestInitial(
    val type: String = "test",
    val status: String = "pending",
    val success: Boolean? = null,
    val summary: TestSummary,
    val tests: List<JsonlTestCaseInitial>,
    val console: List<ConsoleEntry> = emptyList()
)

/**
 * Error object for JSONL error patches.
 */
@Serializable
data class JsonlError(
    val message: String,
    val location: ErrorLocation,
    val stack: List<StackFrame>
)

// =============================================================================
// JSONL Writer
// =============================================================================

/**
 * Writer for JSONL streaming output.
 * Handles initial state emission and RFC 6902 patches.
 */
class JsonlWriter {
    @PublishedApi
    internal val json = Json {
        encodeDefaults = true
        explicitNulls = true
    }

    /**
     * Write initial state line.
     */
    inline fun <reified T> writeInitial(state: T) {
        println(json.encodeToString(state))
        System.out.flush()
    }

    /**
     * Write a patch array.
     */
    fun writePatches(patches: List<JsonPatch>) {
        println(json.encodeToString(patches))
        System.out.flush()
    }

    /**
     * Create a replace patch.
     */
    inline fun <reified T> replacePatch(path: String, value: T): JsonPatch {
        return JsonPatch("replace", path, json.encodeToJsonElement(value))
    }

    /**
     * Create an add patch (for appending to arrays with path ending in /-).
     */
    inline fun <reified T> addPatch(path: String, value: T): JsonPatch {
        return JsonPatch("add", path, json.encodeToJsonElement(value))
    }
}
