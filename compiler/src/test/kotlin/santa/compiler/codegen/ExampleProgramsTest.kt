package santa.compiler.codegen

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import santa.runtime.value.*

/**
 * Integration tests for LANG.txt Appendix D example programs.
 *
 * These tests verify that complete santa-lang programs from the specification
 * execute correctly through the full compilation pipeline.
 *
 * Note: Some examples require features not yet implemented:
 * - Recursive functions (self-reference in let bindings)
 * - Range expressions (2..n)
 * - Return/break statements
 * These are deferred to later phases.
 */
class ExampleProgramsTest {

    private fun eval(source: String): Value = Compiler.compile(source).execute()

    @Nested
    inner class Example2AocDay1StylePartial {
        // AOC Day 1 style - testing parts that work without ranges/return

        @Test
        fun `pipeline with map and sum`() {
            // Simplified AOC-style parsing and computation
            val result = eval("""
                let data = [[1000, 2000, 3000], [4000], [5000, 6000]];
                data |> map(sum) |> max
            """.trimIndent())
            result shouldBe IntValue(11000)
        }

        @Test
        fun `composition for parsing pipeline`() {
            // Test composition: split >> map(ints >> sum)
            eval("""
                let parse_inventories = |input| {
                    input |> split("\n\n") |> map(|group| {
                        group |> ints |> sum
                    })
                };
                parse_inventories("1000\n2000\n3000\n\n4000\n\n5000\n6000") |> max
            """.trimIndent()) shouldBe IntValue(11000)
        }

        @Test
        fun `sort with comparator and take`() {
            // For descending order (largest first), comparator returns true if a should come after b
            // So we use |a, b| a < b (smaller elements come after larger ones)
            val result = eval("""
                let inventories = [6000, 4000, 11000, 24000, 10000];
                inventories |> sort(|a, b| a < b) |> take(3) |> sum
            """.trimIndent())
            result shouldBe IntValue(45000)  // 24000 + 11000 + 10000
        }

        @Test
        fun `ascending sort`() {
            // For ascending order (smallest first), comparator returns true if a should come after b
            // So we use |a, b| a > b (larger elements come after smaller ones)
            val result = eval("""
                let nums = [5, 2, 8, 1, 9];
                nums |> sort(|a, b| a > b) |> take(3) |> sum
            """.trimIndent())
            result shouldBe IntValue(8)  // 1 + 2 + 5
        }
    }

    @Nested
    inner class Example3WordFrequencyCounterPartial {
        @Test
        fun `fold builds frequency dictionary`() {
            val result = eval("""
                let text = "hello world hello";
                text
                  |> split(" ")
                  |> fold(#{}) |freq, word| {
                    update_d(word, 0, |x| x + 1, freq)
                  }
            """.trimIndent()) as DictValue
            result.get(StringValue("hello")) shouldBe IntValue(2)
            result.get(StringValue("world")) shouldBe IntValue(1)
        }

        @Test
        fun `word frequency sorted by count`() {
            // Sort descending by count: a[1] < b[1] means larger counts come first
            val result = eval("""
                let text = "a b a b a c";
                let freq = text
                  |> split(" ")
                  |> fold(#{}) |freq, word| {
                    update_d(word, 0, |x| x + 1, freq)
                  };
                freq |> list |> sort(|a, b| a[1] < b[1]) |> first
            """.trimIndent()) as ListValue
            result.get(0) shouldBe StringValue("a")
            result.get(1) shouldBe IntValue(3)
        }
    }

    @Nested
    inner class Example5RecursiveListSumNonRecursive {
        // Non-recursive versions using built-in functions

        @Test
        fun `list sum using fold`() {
            eval("""
                let sum_list = |lst| fold(0, |acc, x| acc + x, lst);
                sum_list([1, 2, 3, 4, 5])
            """.trimIndent()) shouldBe IntValue(15)
        }

        @Test
        fun `list sum of empty list`() {
            eval("""
                let sum_list = |lst| fold(0, |acc, x| acc + x, lst);
                sum_list([])
            """.trimIndent()) shouldBe IntValue(0)
        }

        @Test
        fun `list sum via builtin`() {
            // The builtin sum is the idiomatic way
            eval("[1, 2, 3, 4, 5] |> sum") shouldBe IntValue(15)
        }

        @Test
        fun `match with head and tail patterns`() {
            // Test the pattern matching itself works, even if not recursive
            eval("""
                match [1, 2, 3] {
                  [] { 0 }
                  [head, ..tail] { head }
                }
            """.trimIndent()) shouldBe IntValue(1)
        }

        @Test
        fun `match empty list`() {
            eval("""
                match [] {
                  [] { 0 }
                  [head, ..tail] { head }
                }
            """.trimIndent()) shouldBe IntValue(0)
        }
    }

