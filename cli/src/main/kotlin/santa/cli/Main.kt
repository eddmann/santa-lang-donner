package santa.cli

import santa.cli.output.*
import santa.compiler.codegen.Compiler
import santa.compiler.error.ErrorFormatter
import santa.compiler.error.SantaException
import santa.compiler.parser.Program
import santa.compiler.parser.Section
import santa.compiler.parser.StatementItem
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
private const val ANSI_GRAY = "\u001b[90m"

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

    // Parse output mode
    val outputMode = parseOutputMode(args)

    // Need a file argument (filter out -o and its argument)
    val nonFlagArgs = args.filterIndexed { i, arg ->
        !arg.startsWith("-") &&
            (i == 0 || args[i - 1] != "-o" && args[i - 1] != "--output")
    }
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

    // Enable console capture for JSON/JSONL modes
    if (outputMode != OutputMode.Text) {
        ConsoleCapture.enable()
        Builtins.putsHook = { message -> ConsoleCapture.add(message) }
    }

    try {
        if (runTests) {
            runTestMode(source, includeSlow, outputMode)
        } else {
            runNormalMode(source, outputMode)
        }
    } catch (e: SantaException) {
        handleCompileError(e, source, outputMode)
    } catch (e: SantaRuntimeException) {
        handleRuntimeError(e, outputMode)
    } catch (e: Exception) {
        handleUnexpectedError(e, outputMode)
    } finally {
        // Clean up hooks
        Builtins.putsHook = null
    }
}

private fun parseOutputMode(args: Array<String>): OutputMode {
    val oIndex = args.indexOf("-o").takeIf { it >= 0 }
        ?: args.indexOf("--output").takeIf { it >= 0 }

    if (oIndex == null) return OutputMode.Text

    val modeArg = args.getOrNull(oIndex + 1)
    return when (modeArg) {
        null, "text" -> OutputMode.Text
        "json" -> OutputMode.Json
        "jsonl" -> OutputMode.Jsonl
        else -> {
            System.err.println("Error: Invalid output format '$modeArg'. Use: text, json, jsonl")
            exitProcess(1)
        }
    }
}

private fun handleCompileError(e: SantaException, source: String, outputMode: OutputMode) {
    when (outputMode) {
        OutputMode.Text -> {
            System.err.println(e.formatWithSource(source))
            exitProcess(2)
        }
        OutputMode.Json -> {
            ConsoleCapture.disable()
            println(JsonFormatter.formatError(e))
            exitProcess(2)
        }
        OutputMode.Jsonl -> {
            ConsoleCapture.disable()
            val writer = JsonlWriter()
            // Emit minimal initial state
            writer.writeInitial(JsonlScriptInitial())
            writer.writePatches(listOf(writer.replacePatch("/status", "running")))
            writer.writePatches(
                listOf(
                    writer.replacePatch("/status", "error"),
                    writer.addPatch(
                        "/error",
                        JsonlError(
                            message = e.message,
                            location = ErrorLocation(e.position?.line ?: 1, e.position?.column ?: 1),
                            stack = emptyList()
                        )
                    )
                )
            )
            exitProcess(2)
        }
    }
}

private fun handleRuntimeError(e: SantaRuntimeException, outputMode: OutputMode) {
    when (outputMode) {
        OutputMode.Text -> {
            System.err.println(ErrorFormatter.format(e.message ?: "Unknown error", null, null, "RuntimeError"))
            exitProcess(2)
        }
        OutputMode.Json -> {
            ConsoleCapture.disable()
            println(JsonFormatter.formatError(e.message ?: "Unknown error", null))
            exitProcess(2)
        }
        OutputMode.Jsonl -> {
            ConsoleCapture.disable()
            val writer = JsonlWriter()
            writer.writeInitial(JsonlScriptInitial())
            writer.writePatches(listOf(writer.replacePatch("/status", "running")))
            writer.writePatches(
                listOf(
                    writer.replacePatch("/status", "error"),
                    writer.addPatch(
                        "/error",
                        JsonlError(
                            message = e.message ?: "Unknown error",
                            location = ErrorLocation(1, 1),
                            stack = emptyList()
                        )
                    )
                )
            )
            exitProcess(2)
        }
    }
}

