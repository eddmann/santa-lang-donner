package santa.compiler.codegen

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import santa.runtime.value.*

/**
 * Tests for bytecode optimizations.
 *
 * These tests verify that optimizations produce correct results.
 * The actual bytecode efficiency is validated separately in benchmarks.
 */
class OptimizationTest {

    private fun eval(source: String): Value = Compiler.compile(source).execute()

    @Nested
    inner class ConstantFolding {
        @Test
        fun `constant folding for integer addition`() {
            // Should fold to single constant at compile time
            eval("1 + 2") shouldBe IntValue(3)
        }

        @Test
        fun `constant folding for chained arithmetic`() {
            // Should fold 1 + 2 * 3 to 7 at compile time
            eval("1 + 2 * 3") shouldBe IntValue(7)
        }

        @Test
        fun `constant folding for subtraction`() {
            eval("10 - 3") shouldBe IntValue(7)
        }

        @Test
        fun `constant folding for multiplication`() {
            eval("4 * 5") shouldBe IntValue(20)
        }

        @Test
        fun `constant folding for division`() {
            eval("20 / 4") shouldBe IntValue(5)
        }

        @Test
        fun `constant folding for modulo`() {
            eval("17 % 5") shouldBe IntValue(2)
        }

        @Test
        fun `constant folding for nested arithmetic`() {
            eval("(1 + 2) * (3 + 4)") shouldBe IntValue(21)
        }

        @Test
        fun `constant folding preserves correct precedence`() {
            // 2 + 3 * 4 = 2 + 12 = 14, not (2 + 3) * 4 = 20
            eval("2 + 3 * 4") shouldBe IntValue(14)
        }

        @Test
        fun `constant folding for unary minus`() {
            // Already implemented - verify it still works
            eval("-5") shouldBe IntValue(-5)
        }

        @Test
        fun `constant folding for double negation`() {
            eval("--5") shouldBe IntValue(5)
        }

        @Test
        fun `constant folding for decimal literals`() {
            eval("1.5 + 2.5") shouldBe DecimalValue(4.0)
        }

        @Test
        fun `constant folding for string concatenation`() {
            eval("\"hello\" + \" \" + \"world\"") shouldBe StringValue("hello world")
        }
    }

    @Nested
    inner class BooleanOptimizations {
        @Test
        fun `true and x evaluates x`() {
            eval("true && true") shouldBe BoolValue(true)
            eval("true && false") shouldBe BoolValue(false)
        }

        @Test
        fun `false and x short circuits`() {
            // Short-circuit: false && anything = false
            eval("false && true") shouldBe BoolValue(false)
        }

        @Test
        fun `true or x short circuits`() {
            // Short-circuit: true || anything = true
            eval("true || false") shouldBe BoolValue(true)
        }

        @Test
        fun `false or x evaluates x`() {
            eval("false || true") shouldBe BoolValue(true)
            eval("false || false") shouldBe BoolValue(false)
        }

        @Test
        fun `not true folds to false`() {
            eval("!true") shouldBe BoolValue(false)
        }

        @Test
        fun `not false folds to true`() {
            eval("!false") shouldBe BoolValue(true)
        }
    }

    @Nested
    inner class ConditionalOptimizations {
        @Test
        fun `if true always takes then branch`() {
            eval("if true { 1 } else { 2 }") shouldBe IntValue(1)
        }

        @Test
        fun `if false always takes else branch`() {
            eval("if false { 1 } else { 2 }") shouldBe IntValue(2)
        }

        @Test
        fun `if true without else returns then value`() {
            eval("if true { 42 }") shouldBe IntValue(42)
        }

        @Test
        fun `if false without else returns nil`() {
            eval("if false { 42 }") shouldBe NilValue
        }
    }

    @Nested
    inner class ComparisonOptimizations {
        @Test
        fun `constant comparison equals`() {
            eval("1 == 1") shouldBe BoolValue(true)
            eval("1 == 2") shouldBe BoolValue(false)
        }

        @Test
        fun `constant comparison not equals`() {
            eval("1 != 2") shouldBe BoolValue(true)
            eval("1 != 1") shouldBe BoolValue(false)
        }

        @Test
        fun `constant comparison less than`() {
            eval("1 < 2") shouldBe BoolValue(true)
            eval("2 < 1") shouldBe BoolValue(false)
        }

        @Test
        fun `constant comparison greater than`() {
            eval("2 > 1") shouldBe BoolValue(true)
            eval("1 > 2") shouldBe BoolValue(false)
        }
    }
}
