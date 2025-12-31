package santa.cli

import org.junit.jupiter.api.Test
import santa.runtime.value.IntValue

/**
 * Summary benchmark test that outputs results to verify performance characteristics.
 */
class BenchmarkSummaryTest {

    @Test
    fun `benchmark summary report`() {
        println("\n=== Santa-Lang Benchmark Summary ===\n")

        // Simple expressions
        benchmark("Integer literal", "42")
        benchmark("Arithmetic", "1 + 2 * 3 - 4")
        benchmark("String concat", "\"hello\" + \" \" + \"world\"")

        // Collections
        benchmark("List creation", "[1, 2, 3, 4, 5, 6, 7, 8, 9, 10]")
        benchmark("Map over list", "map(|x| x * 2, [1, 2, 3, 4, 5])")
        benchmark("Filter list", "filter(|x| x > 3, [1, 2, 3, 4, 5])")
        benchmark("Fold list", "fold(0, |a, b| a + b, [1, 2, 3, 4, 5])")

        // Functions
        benchmark("Simple lambda", "let f = |x| x * 2; f(5)")
        benchmark("Closure capture", "let x = 10; let f = || x; f()")
        benchmark("Higher-order", "let apply = |f, x| f(x); let dbl = |x| x * 2; apply(dbl, 5)")

        // Control flow
        benchmark("If expression", "if true { 1 } else { 2 }")
        benchmark("Match literal", "match 2 { 1 { \"one\" } 2 { \"two\" } _ { \"other\" } }")
        benchmark("Match with guard", "match 5 { n if n > 3 { \"big\" } _ { \"small\" } }")

        println("\n=== End Benchmark Summary ===\n")
    }

    private fun benchmark(name: String, source: String) {
        val result = BenchmarkRunner.run(source, iterations = 100, warmupIterations = 20)
        val padded = name.padEnd(20)
        println("$padded: ${result.averageFormatted.padStart(10)} (min: ${result.minFormatted}, max: ${result.maxFormatted})")
    }
}
