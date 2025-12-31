package santa.compiler.codegen

import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import santa.compiler.lexer.Lexer
import santa.compiler.parser.FunctionExpr
import santa.compiler.parser.LetExpr
import santa.compiler.parser.Parser

/**
 * Tests for TailCallAnalyzer.
 */
class TailCallAnalyzerTest {

    private fun parseFunction(source: String): Pair<String, FunctionExpr> {
        val tokens = Lexer(source).lex()
        val program = Parser(tokens).parseProgram()
        val letExpr = program.items.first() as santa.compiler.parser.StatementItem
        val let = letExpr.statement as LetExpr
        val pattern = let.pattern as santa.compiler.parser.BindingPattern
        return pattern.name to (let.value as FunctionExpr)
    }

    @Nested
    inner class TailRecursiveDetection {
        @Test
        fun `detects simple tail recursion in if else branch`() {
            val (name, func) = parseFunction("""
                let f = |n, acc| if n == 0 { acc } else { f(n - 1, acc + n) }
            """.trimIndent())

            val info = TailCallAnalyzer.analyzeTailRecursion(name, func.body)

            info.shouldNotBeNull()
            info.funcName shouldBe "f"
            info.tailCalls.size shouldBe 1
        }

        @Test
        fun `detects tail recursion in match arm`() {
            val (name, func) = parseFunction("""
                let process = |xs, acc| match xs { [] { acc } [head, ..tail] { process(tail, acc + head) } }
            """.trimIndent())

            val info = TailCallAnalyzer.analyzeTailRecursion(name, func.body)

            info.shouldNotBeNull()
            info.funcName shouldBe "process"
            info.tailCalls.size shouldBe 1
        }

        @Test
        fun `detects tail recursion at end of block`() {
            val (name, func) = parseFunction("""
                let f = |n| { let next = n - 1; if n == 0 { 0 } else { f(next) } }
            """.trimIndent())

            val info = TailCallAnalyzer.analyzeTailRecursion(name, func.body)

            info.shouldNotBeNull()
            info.funcName shouldBe "f"
            info.tailCalls.size shouldBe 1
        }

        @Test
        fun `detects multiple tail calls in different branches`() {
            val (name, func) = parseFunction("""
                let f = |n, acc| if n == 0 { f(0, acc) } else { f(n - 1, acc + n) }
            """.trimIndent())

            val info = TailCallAnalyzer.analyzeTailRecursion(name, func.body)

            info.shouldNotBeNull()
            info.funcName shouldBe "f"
            info.tailCalls.size shouldBe 2
        }
    }

    @Nested
    inner class NonTailRecursionDetection {
        @Test
        fun `rejects recursion with operation after call`() {
            val (name, func) = parseFunction("""
                let factorial = |n| if n == 0 { 1 } else { n * factorial(n - 1) }
            """.trimIndent())

            val info = TailCallAnalyzer.analyzeTailRecursion(name, func.body)

            info.shouldBeNull() // Has non-tail recursive call
        }

        @Test
        fun `rejects recursion with addition after call`() {
            val (name, func) = parseFunction("""
                let sumBad = |n| if n == 0 { 0 } else { sumBad(n - 1) + n }
            """.trimIndent())

            val info = TailCallAnalyzer.analyzeTailRecursion(name, func.body)

            info.shouldBeNull()
        }

        @Test
        fun `rejects when call is used as function argument`() {
            val (name, func) = parseFunction("""
                let f = |n| if n == 0 { 0 } else { g(f(n - 1)) }
            """.trimIndent())

            val info = TailCallAnalyzer.analyzeTailRecursion(name, func.body)

            info.shouldBeNull()
        }
    }

    @Nested
    inner class NonRecursiveFunctions {
        @Test
        fun `returns null for non-recursive function`() {
            val (name, func) = parseFunction("""
                let add = |a, b| a + b
            """.trimIndent())

            val info = TailCallAnalyzer.analyzeTailRecursion(name, func.body)

            info.shouldBeNull()
        }
    }
}