private fun handleUnexpectedError(e: Exception, outputMode: OutputMode) {
    when (outputMode) {
        OutputMode.Text -> {
            System.err.println("Error: ${e.message}")
            exitProcess(2)
        }
        OutputMode.Json -> {
            ConsoleCapture.disable()
            println(JsonFormatter.formatError(e.message ?: "Unknown error", null))
            exitProcess(2)
        }
        OutputMode.Jsonl -> {
            ConsoleCapture.disable()
            val writer = JsonlWriter()
            writer.writeInitial(JsonlScriptInitial())
            writer.writePatches(listOf(writer.replacePatch("/status", "running")))
            writer.writePatches(
                listOf(
                    writer.replacePatch("/status", "error"),
                    writer.addPatch(
                        "/error",
                        JsonlError(
                            message = e.message ?: "Unknown error",
                            location = ErrorLocation(1, 1),
                            stack = emptyList()
                        )
                    )
                )
            )
            exitProcess(2)
        }
    }
}

// =============================================================================
// Normal Mode (Solution / Script)
// =============================================================================

private fun runNormalMode(source: String, outputMode: OutputMode) {
    val hasPartOne = source.contains("part_one:")
    val hasPartTwo = source.contains("part_two:")
    val isSolution = hasPartOne || hasPartTwo

    when (outputMode) {
        OutputMode.Text -> runNormalModeText(source, isSolution)
        OutputMode.Json -> runNormalModeJson(source, isSolution, hasPartOne, hasPartTwo)
        OutputMode.Jsonl -> runNormalModeJsonl(source, isSolution, hasPartOne, hasPartTwo)
    }
}

private fun runNormalModeText(source: String, isSolution: Boolean) {
    if (isSolution) {
        // Run as solution with timing
        val compileResult = Compiler.compileWithAst(source)
        val program = compileResult.program

        val partOneSectionExpr = program.items.filterIsInstance<Section>().find { it.name == "part_one" }?.expr
        val partTwoSectionExpr = program.items.filterIsInstance<Section>().find { it.name == "part_two" }?.expr
        val topLevelStatements = program.items.filterIsInstance<StatementItem>()

        if (partOneSectionExpr != null) {
            val (result, durationMs) = executePart(program, partOneSectionExpr, topLevelStatements)
            println("Part 1: ${ANSI_GREEN}${formatValueText(result)}${ANSI_RESET} ${ANSI_GRAY}${durationMs}ms${ANSI_RESET}")
        }

        if (partTwoSectionExpr != null) {
            val (result, durationMs) = executePart(program, partTwoSectionExpr, topLevelStatements)
            println("Part 2: ${ANSI_GREEN}${formatValueText(result)}${ANSI_RESET} ${ANSI_GRAY}${durationMs}ms${ANSI_RESET}")
        }
    } else {
        // Script mode
        val compiled = Compiler.compile(source)
        val result = compiled.execute()
        println(formatValueText(result))
    }
    exitProcess(0)
}

private fun runNormalModeJson(source: String, isSolution: Boolean, hasPartOne: Boolean, hasPartTwo: Boolean) {
    val console = try {
        if (isSolution) {
            val compileResult = Compiler.compileWithAst(source)
            val program = compileResult.program

            val partOneSectionExpr = program.items.filterIsInstance<Section>().find { it.name == "part_one" }?.expr
            val partTwoSectionExpr = program.items.filterIsInstance<Section>().find { it.name == "part_two" }?.expr
            val topLevelStatements = program.items.filterIsInstance<StatementItem>()

            val partOneResult = if (partOneSectionExpr != null) {
                val (result, durationMs) = executePart(program, partOneSectionExpr, topLevelStatements)
                PartResult(JsonFormatter.formatValue(result), durationMs)
            } else null

            val partTwoResult = if (partTwoSectionExpr != null) {
                val (result, durationMs) = executePart(program, partTwoSectionExpr, topLevelStatements)
                PartResult(JsonFormatter.formatValue(result), durationMs)
            } else null

            val capturedConsole = ConsoleCapture.disable()
            println(JsonFormatter.formatSolution(partOneResult, partTwoResult, capturedConsole))
        } else {
            val startMs = System.currentTimeMillis()
            val compiled = Compiler.compile(source)
            val result = compiled.execute()
            val durationMs = System.currentTimeMillis() - startMs

            val capturedConsole = ConsoleCapture.disable()
            println(JsonFormatter.formatScript(JsonFormatter.formatValue(result), durationMs, capturedConsole))
        }
        exitProcess(0)
    } catch (e: Exception) {
        ConsoleCapture.disable()
        throw e
    }
}

