package santa.compiler.codegen

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import santa.runtime.value.*

/**
 * Tests for tail-call optimization (TCO).
 *
 * Per LANG.txt §8.9:
 * - TCO is available for self-recursion only (function calling itself in tail position)
 * - The recursive call must be in tail position (last expression evaluated)
 * - No operations after the recursive call
 *
 * These tests verify that tail-recursive functions can handle deep recursion
 * without causing StackOverflowError.
 */
class TailCallOptimizationTest {

    private fun eval(source: String): Value = Compiler.compile(source).execute()

    @Nested
    inner class TailRecursiveSum {
        /**
         * Classic tail-recursive sum using an accumulator.
         * sumTail(n, acc) = if n == 0 { acc } else { sumTail(n - 1, acc + n) }
         */
        @Test
        fun `tail-recursive sum works for small values`() {
            val result = eval("""
                let sumTail = |n, acc| if n == 0 { acc } else { sumTail(n - 1, acc + n) };
                sumTail(10, 0)
            """.trimIndent())
            result shouldBe IntValue(55)
        }

        @Test
        fun `tail-recursive sum handles deep recursion without stack overflow`() {
            // 100,000 recursive calls would definitely overflow without TCO
            val result = eval("""
                let sumTail = |n, acc| if n == 0 { acc } else { sumTail(n - 1, acc + n) };
                sumTail(100000, 0)
            """.trimIndent())
            // Sum of 1..100000 = n*(n+1)/2 = 5000050000
            result shouldBe IntValue(5000050000L)
        }
    }

    @Nested
    inner class TailRecursiveCountdown {
        @Test
        fun `tail-recursive countdown handles deep recursion`() {
            val result = eval("""
                let countdown = |n, acc| if n == 0 { acc } else { countdown(n - 1, acc + 1) };
                countdown(50000, 0)
            """.trimIndent())
            result shouldBe IntValue(50000)
        }
    }

    @Nested
    inner class TailPositionInMatchArms {
        /**
         * Tail calls in match arm bodies are also in tail position.
         */
        @Test
        fun `tail call in match arm body`() {
            val result = eval("""
                let process = |xs, acc| match xs { [] { acc } [head, ..tail] { process(tail, acc + head) } };
                process([1, 2, 3, 4, 5], 0)
            """.trimIndent())
            result shouldBe IntValue(15)
        }

        @Test
        fun `tail call in match handles deep recursion`() {
            val result = eval("""
                let length = |xs, acc| match xs { [] { acc } [_, ..tail] { length(tail, acc + 1) } };
                length(1..=10000 |> list, 0)
            """.trimIndent())
            result shouldBe IntValue(10000)
        }
    }

    @Nested
    inner class TailPositionInBlocks {
        /**
         * The last expression in a block is in tail position.
         */
        @Test
        fun `tail call as last expression in block`() {
            val result = eval("""
                let countdown = |n| { let next = n - 1; if n == 0 { 0 } else { countdown(next) } };
                countdown(10000)
            """.trimIndent())
            result shouldBe IntValue(0)
        }
    }

    @Nested
    inner class NonTailRecursionNotOptimized {
        /**
         * Non-tail recursive calls (with operations after the recursive call)
         * are NOT in tail position and should NOT be optimized.
         */
        @Test
        fun `non-tail recursive factorial computes correctly`() {
            // n * factorial(n-1) - multiplication after recursive call
            val result = eval("""
                let factorial = |n| if n == 0 { 1 } else { n * factorial(n - 1) };
                factorial(10)
            """.trimIndent())
            result shouldBe IntValue(3628800)
        }

        @Test
        fun `addition after recursive call is not tail position`() {
            val result = eval("""
                let sumNonTail = |n| if n == 0 { 0 } else { sumNonTail(n - 1) + n };
                sumNonTail(10)
            """.trimIndent())
            result shouldBe IntValue(55)
        }
    }

    @Nested
    inner class NestedFunctionTailCalls {
        @Test
        fun `inner tail-recursive function`() {
            val result = eval("""
                let outer = |n| { let inner = |x, acc| if x == 0 { acc } else { inner(x - 1, acc + x) }; inner(n, 0) };
                outer(100)
            """.trimIndent())
            result shouldBe IntValue(5050)
        }
    }

    @Nested
    inner class MultipleParameters {
        @Test
        fun `tail recursion with multiple parameters`() {
            val result = eval("""
                let compute = |a, b, c, n| if n == 0 { a + b + c } else { compute(a + 1, b + 2, c + 3, n - 1) };
                compute(0, 0, 0, 1000)
            """.trimIndent())
            result shouldBe IntValue(6000)
        }
    }
}
