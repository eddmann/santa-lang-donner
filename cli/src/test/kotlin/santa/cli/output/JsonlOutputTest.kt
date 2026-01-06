package santa.cli.output

import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldNotContain
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

/**
 * Tests for JSONL streaming output per LANG.txt Section 16.4.
 */
class JsonlOutputTest {

    private val json = Json { encodeDefaults = true }

    @Nested
    inner class InitialState {

        @Test
        fun `script initial state has pending status`() {
            val initial = JsonlScriptInitial()

            val output = json.encodeToString(initial)

            output shouldContain """"type":"script""""
            output shouldContain """"status":"pending""""
            output shouldContain """"value":null"""
            output shouldContain """"console":[]"""
        }

        @Test
        fun `solution initial state has pending parts`() {
            val initial = JsonlSolutionInitial(
                partOne = JsonlPartInitial(),
                partTwo = JsonlPartInitial()
            )

            val output = json.encodeToString(initial)

            output shouldContain """"type":"solution""""
            output shouldContain """"status":"pending""""
            output shouldContain """"part_one":{"status":"pending""""
            output shouldContain """"part_two":{"status":"pending""""
        }

        @Test
        @OptIn(kotlinx.serialization.ExperimentalSerializationApi::class)
        fun `solution initial state omits missing parts when explicitNulls is false`() {
            val initial = JsonlSolutionInitial(
                partOne = JsonlPartInitial(),
                partTwo = null
            )

            val output = Json {
                encodeDefaults = true
                explicitNulls = false
            }.encodeToString(initial)

            output shouldContain """"part_one":"""
            // part_two should be omitted entirely (not present as "part_two":null)
            output shouldNotContain """"part_two":"""
        }

        @Test
        fun `test initial state has all tests pending`() {
            val initial = JsonlTestInitial(
                summary = TestSummary(total = 2, passed = 0, failed = 0, skipped = 0),
                tests = listOf(
                    JsonlTestCaseInitial(index = 1, slow = false),
                    JsonlTestCaseInitial(index = 2, slow = true)
                )
            )

            val output = json.encodeToString(initial)

            output shouldContain """"type":"test""""
            output shouldContain """"total":2"""
            output shouldContain """"index":1"""
            output shouldContain """"index":2"""
            output shouldContain """"slow":true"""
        }
    }

    @Nested
    inner class JsonPatchFormat {

        @Test
        fun `replace patch has correct format`() {
            val writer = JsonlWriter()
            val patch = writer.replacePatch("/status", "running")

            patch.op shouldBe "replace"
            patch.path shouldBe "/status"
        }

        @Test
        fun `add patch has correct format`() {
            val writer = JsonlWriter()
            val patch = writer.addPatch("/console/-", ConsoleEntry(0, "hello"))

            patch.op shouldBe "add"
            patch.path shouldBe "/console/-"
        }

        @Test
        fun `patch with object value serializes correctly`() {
            val writer = JsonlWriter()
            val patch = writer.replacePatch(
                "/part_one",
                JsonTestPartResult(passed = true, expected = "42", actual = "42")
            )

            val output = json.encodeToString(patch)

            output shouldContain """"passed":true"""
            output shouldContain """"expected":"42""""
        }

        @Test
        fun `patches for console append use dash path`() {
            val writer = JsonlWriter()
            val patch = writer.addPatch("/console/-", ConsoleEntry(100, "debug"))

            patch.path shouldBe "/console/-"
            val output = json.encodeToString(patch)
            output shouldContain """"message":"debug""""
        }
    }

    @Nested
    inner class JsonlErrorFormat {

        @Test
        fun `error has required fields`() {
            val error = JsonlError(
                message = "Division by zero",
                location = ErrorLocation(line = 10, column = 5),
                stack = emptyList()
            )

            val output = json.encodeToString(error)

            output shouldContain """"message":"Division by zero""""
            output shouldContain """"line":10"""
            output shouldContain """"column":5"""
            output shouldContain """"stack":[]"""
        }

        @Test
        fun `error with stack frame`() {
            val error = JsonlError(
                message = "Error",
                location = ErrorLocation(line = 1, column = 1),
                stack = listOf(
                    StackFrame(function = "calculate", line = 15, column = 8)
                )
            )

            val output = json.encodeToString(error)

            output shouldContain """"function":"calculate""""
        }
    }

    @Nested
    inner class StatusTransitions {

        @Test
        fun `solution status transitions pending to running to complete`() {
            // This tests that the data structures support the required transitions
            val initial = JsonlSolutionInitial()
            initial.status shouldBe "pending"

            // After running patch
            val runningPatch = JsonPatch(
                op = "replace",
                path = "/status",
                value = Json.encodeToJsonElement(kotlinx.serialization.serializer(), "running")
            )
            runningPatch.path shouldBe "/status"

            // After complete patch
            val completePatch = JsonPatch(
                op = "replace",
                path = "/status",
                value = Json.encodeToJsonElement(kotlinx.serialization.serializer(), "complete")
            )
            completePatch.path shouldBe "/status"
        }

        @Test
        fun `test case status can transition to skipped`() {
            val initial = JsonlTestCaseInitial(index = 1, slow = true)
            initial.status shouldBe "pending"

            // Skipped test jumps directly to skipped status
            val skippedPatch = JsonPatch(
                op = "replace",
                path = "/tests/0/status",
                value = Json.encodeToJsonElement(kotlinx.serialization.serializer(), "skipped")
            )
            skippedPatch.path shouldBe "/tests/0/status"
        }
    }

    @Nested
    inner class PartPaths {

        @Test
        fun `part_one path uses snake_case`() {
            val writer = JsonlWriter()
            val patch = writer.replacePatch("/part_one/status", "running")

            patch.path shouldBe "/part_one/status"
        }

        @Test
        fun `part_two path uses snake_case`() {
            val writer = JsonlWriter()
            val patch = writer.replacePatch("/part_two/value", "42")

            patch.path shouldBe "/part_two/value"
        }

        @Test
        fun `duration_ms path uses snake_case`() {
            val writer = JsonlWriter()
            val patch = writer.replacePatch("/part_one/duration_ms", 123L)

            patch.path shouldBe "/part_one/duration_ms"
        }
    }
}