private fun runNormalModeJsonl(source: String, isSolution: Boolean, hasPartOne: Boolean, hasPartTwo: Boolean) {
    val writer = JsonlWriter()

    try {
        if (isSolution) {
            // Emit initial state
            val initial = JsonlSolutionInitial(
                partOne = if (hasPartOne) JsonlPartInitial() else null,
                partTwo = if (hasPartTwo) JsonlPartInitial() else null
            )
            writer.writeInitial(initial)
            writer.writePatches(listOf(writer.replacePatch("/status", "running")))

            val compileResult = Compiler.compileWithAst(source)
            val program = compileResult.program

            val partOneSectionExpr = program.items.filterIsInstance<Section>().find { it.name == "part_one" }?.expr
            val partTwoSectionExpr = program.items.filterIsInstance<Section>().find { it.name == "part_two" }?.expr
            val topLevelStatements = program.items.filterIsInstance<StatementItem>()

            if (partOneSectionExpr != null) {
                writer.writePatches(listOf(writer.replacePatch("/part_one/status", "running")))
                val (result, durationMs) = executePart(program, partOneSectionExpr, topLevelStatements)
                writer.writePatches(
                    listOf(
                        writer.replacePatch("/part_one/status", "complete"),
                        writer.replacePatch("/part_one/value", JsonFormatter.formatValue(result)),
                        writer.replacePatch("/part_one/duration_ms", durationMs)
                    )
                )
            }

            if (partTwoSectionExpr != null) {
                writer.writePatches(listOf(writer.replacePatch("/part_two/status", "running")))
                val (result, durationMs) = executePart(program, partTwoSectionExpr, topLevelStatements)
                writer.writePatches(
                    listOf(
                        writer.replacePatch("/part_two/status", "complete"),
                        writer.replacePatch("/part_two/value", JsonFormatter.formatValue(result)),
                        writer.replacePatch("/part_two/duration_ms", durationMs)
                    )
                )
            }

            // Emit console entries
            val capturedConsole = ConsoleCapture.disable()
            for (entry in capturedConsole) {
                writer.writePatches(listOf(writer.addPatch("/console/-", entry)))
            }

            writer.writePatches(listOf(writer.replacePatch("/status", "complete")))
        } else {
            // Script mode
            writer.writeInitial(JsonlScriptInitial())
            writer.writePatches(listOf(writer.replacePatch("/status", "running")))

            val startMs = System.currentTimeMillis()
            val compiled = Compiler.compile(source)
            val result = compiled.execute()
            val durationMs = System.currentTimeMillis() - startMs

            // Emit console entries
            val capturedConsole = ConsoleCapture.disable()
            for (entry in capturedConsole) {
                writer.writePatches(listOf(writer.addPatch("/console/-", entry)))
            }

            writer.writePatches(
                listOf(
                    writer.replacePatch("/status", "complete"),
                    writer.replacePatch("/value", JsonFormatter.formatValue(result)),
                    writer.replacePatch("/duration_ms", durationMs)
                )
            )
        }
        exitProcess(0)
    } catch (e: SantaException) {
        val console = ConsoleCapture.disable()
        writer.writePatches(
            listOf(
                writer.replacePatch("/status", "error"),
                writer.addPatch(
                    "/error",
                    JsonlError(
                        message = e.message,
                        location = ErrorLocation(e.position?.line ?: 1, e.position?.column ?: 1),
                        stack = emptyList()
                    )
                )
            )
        )
        exitProcess(2)
    } catch (e: SantaRuntimeException) {
        val console = ConsoleCapture.disable()
        writer.writePatches(
            listOf(
                writer.replacePatch("/status", "error"),
                writer.addPatch(
                    "/error",
                    JsonlError(
                        message = e.message ?: "Unknown error",
                        location = ErrorLocation(1, 1),
                        stack = emptyList()
                    )
                )
            )
        )
        exitProcess(2)
    }
}

private fun executePart(
    program: Program,
    partExpr: santa.compiler.parser.Expr,
    topLevelStatements: List<StatementItem>
): Pair<Value, Long> {
    val inputSection = program.items.filterIsInstance<Section>().find { it.name == "input" }

    val modifiedItems = buildList {
        addAll(topLevelStatements)
        if (inputSection != null) {
            add(Section("input", inputSection.expr, false, inputSection.span))
        }
        add(Section("result", partExpr, false, partExpr.span))
    }

    val modifiedProgram = Program(modifiedItems)
    val compiled = Compiler.compileProgram(modifiedProgram)

    val startMs = System.currentTimeMillis()
    val result = compiled.execute()
    val durationMs = System.currentTimeMillis() - startMs

    return Pair(result, durationMs)
}

// =============================================================================
// Test Mode
// =============================================================================