    @Nested
    inner class PipelineAndCompositionIntegration {
        @Test
        fun `complex pipeline chain`() {
            // input |> lines |> map(int) |> filter(_ > 0) |> sum
            eval("""
                let input = "1\n-2\n3\n-4\n5";
                input |> lines |> map(int) |> filter(|x| x > 0) |> sum
            """.trimIndent()) shouldBe IntValue(9)
        }

        @Test
        fun `composition creates reusable pipelines`() {
            eval("""
                let double_all = map(|x| x * 2);
                let positive = filter(|x| x > 0);
                let process = double_all >> positive >> sum;
                process([-1, 2, -3, 4])
            """.trimIndent()) shouldBe IntValue(12)  // (2*2) + (4*2) = 4 + 8 = 12
        }

        @Test
        fun `mixed pipeline and composition`() {
            eval("""
                let inc = |x| x + 1;
                let double = |x| x * 2;
                let transform = inc >> double;
                [1, 2, 3] |> map(transform) |> sum
            """.trimIndent()) shouldBe IntValue(18)  // (1+1)*2 + (2+1)*2 + (3+1)*2 = 4 + 6 + 8 = 18
        }

        @Test
        fun `pipeline with partial application`() {
            eval("""
                [1, 2, 3] |> map(|x| x * 10) |> filter(|x| x > 15) |> size
            """.trimIndent()) shouldBe IntValue(2)  // 20, 30 pass the filter
        }

        @Test
        fun `pipeline with string functions`() {
            eval("""
                "hello world" |> split(" ") |> map(upper) |> join("-")
            """.trimIndent()) shouldBe StringValue("HELLO-WORLD")
        }

        @Test
        fun `nested composition`() {
            eval("""
                let a = |x| x + 1;
                let b = |x| x * 2;
                let c = |x| x - 3;
                let abc = a >> b >> c;
                abc(5)
            """.trimIndent()) shouldBe IntValue(9)  // ((5+1)*2)-3 = 12-3 = 9
        }
    }

    @Nested
    inner class CollectionOperationsIntegration {
        @Test
        fun `flat_map for nested lists`() {
            val result = eval("""
                [[1, 2], [3, 4]] |> flat_map(|x| x)
            """.trimIndent()) as ListValue
            result.size() shouldBe 4
            result.get(0) shouldBe IntValue(1)
            result.get(3) shouldBe IntValue(4)
        }

        @Test
        fun `chunk and map`() {
            eval("""
                [1, 2, 3, 4, 5, 6] |> chunk(2) |> map(sum)
            """.trimIndent()).let { result ->
                result as ListValue
                result.size() shouldBe 3
                result.get(0) shouldBe IntValue(3)   // 1+2
                result.get(1) shouldBe IntValue(7)   // 3+4
                result.get(2) shouldBe IntValue(11)  // 5+6
            }
        }

        @Test
        fun `filter_map combines filter and map`() {
            // filter_map: keep only elements where mapper returns truthy
            val result = eval("""
                [1, 2, 3, 4, 5] |> filter_map(|x| if x % 2 == 0 { x * 10 })
            """.trimIndent()) as ListValue
            result.size() shouldBe 2
            result.get(0) shouldBe IntValue(20)
            result.get(1) shouldBe IntValue(40)
        }

        @Test
        fun `find_map returns first mapped truthy`() {
            eval("""
                [1, 2, 3, 4] |> find_map(|x| if x > 2 { x * 10 })
            """.trimIndent()) shouldBe IntValue(30)
        }

        @Test
        fun `scan shows intermediate results including initial`() {
            // Note: This includes initial value for AOC compatibility (deviates from LANG.txt)
            val result = eval("""
                [1, 2, 3, 4] |> scan(0, |acc, x| acc + x)
            """.trimIndent()) as ListValue
            result.size() shouldBe 5
            result.get(0) shouldBe IntValue(0)   // Initial
            result.get(1) shouldBe IntValue(1)   // 0+1
            result.get(2) shouldBe IntValue(3)   // 1+2
            result.get(3) shouldBe IntValue(6)   // 3+3
            result.get(4) shouldBe IntValue(10)  // 6+4
        }
    }

    @Nested
    inner class MemoizationIntegration {
        @Test
        fun `memoized function caches results`() {
            // Can't test recursive memoized functions yet, but can test basic memoization
            eval("""
                let expensive = memoize(|x| x * x * x);
                let a = expensive(5);
                let b = expensive(5);
                a + b
            """.trimIndent()) shouldBe IntValue(250)  // 125 + 125
        }

        @Test
        fun `memoized function with different args`() {
            eval("""
                let square = memoize(|x| x * x);
                square(3) + square(4) + square(3)
            """.trimIndent()) shouldBe IntValue(34)  // 9 + 16 + 9
        }
    }

    @Nested
    inner class StringProcessingIntegration {
        @Test
        fun `parse and process CSV-like data`() {
            eval("""
                let data = "name,age,score\nalice,25,95\nbob,30,88";
                data |> lines |> rest |> map(|line| {
                    let parts = split(",", line);
                    parts[2] |> int
                }) |> sum
            """.trimIndent()) shouldBe IntValue(183)  // 95 + 88
        }

        @Test
        fun `join after transformation`() {
            eval("""
                ["hello", "world"] |> map(|s| upper(s)) |> join(" ")
            """.trimIndent()) shouldBe StringValue("HELLO WORLD")
        }
    }
}
