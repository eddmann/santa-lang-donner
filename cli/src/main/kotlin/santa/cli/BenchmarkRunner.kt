package santa.cli

import santa.compiler.codegen.Compiler
import santa.runtime.value.Value

/**
 * Result of running a benchmark.
 */
data class BenchmarkResult(
    val value: Value,
    val iterations: Int,
    val warmupIterations: Int,
    val averageNanos: Long,
    val minNanos: Long,
    val maxNanos: Long,
) {
    val averageFormatted: String get() = BenchmarkRunner.formatDuration(averageNanos)
    val minFormatted: String get() = BenchmarkRunner.formatDuration(minNanos)
    val maxFormatted: String get() = BenchmarkRunner.formatDuration(maxNanos)
}

/**
 * Benchmark runner for Santa-lang programs.
 *
 * Compiles and runs programs multiple times to measure performance,
 * with warmup iterations to allow JIT optimization.
 */
object BenchmarkRunner {
    /**
     * Run a benchmark on the given source code.
     *
     * @param source The Santa-lang source code to benchmark
     * @param iterations Number of timed iterations to run
     * @param warmupIterations Number of warmup iterations (not timed)
     * @return BenchmarkResult containing timing statistics
     */
    fun run(
        source: String,
        iterations: Int = 100,
        warmupIterations: Int = 10,
    ): BenchmarkResult {
        val compiled = Compiler.compile(source)

        // Warmup phase - run without timing to allow JIT compilation
        repeat(warmupIterations) {
            compiled.execute()
        }

        // Timed phase
        val times = LongArray(iterations)
        var lastResult: Value? = null

        for (i in 0 until iterations) {
            val startNanos = System.nanoTime()
            lastResult = compiled.execute()
            val endNanos = System.nanoTime()
            times[i] = endNanos - startNanos
        }

        return BenchmarkResult(
            value = lastResult!!,
            iterations = iterations,
            warmupIterations = warmupIterations,
            averageNanos = times.average().toLong(),
            minNanos = times.min(),
            maxNanos = times.max(),
        )
    }

    /**
     * Format a duration in nanoseconds as a human-readable string.
     */
    fun formatDuration(nanos: Long): String {
        return when {
            nanos < 1_000 -> "$nanos ns"
            nanos < 1_000_000 -> "%.2f µs".format(nanos / 1_000.0)
            nanos < 1_000_000_000 -> "%.2f ms".format(nanos / 1_000_000.0)
            else -> "%.2f s".format(nanos / 1_000_000_000.0)
        }
    }
}