private fun runTestMode(source: String, includeSlow: Boolean, outputMode: OutputMode) {
    val compileResult = Compiler.compileWithAst(source)
    val program = compileResult.program

    // Determine which parts the solution defines
    val hasPartOne = program.items.filterIsInstance<Section>().any { it.name == "part_one" }
    val hasPartTwo = program.items.filterIsInstance<Section>().any { it.name == "part_two" }

    // Get all test sections
    val allTestSections = program.items.filterIsInstance<Section>()
        .filter { it.name == "test" && it.expr is TestBlockExpr }

    when (outputMode) {
        OutputMode.Text -> runTestModeText(source, program, includeSlow, allTestSections)
        OutputMode.Json -> runTestModeJson(source, program, includeSlow, allTestSections, hasPartOne, hasPartTwo)
        OutputMode.Jsonl -> runTestModeJsonl(source, program, includeSlow, allTestSections, hasPartOne, hasPartTwo)
    }
}

private fun runTestModeText(
    source: String,
    program: Program,
    includeSlow: Boolean,
    allTestSections: List<Section>
) {
    val testSections = allTestSections.filter { includeSlow || !it.isSlow }

    if (testSections.isEmpty()) {
        val totalTestBlocks = allTestSections.size
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
        if (index > 0) println()

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

        if (result.partOnePassed == null && result.partTwoPassed == null) {
            println("No expectations")
            continue
        }

        if (result.partOnePassed != null) {
            val actual = formatValueText(result.partOneActual!!)
            if (result.partOnePassed == true) {
                println("Part 1: $actual ${ANSI_GREEN}✔${ANSI_RESET}")
            } else {
                val expected = formatValueText(result.partOneExpected!!)
                println("Part 1: $actual ${ANSI_RED}✘ (Expected: $expected)${ANSI_RESET}")
                exitCode = 3
            }
        }

        if (result.partTwoPassed != null) {
            val actual = formatValueText(result.partTwoActual!!)
            if (result.partTwoPassed == true) {
                println("Part 2: $actual ${ANSI_GREEN}✔${ANSI_RESET}")
            } else {
                val expected = formatValueText(result.partTwoExpected!!)
                println("Part 2: $actual ${ANSI_RED}✘ (Expected: $expected)${ANSI_RESET}")
                exitCode = 3
            }
        }
    }

    exitProcess(exitCode)
}

private fun runTestModeJson(
    source: String,
    program: Program,
    includeSlow: Boolean,
    allTestSections: List<Section>,
    hasPartOne: Boolean,
    hasPartTwo: Boolean
) {
    val runner = TestRunner(program, includeSlow)
    val results = runner.runTests()

    // Build test results including skipped tests
    val jsonResults = mutableListOf<TestJsonResult>()

    for ((index, section) in allTestSections.withIndex()) {
        val result = results.find { it.testIndex == index + 1 }

        if (result == null) {
            // This test was skipped
            jsonResults.add(
                TestJsonResult(
                    slow = section.isSlow,
                    skipped = true,
                    partOnePassed = null,
                    partTwoPassed = null,
                    partOneExpected = null,
                    partOneActual = null,
                    partTwoExpected = null,
                    partTwoActual = null
                )
            )
        } else {
            jsonResults.add(
                TestJsonResult(
                    slow = result.isSlow,
                    skipped = false,
                    partOnePassed = result.partOnePassed,
                    partTwoPassed = result.partTwoPassed,
                    partOneExpected = result.partOneExpected?.let { JsonFormatter.formatValue(it) },
                    partOneActual = result.partOneActual?.let { JsonFormatter.formatValue(it) },
                    partTwoExpected = result.partTwoExpected?.let { JsonFormatter.formatValue(it) },
                    partTwoActual = result.partTwoActual?.let { JsonFormatter.formatValue(it) }
                )
            )
        }
    }

    val console = ConsoleCapture.disable()
    println(JsonFormatter.formatTest(jsonResults, hasPartOne, hasPartTwo, console))

    val hasFailures = jsonResults.any { !it.skipped && (it.partOnePassed == false || it.partTwoPassed == false) }
    exitProcess(if (hasFailures) 3 else 0)
}

