package santa.cli

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import santa.runtime.value.IntValue

/**
 * Benchmark examples demonstrating santa-lang performance characteristics.
 *
 * Based on LANG.txt Section 15.4:
 * - Persistent collections: O(log n) updates via structural sharing
 * - Memoization: O(1) lookup for cached function results
 *
 * Note: Tail-call optimization (TCO) is deferred (Phase 8).
 */
class BenchmarkExamplesTest {

    @Nested
    inner class IterativeComputation {
        @Test
        fun `iterative sum with fold on list`() {
            // Compute sum of 1..20 using fold on a list
            val source = """
                fold(0, |acc, x| acc + x, [1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16, 17, 18, 19, 20])
            """.trimIndent()
            val result = BenchmarkRunner.run(source, iterations = 50, warmupIterations = 10)

            result.value shouldBe IntValue(210)
            println("Iterative sum (1..20): ${result.averageFormatted} (min: ${result.minFormatted}, max: ${result.maxFormatted})")
        }

        @Test
        fun `iterative factorial with fold`() {
            // Factorial of 10 = 3,628,800
            val source = """
                fold(1, |acc, x| acc * x, [1, 2, 3, 4, 5, 6, 7, 8, 9, 10])
            """.trimIndent()
            val result = BenchmarkRunner.run(source, iterations = 50, warmupIterations = 10)

            result.value shouldBe IntValue(3628800)
            println("Iterative factorial(10): ${result.averageFormatted}")
        }
    }

    @Nested
    inner class CollectionOperations {
        @Test
        fun `list mapping and filtering`() {
            // Map and filter a list
            val source = """
                let nums = [1, 2, 3, 4, 5, 6, 7, 8, 9, 10];
                let doubled = map(|x| x * 2, nums);
                let evens = filter(|x| x % 4 == 0, doubled);
                size(evens)
            """.trimIndent()
            val result = BenchmarkRunner.run(source, iterations = 50, warmupIterations = 10)

            result.value shouldBe IntValue(5)
            println("List map/filter (10 elements): ${result.averageFormatted}")
        }

        @Test
        fun `set creation and operations`() {
            // Set operations - demonstrates persistent collection sharing
            val source = """
                let s1 = set([1, 2, 3, 4, 5]);
                let s2 = set([4, 5, 6, 7, 8]);
                size(s1) + size(s2)
            """.trimIndent()
            val result = BenchmarkRunner.run(source, iterations = 50, warmupIterations = 10)

            result.value shouldBe IntValue(10)
            println("Set creation: ${result.averageFormatted}")
        }

        @Test
        fun `dictionary operations`() {
            // Dictionary creation and access
            val source = """
                let d = #{1: "one", 2: "two", 3: "three"};
                size(d)
            """.trimIndent()
            val result = BenchmarkRunner.run(source, iterations = 50, warmupIterations = 10)

            result.value shouldBe IntValue(3)
            println("Dict operations: ${result.averageFormatted}")
        }
    }

    @Nested
    inner class FunctionPerformance {
        @Test
        fun `closure capture performance`() {
            // Test closure capture overhead
            val source = """
                let x = 10;
                let y = 20;
                let z = 30;
                let f = || x + y + z;
                f() + f() + f()
            """.trimIndent()
            val result = BenchmarkRunner.run(source, iterations = 100, warmupIterations = 20)

            result.value shouldBe IntValue(180)
            println("Closure capture (3 vars, 3 calls): ${result.averageFormatted}")
        }

        @Test
        fun `higher-order function performance`() {
            // Apply a function to each element
            val source = """
                let apply_twice = |f, x| f(f(x));
                let double = |x| x * 2;
                apply_twice(double, 5)
            """.trimIndent()
            val result = BenchmarkRunner.run(source, iterations = 100, warmupIterations = 20)

            result.value shouldBe IntValue(20)
            println("Higher-order function: ${result.averageFormatted}")
        }

        @Test
        fun `memoized function performance`() {
            // Memoized function should cache results - O(1) lookup
            val source = """
                let expensive = memoize(|x| x * x * x);
                let a = expensive(10);
                let b = expensive(10);
                let c = expensive(10);
                let d = expensive(10);
                let e = expensive(10);
                a + b + c + d + e
            """.trimIndent()
            val result = BenchmarkRunner.run(source, iterations = 100, warmupIterations = 20)

            result.value shouldBe IntValue(5000)
            println("Memoized function (5 cached calls): ${result.averageFormatted}")
        }

        @Test
        fun `curried function performance`() {
            // Curried function - multiple closure captures
            val source = """
                let make_adder = |x| |y| x + y;
                let add5 = make_adder(5);
                let add10 = make_adder(10);
                add5(3) + add10(7)
            """.trimIndent()
            val result = BenchmarkRunner.run(source, iterations = 100, warmupIterations = 20)

            result.value shouldBe IntValue(25)
            println("Curried function: ${result.averageFormatted}")
        }
    }

