package santa.cli.output

import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldNotContain
import kotlinx.collections.immutable.persistentListOf
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import santa.compiler.codegen.Compiler
import santa.runtime.Builtins
import santa.runtime.value.IntValue
import santa.runtime.value.ListValue

/**
 * Tests for JSON and JSONL output formatting per LANG.txt Section 16.
 */
class JsonOutputTest {

    @BeforeEach
    fun setUp() {
        ConsoleCapture.enable()
        Builtins.putsHook = { message -> ConsoleCapture.add(message) }
    }

    @AfterEach
    fun tearDown() {
        Builtins.putsHook = null
        ConsoleCapture.disable()
    }

    @Nested
    inner class ScriptJsonOutput {

        @Test
        fun `json script simple expression`() {
            val compiled = Compiler.compile("1 + 2")
            val result = compiled.execute()
            val console = ConsoleCapture.disable()

            val json = JsonFormatter.formatScript(
                JsonFormatter.formatValue(result),
                10,
                console
            )

            json shouldContain """"type":"script""""
            json shouldContain """"status":"complete""""
            json shouldContain """"value":"3""""
            json shouldContain """"console":[]"""
        }

        @Test
        fun `json script with console output`() {
            val compiled = Compiler.compile("""puts("hello"); 42""")
            val result = compiled.execute()
            val console = ConsoleCapture.disable()

            val json = JsonFormatter.formatScript(
                JsonFormatter.formatValue(result),
                10,
                console
            )

            json shouldContain """"type":"script""""
            json shouldContain """"value":"42""""
            json shouldContain """"message":"hello""""
            json shouldContain """"timestamp_ms":"""
        }

        @Test
        fun `json script puts with multiple values`() {
            val compiled = Compiler.compile("""puts(1, nil, [1, 2]); 0""")
            compiled.execute()
            val console = ConsoleCapture.disable()

            console.size shouldBe 1
            console[0].message shouldBe "1 nil [1, 2]"
        }

        @Test
        fun `json script puts with no arguments emits nothing`() {
            val compiled = Compiler.compile("""puts(); 42""")
            compiled.execute()
            val console = ConsoleCapture.disable()

            console.size shouldBe 0
        }
    }

    @Nested
    inner class SolutionJsonOutput {

        @Test
        fun `json solution with both parts`() {
            val partOne = PartResult("232", 5)
            val partTwo = PartResult("1783", 12)

            val json = JsonFormatter.formatSolution(partOne, partTwo, emptyList())

            json shouldContain """"type":"solution""""
            json shouldContain """"status":"complete""""
            json shouldContain """"part_one":"""
            json shouldContain """"part_two":"""
            json shouldContain """"value":"232""""
            json shouldContain """"value":"1783""""
        }

        @Test
        fun `json solution single part omits missing part`() {
            val partOne = PartResult("42", 5)

            val json = JsonFormatter.formatSolution(partOne, null, emptyList())

            json shouldContain """"part_one":"""
            json shouldNotContain """"part_two":"""
        }

        @Test
        fun `json solution only part_two omits part_one`() {
            val partTwo = PartResult("99", 10)

            val json = JsonFormatter.formatSolution(null, partTwo, emptyList())

            json shouldNotContain """"part_one":"""
            json shouldContain """"part_two":"""
        }
    }