private fun runTestModeJsonl(
    source: String,
    program: Program,
    includeSlow: Boolean,
    allTestSections: List<Section>,
    hasPartOne: Boolean,
    hasPartTwo: Boolean
) {
    val writer = JsonlWriter()

    // Build initial test state
    val initialTests = allTestSections.mapIndexed { i, section ->
        JsonlTestCaseInitial(
            index = i + 1,
            slow = section.isSlow,
            status = "pending"
        )
    }

    val initial = JsonlTestInitial(
        summary = TestSummary(
            total = allTestSections.size,
            passed = 0,
            failed = 0,
            skipped = 0
        ),
        tests = initialTests
    )
    writer.writeInitial(initial)
    writer.writePatches(listOf(writer.replacePatch("/status", "running")))

    val runner = TestRunner(program, includeSlow)
    val results = runner.runTests()

    var passed = 0
    var failed = 0
    var skipped = 0

    for ((index, section) in allTestSections.withIndex()) {
        val pathPrefix = "/tests/$index"
        val result = results.find { it.testIndex == index + 1 }

        if (result == null) {
            // Skipped test
            skipped++
            writer.writePatches(
                listOf(
                    writer.replacePatch("$pathPrefix/status", "skipped"),
                    writer.replacePatch("/summary/skipped", skipped)
                )
            )
        } else {
            // Running
            writer.writePatches(listOf(writer.replacePatch("$pathPrefix/status", "running")))

            val patches = mutableListOf(writer.replacePatch("$pathPrefix/status", "complete"))

            if (hasPartOne && result.partOnePassed != null) {
                patches.add(
                    writer.replacePatch(
                        "$pathPrefix/part_one",
                        JsonTestPartResult(
                            passed = result.partOnePassed,
                            expected = JsonFormatter.formatValue(result.partOneExpected!!),
                            actual = JsonFormatter.formatValue(result.partOneActual!!)
                        )
                    )
                )
            }

            if (hasPartTwo && result.partTwoPassed != null) {
                patches.add(
                    writer.replacePatch(
                        "$pathPrefix/part_two",
                        JsonTestPartResult(
                            passed = result.partTwoPassed,
                            expected = JsonFormatter.formatValue(result.partTwoExpected!!),
                            actual = JsonFormatter.formatValue(result.partTwoActual!!)
                        )
                    )
                )
            }

            val allPassed = (result.partOnePassed ?: true) && (result.partTwoPassed ?: true)
            if (allPassed) {
                passed++
                patches.add(writer.replacePatch("/summary/passed", passed))
            } else {
                failed++
                patches.add(writer.replacePatch("/summary/failed", failed))
            }

            writer.writePatches(patches)
        }
    }

    // Emit console entries
    val console = ConsoleCapture.disable()
    for (entry in console) {
        writer.writePatches(listOf(writer.addPatch("/console/-", entry)))
    }

    // Emit completion
    val success = failed == 0
    writer.writePatches(
        listOf(
            writer.replacePatch("/status", "complete"),
            writer.replacePatch("/success", success)
        )
    )

    exitProcess(if (!success) 3 else 0)
}

// =============================================================================
// Helpers
// =============================================================================

private fun printHelp() {
    println(
        """santa-lang CLI - Donner $VERSION

USAGE:
    santa-cli <SCRIPT>              Run solution file
    santa-cli -o json <SCRIPT>      Output as JSON
    santa-cli -o jsonl <SCRIPT>     Output as JSON Lines (streaming)
    santa-cli -t <SCRIPT>           Run test suite
    santa-cli -t -s <SCRIPT>        Run tests including @slow
    santa-cli -h                    Show this help

OPTIONS:
    -o, --output FORMAT  Output format: text (default), json, jsonl
    -t, --test           Run the solution's test suite
    -s, --slow           Include @slow tests (use with -t)
    -h, --help           Show this help message
    -v, --version        Display version information"""
    )
}

private fun formatValueText(value: Value): String = when (value) {
    is IntValue -> value.value.toString()
    is DecimalValue -> value.value.toString()
    is StringValue -> "\"${value.value}\""
    is BoolValue -> value.value.toString()
    is NilValue -> "nil"
    is ListValue -> "[" + (0 until value.size()).map { formatValueText(value.get(it)) }.joinToString(", ") + "]"
    is SetValue -> "{" + value.elements.map { formatValueText(it) }.joinToString(", ") + "}"
    is DictValue -> "#{" + value.entries.entries.map { (k, v) -> "${formatValueText(k)}: ${formatValueText(v)}" }.joinToString(", ") + "}"
    is FunctionValue -> "<function>"
    is LazySequenceValue -> "<lazy-sequence>"
    is RangeValue -> "<range>"
    is JavaClassValue -> "<class:${value.clazz.simpleName}>"
    is JavaObjectValue -> "<java:${value.obj?.javaClass?.simpleName ?: "null"}>"
}
