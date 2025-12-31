package santa.cli

import santa.compiler.codegen.Compiler
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

    try {
        val source = file.readText()
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
    } catch (e: Exception) {
        System.err.println("Error: ${e.message}")
        exitProcess(1)
    }
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
