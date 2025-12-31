package santa.cli

import io.kotest.matchers.longs.shouldBeGreaterThan
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import santa.compiler.codegen.Compiler
import santa.runtime.value.IntValue

/**
 * Tests for the benchmark harness.
 */
class BenchmarkRunnerTest {

    @Nested
    inner class BenchmarkExecution {
        @Test
        fun `benchmark runs code and measures time`() {
            val source = "1 + 1"
            val result = BenchmarkRunner.run(source, iterations = 10, warmupIterations = 5)

            result.value shouldBe IntValue(2)
            result.iterations shouldBe 10
            result.warmupIterations shouldBe 5
            result.averageNanos shouldBeGreaterThan 0L
            result.minNanos shouldBeGreaterThan 0L
            result.maxNanos shouldBeGreaterThan 0L
        }

        @Test
        fun `benchmark with complex expression`() {
            val source = """
                let f = |x| x * 2;
                let a = f(1);
                let b = f(2);
                let c = f(3);
                a + b + c
            """.trimIndent()
            val result = BenchmarkRunner.run(source, iterations = 5, warmupIterations = 2)

            result.value shouldBe IntValue(12)
            result.iterations shouldBe 5
        }

        @Test
        fun `warmup iterations are not counted in results`() {
            val source = "42"
            val result = BenchmarkRunner.run(source, iterations = 3, warmupIterations = 100)

            result.iterations shouldBe 3
            result.warmupIterations shouldBe 100
        }
    }

    @Nested
    inner class BenchmarkFormatting {
        @Test
        fun `formats nanoseconds as nanoseconds`() {
            BenchmarkRunner.formatDuration(500L) shouldBe "500 ns"
        }

        @Test
        fun `formats microseconds`() {
            BenchmarkRunner.formatDuration(5_000L) shouldBe "5.00 µs"
            BenchmarkRunner.formatDuration(1_500L) shouldBe "1.50 µs"
        }

        @Test
        fun `formats milliseconds`() {
            BenchmarkRunner.formatDuration(5_000_000L) shouldBe "5.00 ms"
            BenchmarkRunner.formatDuration(1_500_000L) shouldBe "1.50 ms"
        }

        @Test
        fun `formats seconds`() {
            BenchmarkRunner.formatDuration(5_000_000_000L) shouldBe "5.00 s"
            BenchmarkRunner.formatDuration(1_500_000_000L) shouldBe "1.50 s"
        }
    }
}
