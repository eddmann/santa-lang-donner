package santa.cli

import santa.compiler.codegen.Compiler
import santa.compiler.error.ErrorFormatter
import santa.compiler.error.SantaException
import santa.compiler.parser.Section
import santa.compiler.parser.TestBlockExpr
import santa.runtime.Builtins
import santa.runtime.SantaRuntimeException
import santa.runtime.value.*
import java.io.File
import kotlin.system.exitProcess

// Version info
private const val VERSION = "0.1.0"

// ANSI escape codes for terminal formatting
private const val ANSI_RESET = "\u001b[0m"
private const val ANSI_UNDERLINE = "\u001b[4m"
private const val ANSI_GREEN = "\u001b[32m"
private const val ANSI_RED = "\u001b[31m"
private const val ANSI_YELLOW = "\u001b[33m"

/**
 * Santa-lang CLI entry point.
 */
fun main(args: Array<String>) {
    // Check -h/--help first
    if (args.contains("-h") || args.contains("--help")) {
        printHelp()
        exitProcess(0)
    }

    // Check -v/--version
    if (args.contains("-v") || args.contains("--version")) {
        println("santa-lang Donner $VERSION")
        exitProcess(0)
    }

    // Need a file argument
    val nonFlagArgs = args.filter { !it.startsWith("-") }
    if (nonFlagArgs.isEmpty()) {
        printHelp()
        exitProcess(1)
    }

    val filePath = nonFlagArgs.last()
    val runTests = args.contains("-t") || args.contains("--test")
    val includeSlow = args.contains("-s") || args.contains("--slow")

    val file = File(filePath)
    if (!file.exists()) {
        System.err.println("Error: File not found: $filePath")
        exitProcess(1)
    }

    val source = file.readText()

    // Set source file path for relative file lookups (e.g., AOC .input files)
    Builtins.sourceFilePath = file.absolutePath

    try {
        if (runTests) {
            runTestMode(source, includeSlow)
        } else {
            runNormalMode(source)
        }
    } catch (e: SantaException) {
        // Compile-time errors with source context
        System.err.println(e.formatWithSource(source))
        exitProcess(1)
    } catch (e: SantaRuntimeException) {
        // Runtime errors without source context (position not available)
        System.err.println(ErrorFormatter.format(e.message ?: "Unknown error", null, null, "RuntimeError"))
        exitProcess(2)
    } catch (e: Exception) {
        // Unexpected errors
        System.err.println("Error: ${e.message}")
        exitProcess(2)
    }
}

private fun runTestMode(source: String, includeSlow: Boolean) {
    val compileResult = Compiler.compileWithAst(source)
    val program = compileResult.program

    // Check if there are any test blocks
    val testSections = program.items.filterIsInstance<Section>()
        .filter { it.name == "test" && it.expr is TestBlockExpr }
        .filter { includeSlow || !it.isSlow }

    if (testSections.isEmpty()) {
        val totalTestBlocks = program.items.filterIsInstance<Section>()
            .count { it.name == "test" && it.expr is TestBlockExpr }
        if (totalTestBlocks > 0 && !includeSlow) {
            println("No tests to run (${totalTestBlocks} slow test(s) skipped, use -s to include)")
        } else {
            println("No test blocks found")
        }
        exitProcess(0)
    }

    val runner = TestRunner(program, includeSlow)
    val results = runner.runTests()

    var exitCode = 0

    for ((index, result) in results.withIndex()) {
        // Print blank line before each test case (except first) - matches Comet
        if (index > 0) {
            println()
        }

        // Print underlined header with optional (slow) tag - matches Comet
        if (result.isSlow) {
            println("${ANSI_UNDERLINE}Testcase #${result.testIndex}${ANSI_RESET} ${ANSI_YELLOW}(slow)${ANSI_RESET}")
        } else {
            println("${ANSI_UNDERLINE}Testcase #${result.testIndex}${ANSI_RESET}")
        }

        if (result.error != null) {
            println("${ANSI_RED}Error: ${result.error}${ANSI_RESET}")
            exitCode = 3
            continue
        }

        // Check if no expectations
        if (result.partOnePassed == null && result.partTwoPassed == null) {
            println("No expectations")
            continue
        }

        // Part 1
        if (result.partOnePassed != null) {
            val actual = formatValue(result.partOneActual!!)
            if (result.partOnePassed == true) {
                println("Part 1: $actual ${ANSI_GREEN}✔${ANSI_RESET}")
            } else {
                val expected = formatValue(result.partOneExpected!!)
                println("Part 1: $actual ${ANSI_RED}✘ (Expected: $expected)${ANSI_RESET}")
                exitCode = 3
            }
        }

        // Part 2
        if (result.partTwoPassed != null) {
            val actual = formatValue(result.partTwoActual!!)
            if (result.partTwoPassed == true) {
                println("Part 2: $actual ${ANSI_GREEN}✔${ANSI_RESET}")
            } else {
                val expected = formatValue(result.partTwoExpected!!)
                println("Part 2: $actual ${ANSI_RED}✘ (Expected: $expected)${ANSI_RESET}")
                exitCode = 3
            }
        }
    }

    exitProcess(exitCode)
}

private fun runNormalMode(source: String) {
    val compiled = Compiler.compile(source)
    val result = compiled.execute()

    // Print result if in script mode (no sections)
    if (!source.contains("part_one:") && !source.contains("part_two:")) {
        // Script mode: print the result
        println(formatValue(result))
    } else {
        // AOC mode: result is the last part evaluated
        println("Result: ${formatValue(result)}")
    }

    exitProcess(0)
}

private fun printHelp() {
    println("""santa-lang CLI - Donner $VERSION

USAGE:
    santa-cli <SCRIPT>              Run solution file
    santa-cli -t <SCRIPT>           Run test suite
    santa-cli -t -s <SCRIPT>        Run tests including @slow
    santa-cli -h                    Show this help

OPTIONS:
    -t, --test           Run the solution's test suite
    -s, --slow           Include @slow tests (use with -t)
    -h, --help           Show this help message
    -v, --version        Display version information""")
}

private fun formatValue(value: Value): String = when (value) {
    is IntValue -> value.value.toString()
    is DecimalValue -> value.value.toString()
    is StringValue -> "\"${value.value}\""
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
