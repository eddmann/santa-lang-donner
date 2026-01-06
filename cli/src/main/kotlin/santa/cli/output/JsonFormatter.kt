package santa.cli.output

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import santa.compiler.error.SantaException
import santa.compiler.lexer.SourcePosition
import santa.runtime.value.*

/**
 * Formats execution results as JSON per LANG.txt Section 16.3.
 */
object JsonFormatter {
    private val json = Json {
        encodeDefaults = true
        // Don't emit null fields per spec (part_one/part_two omitted when not defined)
        explicitNulls = false
    }

    /**
     * Format a solution result as JSON.
     */
    fun formatSolution(
        partOne: PartResult?,
        partTwo: PartResult?,
        console: List<ConsoleEntry>
    ): String {
        val output = JsonSolutionOutput(
            status = "complete",
            partOne = partOne?.let {
                JsonPartResult("complete", it.value, it.durationMs)
            },
            partTwo = partTwo?.let {
                JsonPartResult("complete", it.value, it.durationMs)
            },
            console = console
        )
        return json.encodeToString(output)
    }

    /**
     * Format a script result as JSON.
     */
    fun formatScript(
        value: String,
        durationMs: Long,
        console: List<ConsoleEntry>
    ): String {
        val output = JsonScriptOutput(
            status = "complete",
            value = value,
            duration_ms = durationMs,
            console = console
        )
        return json.encodeToString(output)
    }

    /**
     * Format test results as JSON.
     */
    fun formatTest(
        testResults: List<TestJsonResult>,
        hasPartOne: Boolean,
        hasPartTwo: Boolean,
        console: List<ConsoleEntry>
    ): String {
        var passed = 0
        var failed = 0
        var skipped = 0

        val tests = testResults.mapIndexed { i, result ->
            when {
                result.skipped -> {
                    skipped++
                    JsonTestCase(
                        index = i + 1,
                        slow = result.slow,
                        status = "skipped"
                    )
                }
                else -> {
                    val allPassed = (result.partOnePassed ?: true) &&
                        (result.partTwoPassed ?: true)
                    if (allPassed) passed++ else failed++

                    JsonTestCase(
                        index = i + 1,
                        slow = result.slow,
                        status = "complete",
                        partOne = if (hasPartOne && result.partOneExpected != null) {
                            JsonTestPartResult(
                                passed = result.partOnePassed ?: true,
                                expected = result.partOneExpected,
                                actual = result.partOneActual ?: ""
                            )
                        } else null,
                        partTwo = if (hasPartTwo && result.partTwoExpected != null) {
                            JsonTestPartResult(
                                passed = result.partTwoPassed ?: true,
                                expected = result.partTwoExpected,
                                actual = result.partTwoActual ?: ""
                            )
                        } else null
                    )
                }
            }
        }

        val output = JsonTestOutput(
            status = "complete",
            success = failed == 0,
            summary = TestSummary(
                total = testResults.size,
                passed = passed,
                failed = failed,
                skipped = skipped
            ),
            tests = tests,
            console = console
        )
        return json.encodeToString(output)
    }

    /**
     * Format an error as JSON.
     * Per Section 16.3.5: replaces entire output when error occurs.
     */
    fun formatError(
        message: String,
        position: SourcePosition?
    ): String {
        val output = JsonErrorOutput(
            message = message,
            location = ErrorLocation(
                line = position?.line ?: 1,
                column = position?.column ?: 1
            ),
            // Stack traces require bytecode enhancement - initial impl uses empty
            stack = emptyList()
        )
        return json.encodeToString(output)
    }

    /**
     * Format a SantaException as JSON error.
     */
    fun formatError(exception: SantaException): String {
        return formatError(exception.message, exception.position)
    }

    /**
     * Format a runtime value as a string.
     * Per spec: All values are serialized as strings.
     */
    fun formatValue(value: Value): String = when (value) {
        is IntValue -> value.value.toString()
        is DecimalValue -> value.value.toString()
        is StringValue -> value.value
        is BoolValue -> value.value.toString()
        is NilValue -> "nil"
        is ListValue -> "[" + (0 until value.size()).map { formatValue(value.get(it)) }.joinToString(", ") + "]"
        is SetValue -> "{" + value.elements.map { formatValue(it) }.joinToString(", ") + "}"
        is DictValue -> "#{" + value.entries.entries.map { (k, v) -> "${formatValue(k)}: ${formatValue(v)}" }.joinToString(", ") + "}"
        is FunctionValue -> "<function>"
        is LazySequenceValue -> "<lazy-sequence>"
        is RangeValue -> "<range>"
        is JavaClassValue -> "<class:${value.clazz.simpleName}>"
        is JavaObjectValue -> "<java:${value.obj?.javaClass?.simpleName ?: "null"}>"
    }
}

/**
 * Result of executing a single part.
 */
data class PartResult(
    val value: String,
    val durationMs: Long
)

/**
 * Test result for JSON formatting.
 */
data class TestJsonResult(
    val slow: Boolean,
    val skipped: Boolean,
    val partOnePassed: Boolean?,
    val partTwoPassed: Boolean?,
    val partOneExpected: String?,
    val partOneActual: String?,
    val partTwoExpected: String?,
    val partTwoActual: String?
)
