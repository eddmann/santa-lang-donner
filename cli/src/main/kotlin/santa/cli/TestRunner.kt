package santa.cli

import santa.compiler.codegen.Compiler
import santa.compiler.parser.*
import santa.runtime.value.*

/**
 * Result of running a single test case.
 */
data class TestCaseResult(
    val testIndex: Int,
    val isSlow: Boolean,
    val partOnePassed: Boolean?,
    val partTwoPassed: Boolean?,
    val partOneExpected: Value?,
    val partOneActual: Value?,
    val partTwoExpected: Value?,
    val partTwoActual: Value?,
    val error: String? = null,
)

/**
 * Test runner for AOC-style test blocks.
 *
 * Executes test blocks from a Santa-lang program and validates results
 * against expected values.
 */
class TestRunner(
    private val program: Program,
    private val includeSlow: Boolean = false,
) {
    /**
     * Run all test blocks and return results.
     */
    fun runTests(): List<TestCaseResult> {
        val results = mutableListOf<TestCaseResult>()

        // Find test sections
        val testSections = program.items.filterIsInstance<Section>()
            .filter { it.name == "test" }
            .filter { includeSlow || !it.isSlow }

        // Find part_one and part_two sections
        val partOneSectionExpr = program.items.filterIsInstance<Section>()
            .find { it.name == "part_one" }?.expr
        val partTwoSectionExpr = program.items.filterIsInstance<Section>()
            .find { it.name == "part_two" }?.expr

        // Find top-level statements (not sections) for context
        val topLevelStatements = program.items.filterIsInstance<StatementItem>()

        for ((index, testSection) in testSections.withIndex()) {
            val testBlock = testSection.expr
            if (testBlock !is TestBlockExpr) {
                results.add(TestCaseResult(
                    testIndex = index + 1,
                    isSlow = testSection.isSlow,
                    partOnePassed = null,
                    partTwoPassed = null,
                    partOneExpected = null,
                    partOneActual = null,
                    partTwoExpected = null,
                    partTwoActual = null,
                    error = "Invalid test block structure",
                ))
                continue
            }

            val result = runSingleTest(
                testIndex = index + 1,
                isSlow = testSection.isSlow,
                testBlock = testBlock,
                partOneExpr = partOneSectionExpr,
                partTwoExpr = partTwoSectionExpr,
                topLevelStatements = topLevelStatements,
            )
            results.add(result)
        }

        return results
    }

    private fun runSingleTest(
        testIndex: Int,
        isSlow: Boolean,
        testBlock: TestBlockExpr,
        partOneExpr: Expr?,
        partTwoExpr: Expr?,
        topLevelStatements: List<StatementItem>,
    ): TestCaseResult {
        try {
            // Extract test entries
            val testInputEntry = testBlock.entries.find { it.name == "input" }
            val expectedPartOne = testBlock.entries.find { it.name == "part_one" }
            val expectedPartTwo = testBlock.entries.find { it.name == "part_two" }

            if (testInputEntry == null) {
                return TestCaseResult(
                    testIndex = testIndex,
                    isSlow = isSlow,
                    partOnePassed = null,
                    partTwoPassed = null,
                    partOneExpected = null,
                    partOneActual = null,
                    partTwoExpected = null,
                    partTwoActual = null,
                    error = "Test block missing 'input' entry",
                )
            }

            // Build a modified program that uses the test's input
            val modifiedItems = buildList {
                // Add top-level statements first
                addAll(topLevelStatements)

                // Add input section with test's input value
                add(Section("input", testInputEntry.expr, false, testInputEntry.span))

                // Add part_one if exists and expected
                if (partOneExpr != null && expectedPartOne != null) {
                    add(Section("part_one", partOneExpr, false, partOneExpr.span))
                }

                // Add part_two if exists and expected
                if (partTwoExpr != null && expectedPartTwo != null) {
                    add(Section("part_two", partTwoExpr, false, partTwoExpr.span))
                }
            }

            val modifiedProgram = Program(modifiedItems)
            val compiled = Compiler.compileProgram(modifiedProgram)
            val result = compiled.execute()

            // Get expected values by compiling just the expected expressions
            val partOneExpectedValue = expectedPartOne?.let { evaluateExpr(it.expr, topLevelStatements) }
            val partTwoExpectedValue = expectedPartTwo?.let { evaluateExpr(it.expr, topLevelStatements) }

            // Get actual values by running modified programs
            val partOneActualValue = if (partOneExpr != null && expectedPartOne != null) {
                val partOneProgram = Program(buildList {
                    addAll(topLevelStatements)
                    add(Section("input", testInputEntry.expr, false, testInputEntry.span))
                    add(Section("part_one", partOneExpr, false, partOneExpr.span))
                })
                Compiler.compileProgram(partOneProgram).execute()
            } else null

            val partTwoActualValue = if (partTwoExpr != null && expectedPartTwo != null) {
                val partTwoProgram = Program(buildList {
                    addAll(topLevelStatements)
                    add(Section("input", testInputEntry.expr, false, testInputEntry.span))
                    add(Section("part_two", partTwoExpr, false, partTwoExpr.span))
                })
                Compiler.compileProgram(partTwoProgram).execute()
            } else null

            val partOnePassed = if (partOneExpectedValue != null && partOneActualValue != null) {
                valuesEqual(partOneExpectedValue, partOneActualValue)
            } else null

            val partTwoPassed = if (partTwoExpectedValue != null && partTwoActualValue != null) {
                valuesEqual(partTwoExpectedValue, partTwoActualValue)
            } else null

            return TestCaseResult(
                testIndex = testIndex,
                isSlow = isSlow,
                partOnePassed = partOnePassed,
                partTwoPassed = partTwoPassed,
                partOneExpected = partOneExpectedValue,
                partOneActual = partOneActualValue,
                partTwoExpected = partTwoExpectedValue,
                partTwoActual = partTwoActualValue,
            )
        } catch (e: Exception) {
            return TestCaseResult(
                testIndex = testIndex,
                isSlow = isSlow,
                partOnePassed = null,
                partTwoPassed = null,
                partOneExpected = null,
                partOneActual = null,
                partTwoExpected = null,
                partTwoActual = null,
                error = e.message ?: "Unknown error",
            )
        }
    }

    /**
     * Evaluate a single expression in isolation (for expected values).
     */
    private fun evaluateExpr(expr: Expr, topLevelStatements: List<StatementItem>): Value {
        // Wrap expression in a program and compile
        val program = Program(buildList {
            addAll(topLevelStatements)
            add(StatementItem(ExprStatement(expr, expr.span)))
        })
        return Compiler.compileProgram(program).execute()
    }

    /**
     * Check if two values are equal.
     */
    private fun valuesEqual(a: Value, b: Value): Boolean {
        // Use the runtime equality check
        return a == b
    }
}
