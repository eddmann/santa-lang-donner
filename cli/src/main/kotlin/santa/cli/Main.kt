package santa.cli

import santa.compiler.codegen.Compiler
import santa.compiler.error.ErrorFormatter
import santa.compiler.error.SantaException
import santa.compiler.lexer.SourcePosition
import santa.compiler.parser.Section
import santa.compiler.parser.TestBlockExpr
import santa.runtime.Builtins
import santa.runtime.SantaRuntimeException
import santa.runtime.value.*
import java.io.File
import kotlin.system.exitProcess

/**
 * Santa-lang CLI entry point.
 *
 * Usage:
 *   santa <file.santa>          - Run a Santa-lang source file
 *   santa -t <file.santa>       - Run tests only
 *   santa -h | --help           - Show help
 */
fun main(args: Array<String>) {
    if (args.isEmpty() || args.contains("-h") || args.contains("--help")) {
        printUsage()
        exitProcess(if (args.isEmpty()) 1 else 0)
    }

    val filePath = args.last()
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
        exitProcess(1)
    } catch (e: Exception) {
        // Unexpected errors
        System.err.println("Error: ${e.message}")
        exitProcess(1)
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

    var allPassed = true
    var passCount = 0
    var failCount = 0

    for (result in results) {
        val prefix = "Test ${result.testIndex}"

        if (result.error != null) {
            println("$prefix: ERROR - ${result.error}")
            allPassed = false
            failCount++
            continue
        }

        val partOneStatus = when (result.partOnePassed) {
            true -> { passCount++; "PASS" }
            false -> { failCount++; allPassed = false; "FAIL" }
            null -> null
        }

        val partTwoStatus = when (result.partTwoPassed) {
            true -> { passCount++; "PASS" }
            false -> { failCount++; allPassed = false; "FAIL" }
            null -> null
        }

        val parts = buildList {
            if (partOneStatus != null) {
                add("part_one: $partOneStatus")
                if (result.partOnePassed == false) {
                    add("  expected: ${formatValue(result.partOneExpected!!)}")
                    add("  actual:   ${formatValue(result.partOneActual!!)}")
                }
            }
            if (partTwoStatus != null) {
                add("part_two: $partTwoStatus")
                if (result.partTwoPassed == false) {
                    add("  expected: ${formatValue(result.partTwoExpected!!)}")
                    add("  actual:   ${formatValue(result.partTwoActual!!)}")
                }
            }
        }

        if (parts.isEmpty()) {
            println("$prefix: (no expectations)")
        } else {
            println("$prefix:")
            parts.forEach { println("  $it") }
        }
    }

    println()
    println("Results: $passCount passed, $failCount failed")

    exitProcess(if (allPassed) 0 else 1)
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

private fun printUsage() {
    println("""
        santa-lang CLI

        Usage:
          santa <file.santa>          Run a Santa-lang source file
          santa -t <file.santa>       Run tests only
          santa -s -t <file.santa>    Run tests including slow tests
          santa -h | --help           Show this help

        Exit codes:
          0  Success
          1  Error (file not found, parse error, runtime error)
    """.trimIndent())
}

private fun formatValue(value: Value): String = when (value) {
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
}