    @Nested
    inner class TestJsonOutput {

        @Test
        fun `json test passing`() {
            val testResults = listOf(
                TestJsonResult(
                    slow = false,
                    skipped = false,
                    partOnePassed = true,
                    partTwoPassed = true,
                    partOneExpected = "6",
                    partOneActual = "6",
                    partTwoExpected = "12",
                    partTwoActual = "12"
                )
            )

            val json = JsonFormatter.formatTest(testResults, hasPartOne = true, hasPartTwo = true, emptyList())

            json shouldContain """"type":"test""""
            json shouldContain """"success":true"""
            json shouldContain """"passed":1"""
            json shouldContain """"failed":0"""
        }

        @Test
        fun `json test failing`() {
            val testResults = listOf(
                TestJsonResult(
                    slow = false,
                    skipped = false,
                    partOnePassed = true,
                    partTwoPassed = false,
                    partOneExpected = "6",
                    partOneActual = "6",
                    partTwoExpected = "99",
                    partTwoActual = "2"
                )
            )

            val json = JsonFormatter.formatTest(testResults, hasPartOne = true, hasPartTwo = true, emptyList())

            json shouldContain """"success":false"""
            json shouldContain """"passed":0"""
            json shouldContain """"failed":1"""
            json shouldContain """"expected":"99""""
            json shouldContain """"actual":"2""""
        }

        @Test
        fun `json test skipped`() {
            val testResults = listOf(
                TestJsonResult(
                    slow = true,
                    skipped = true,
                    partOnePassed = null,
                    partTwoPassed = null,
                    partOneExpected = null,
                    partOneActual = null,
                    partTwoExpected = null,
                    partTwoActual = null
                )
            )

            val json = JsonFormatter.formatTest(testResults, hasPartOne = true, hasPartTwo = true, emptyList())

            json shouldContain """"skipped":1"""
            json shouldContain """"status":"skipped""""
            json shouldContain """"slow":true"""
        }

        @Test
        fun `json test summary counts`() {
            val testResults = listOf(
                TestJsonResult(false, false, true, true, "1", "1", "2", "2"),  // passed
                TestJsonResult(false, false, true, false, "1", "1", "5", "3"), // failed
                TestJsonResult(true, true, null, null, null, null, null, null) // skipped
            )

            val json = JsonFormatter.formatTest(testResults, hasPartOne = true, hasPartTwo = true, emptyList())

            json shouldContain """"total":3"""
            json shouldContain """"passed":1"""
            json shouldContain """"failed":1"""
            json shouldContain """"skipped":1"""
        }

        @Test
        fun `json test index is 1-indexed`() {
            val testResults = listOf(
                TestJsonResult(false, false, true, null, "1", "1", null, null)
            )

            val json = JsonFormatter.formatTest(testResults, hasPartOne = true, hasPartTwo = false, emptyList())

            json shouldContain """"index":1"""
        }
    }

    @Nested
    inner class ErrorJsonOutput {

        @Test
        fun `json error with position`() {
            val position = santa.compiler.lexer.SourcePosition(15, 8)

            val json = JsonFormatter.formatError("Division by zero", position)

            json shouldContain """"type":"error""""
            json shouldContain """"message":"Division by zero""""
            json shouldContain """"line":15"""
            json shouldContain """"column":8"""
        }

        @Test
        fun `json error without position defaults to line 1 column 1`() {
            val json = JsonFormatter.formatError("Unknown error", null)

            json shouldContain """"line":1"""
            json shouldContain """"column":1"""
        }

        @Test
        fun `json error has empty stack initially`() {
            val json = JsonFormatter.formatError("Error", null)

            json shouldContain """"stack":[]"""
        }
    }

    @Nested
    inner class ValueFormatting {

        @Test
        fun `format integer`() {
            JsonFormatter.formatValue(IntValue(42)) shouldBe "42"
        }

        @Test
        fun `format nil as string`() {
            JsonFormatter.formatValue(santa.runtime.value.NilValue) shouldBe "nil"
        }

        @Test
        fun `format list as string`() {
            val list = ListValue(
                persistentListOf(
                    IntValue(1),
                    IntValue(2),
                    IntValue(3)
                )
            )
            JsonFormatter.formatValue(list) shouldBe "[1, 2, 3]"
        }

        @Test
        fun `format string without extra quotes`() {
            // In JSON output, string VALUES should NOT have extra quotes
            // The "value" field in JSON already quotes the value
            JsonFormatter.formatValue(santa.runtime.value.StringValue("hello")) shouldBe "hello"
        }

        @Test
        fun `format boolean`() {
            JsonFormatter.formatValue(santa.runtime.value.BoolValue(true)) shouldBe "true"
            JsonFormatter.formatValue(santa.runtime.value.BoolValue(false)) shouldBe "false"
        }
    }

    @Nested
    inner class ConsoleCaptureTest {

        @Test
        fun `console entries have timestamps`() {
            val compiled = Compiler.compile("""
                puts("first");
                puts("second");
                1
            """.trimIndent())
            compiled.execute()
            val console = ConsoleCapture.disable()

            console.size shouldBe 2
            console[0].message shouldBe "first"
            console[1].message shouldBe "second"
            // Second timestamp should be >= first
            (console[1].timestamp_ms >= console[0].timestamp_ms) shouldBe true
        }

        @Test
        fun `console disabled returns empty list`() {
            ConsoleCapture.disable()
            val console = ConsoleCapture.disable()

            console.size shouldBe 0
        }
    }
}
