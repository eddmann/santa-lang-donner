package santa.cli

import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance

/**
 * Integration tests for the CLI.
 * These tests spawn the actual CLI binary and verify end-to-end behavior.
 *
 * Based on Comet's CLI test patterns (runtime/cli/src/tests.rs).
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class CliIntegrationTest {

    private lateinit var cli: CliTestHelper

    @BeforeAll
    fun setUp() {
        cli = CliTestHelper()
    }

    @Nested
    inner class BasicExecution {

        @Test
        fun `script execution returns result`() {
            cli.runWithFixture("script.santa")
                .assertSuccess()
                .assertStdoutContains("14")
        }

        @Test
        fun `solution execution shows both parts`() {
            cli.runWithFixture("solution.santa")
                .assertSuccess()
                .assertStdoutContains("Part 1:")
                .assertStdoutContains("-1")
                .assertStdoutContains("Part 2:")
                .assertStdoutContains("5")
        }

        @Test
        fun `solution with test mode shows test results`() {
            cli.runWithFixture("solution.santa", "-t")
                .assertSuccess()
                .assertStdoutContains("Testcase #1")
        }
    }

    @Nested
    inner class ExitCodes {

        @Test
        fun `success returns exit code 0`() {
            cli.runWithFixture("script.santa")
                .assertExitCode(0)
        }

        @Test
        fun `missing file returns exit code 1`() {
            cli.run("nonexistent.santa")
                .assertExitCode(1)
        }

        @Test
        fun `syntax error returns exit code 2`() {
            cli.runWithFixture("syntax_error.santa")
                .assertExitCode(2)
        }

        @Test
        fun `runtime error returns exit code 2`() {
            cli.runWithFixture("runtime_error.santa")
                .assertExitCode(2)
        }

        @Test
        fun `test failure returns exit code 3`() {
            cli.runWithFixture("failing_test.santa", "-t")
                .assertExitCode(3)
        }
    }

    @Nested
    inner class JsonScriptOutput {

        @Test
        fun `json script simple`() {
            cli.runWithFixture("script.santa", "-o", "json")
                .assertSuccess()
                .assertStdoutContains(""""type":"script"""")
                .assertStdoutContains(""""status":"complete"""")
                .assertStdoutContains(""""value":"14"""")
        }

        @Test
        fun `json script with console`() {
            cli.runWithFixture("script_with_puts.santa", "-o", "json")
                .assertSuccess()
                .assertStdoutContains(""""type":"script"""")
                .assertStdoutContains(""""value":"6"""")
                .assertStdoutContains(""""message":"Hello World"""")
        }

        @Test
        fun `json error runtime`() {
            cli.runWithFixture("runtime_error.santa", "-o", "json")
                .assertExitCode(2)
                .assertStdoutContains(""""type":"error"""")
                .assertStdoutContains(""""message":""")
                .assertStdoutContains(""""location":""")
        }
    }

    @Nested
    inner class JsonSolutionOutput {

        @Test
        fun `json solution`() {
            cli.runWithFixture("solution.santa", "-o", "json")
                .assertSuccess()
                .assertStdoutContains(""""type":"solution"""")
                .assertStdoutContains(""""part_one":""")
                .assertStdoutContains(""""part_two":""")
                .assertStdoutContains(""""status":"complete"""")
        }

        @Test
        fun `json solution single part omits missing part`() {
            cli.runWithFixture("solution_single_part.santa", "-o", "json")
                .assertSuccess()
                .assertStdoutContains(""""type":"solution"""")
                .assertStdoutContains(""""part_one":""")
                .assertStdoutNotContains(""""part_two":""")
        }

        @Test
        fun `json error parse`() {
            cli.runWithFixture("syntax_error.santa", "-o", "json")
                .assertExitCode(2)
                .assertStdoutContains(""""type":"error"""")
                .assertStdoutContains(""""message":""")
        }
    }

    @Nested
    inner class JsonTestOutput {

        @Test
        fun `json test passing`() {
            cli.runWithFixture("solution.santa", "-o", "json", "-t")
                .assertSuccess()
                .assertStdoutContains(""""type":"test"""")
                .assertStdoutContains(""""success":true""")
                .assertStdoutContains(""""total":1""")
                .assertStdoutContains(""""failed":0""")
        }

        @Test
        fun `json test failing`() {
            cli.runWithFixture("failing_test.santa", "-o", "json", "-t")
                .assertExitCode(3)
                .assertStdoutContains(""""type":"test"""")
                .assertStdoutContains(""""success":false""")
                .assertStdoutContains(""""expected":"42"""")
                .assertStdoutContains(""""actual":"99"""")
        }

        @Test
        fun `json test skipped`() {
            cli.runWithFixture("solution_with_slow_test.santa", "-o", "json", "-t")
                .assertSuccess()
                .assertStdoutContains(""""type":"test"""")
                .assertStdoutContains(""""skipped":1""")
        }

        @Test
        fun `json test skipped included with slow flag`() {
            cli.runWithFixture("solution_with_slow_test.santa", "-o", "json", "-t", "-s")
                .assertSuccess()
                .assertStdoutContains(""""type":"test"""")
                .assertStdoutContains(""""skipped":0""")
        }
    }

    @Nested
    inner class JsonlOutput {

        @Test
        fun `jsonl script simple`() {
            cli.runWithFixture("script.santa", "-o", "jsonl")
                .assertSuccess()
                .assertStdoutContains(""""type":"script"""")
                .assertStdoutContains(""""status":"pending"""")
                .assertStdoutContains(""""op":"replace"""")
                .assertStdoutContains(""""/status"""")
                .assertStdoutContains(""""running"""")
                .assertStdoutContains(""""complete"""")
        }

        @Test
        fun `jsonl solution`() {
            cli.runWithFixture("solution.santa", "-o", "jsonl")
                .assertSuccess()
                .assertStdoutContains(""""type":"solution"""")
                .assertStdoutContains(""""/part_one/status"""")
                .assertStdoutContains(""""/part_one/value"""")
        }

        @Test
        fun `jsonl error`() {
            cli.runWithFixture("runtime_error.santa", "-o", "jsonl")
                .assertExitCode(2)
                .assertStdoutContains(""""type":"script"""")
                .assertStdoutContains(""""/error"""")
                .assertStdoutContains(""""message"""")
        }

        @Test
        fun `jsonl test`() {
            cli.runWithFixture("solution.santa", "-o", "jsonl", "-t")
                .assertSuccess()
                .assertStdoutContains(""""type":"test"""")
                .assertStdoutContains(""""/tests/0/status"""")
                .assertStdoutContains(""""/summary/passed"""")
        }
    }

    @Nested
    inner class CliArguments {

        @Test
        fun `help flag shows usage`() {
            cli.run("-h")
                .assertSuccess()
                .assertStdoutContains("USAGE:")
                .assertStdoutContains("santa-cli")
        }

        @Test
        fun `version flag shows version`() {
            cli.run("-v")
                .assertSuccess()
                .assertStdoutContains("Donner")
        }

        @Test
        fun `invalid output mode returns error`() {
            cli.runWithFixture("script.santa", "-o", "xml")
                .assertExitCode(1)
                .assertStderrContains("Invalid output format")
        }
    }
}