    @Nested
    inner class StringOperations {
        @Test
        fun `string concatenation`() {
            val source = """
                "hello" + " " + "world" + "!"
            """.trimIndent()
            val result = BenchmarkRunner.run(source, iterations = 100, warmupIterations = 20)

            result.value shouldBe santa.runtime.value.StringValue("hello world!")
            println("String concatenation: ${result.averageFormatted}")
        }

        @Test
        fun `string split and join`() {
            val source = """
                let words = split(" ", "hello world from santa");
                join("-", words)
            """.trimIndent()
            val result = BenchmarkRunner.run(source, iterations = 100, warmupIterations = 20)

            result.value shouldBe santa.runtime.value.StringValue("hello-world-from-santa")
            println("String split/join: ${result.averageFormatted}")
        }

        @Test
        fun `string repetition`() {
            val source = """
                "ab" * 10
            """.trimIndent()
            val result = BenchmarkRunner.run(source, iterations = 100, warmupIterations = 20)

            result.value shouldBe santa.runtime.value.StringValue("abababababababababab")
            println("String repetition: ${result.averageFormatted}")
        }
    }

    @Nested
    inner class PatternMatching {
        @Test
        fun `match expression with multiple arms`() {
            val source = """
                let classify = |n| match n {
                    0 { "zero" }
                    1 { "one" }
                    2 { "two" }
                    n if n < 0 { "negative" }
                    _ { "many" }
                };
                classify(0) + classify(1) + classify(2) + classify(10)
            """.trimIndent()
            val result = BenchmarkRunner.run(source, iterations = 100, warmupIterations = 20)

            result.value shouldBe santa.runtime.value.StringValue("zeroonetwomany")
            println("Pattern matching (5 arms): ${result.averageFormatted}")
        }

        @Test
        fun `list pattern destructuring`() {
            // Note: ..rest pattern binds rest, but resolver may not recognize it in function scope
            // Use simpler pattern without binding rest
            val source = """
                let sum_first_two = |lst| match lst {
                    [a, b, ..] { a + b }
                    [a] { a }
                    [] { 0 }
                };
                sum_first_two([1, 2, 3]) + sum_first_two([10]) + sum_first_two([])
            """.trimIndent()
            val result = BenchmarkRunner.run(source, iterations = 100, warmupIterations = 20)

            result.value shouldBe IntValue(13)
            println("List pattern destructuring: ${result.averageFormatted}")
        }
    }

    @Nested
    inner class ArithmeticOperations {
        @Test
        fun `integer arithmetic chain`() {
            val source = """
                let a = 100;
                let b = 50;
                let c = 25;
                (a + b) * c - (a / b) + (b % c)
            """.trimIndent()
            val result = BenchmarkRunner.run(source, iterations = 100, warmupIterations = 20)

            // (100 + 50) * 25 - (100 / 50) + (50 % 25)
            // = 150 * 25 - 2 + 0
            // = 3750 - 2 = 3748
            result.value shouldBe IntValue(3748)
            println("Integer arithmetic chain: ${result.averageFormatted}")
        }

        @Test
        fun `comparison chain`() {
            val source = """
                let check = |x| x > 0 && x < 100 && x != 50;
                check(25) && check(75) && !check(50)
            """.trimIndent()
            val result = BenchmarkRunner.run(source, iterations = 100, warmupIterations = 20)

            result.value shouldBe santa.runtime.value.BoolValue(true)
            println("Comparison chain: ${result.averageFormatted}")
        }
    }

    @Nested
    inner class ControlFlow {
        @Test
        fun `simple if else chain`() {
            // Simple if/else without else if
            val source = """
                let is_positive = |n| if n > 0 { "yes" } else { "no" };
                is_positive(5) + is_positive(-5) + is_positive(0)
            """.trimIndent()
            val result = BenchmarkRunner.run(source, iterations = 100, warmupIterations = 20)

            result.value shouldBe santa.runtime.value.StringValue("yesnono")
            println("Simple if/else: ${result.averageFormatted}")
        }

        @Test
        fun `if-let binding`() {
            val source = """
                let get_value = || 42;
                if let x = get_value() { x * 2 } else { 0 }
            """.trimIndent()
            val result = BenchmarkRunner.run(source, iterations = 100, warmupIterations = 20)

            result.value shouldBe IntValue(84)
            println("If-let binding: ${result.averageFormatted}")
        }

        @Test
        fun `match based classification`() {
            // Use match expression for multi-way branching
            val source = """
                let classify = |n| match n {
                    0 { "zero" }
                    n if n < 0 { "negative" }
                    n if n < 10 { "small" }
                    n if n < 100 { "medium" }
                    _ { "large" }
                };
                classify(-5) + classify(0) + classify(5) + classify(50) + classify(500)
            """.trimIndent()
            val result = BenchmarkRunner.run(source, iterations = 100, warmupIterations = 20)

            result.value shouldBe santa.runtime.value.StringValue("negativezerosmallmediumlarge")
            println("Match-based classification: ${result.averageFormatted}")
        }
    }
}
