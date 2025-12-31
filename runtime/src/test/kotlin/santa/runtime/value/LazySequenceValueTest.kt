package santa.runtime.value

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

/**
 * Tests for LazySequence values per LANG.txt §3.8.
 *
 * Lazy sequences provide infinite sequences with deferred computation.
 */
class LazySequenceValueTest {

    @Nested
    inner class Iterate {
        @Test
        fun `iterate generates values by applying function`() {
            // iterate(_ + 2, 0) produces 0, 2, 4, 6, ...
            val seq = LazySequenceValue.iterate(IntValue(0)) { value ->
                IntValue((value as IntValue).value + 2)
            }
            val first5 = seq.take(5)
            assertEquals(
                listOf(IntValue(0), IntValue(2), IntValue(4), IntValue(6), IntValue(8)),
                first5
            )
        }
    }

    @Nested
    inner class Repeat {
        @Test
        fun `repeat generates same value infinitely`() {
            // repeat(5) produces 5, 5, 5, 5, ...
            val seq = LazySequenceValue.repeat(IntValue(5))
            val first5 = seq.take(5)
            assertEquals(
                listOf(IntValue(5), IntValue(5), IntValue(5), IntValue(5), IntValue(5)),
                first5
            )
        }
    }

    @Nested
    inner class Cycle {
        @Test
        fun `cycle repeats list infinitely`() {
            // cycle([1, 2, 3]) produces 1, 2, 3, 1, 2, 3, ...
            val seq = LazySequenceValue.cycle(listOf(IntValue(1), IntValue(2), IntValue(3)))
            val first7 = seq.take(7)
            assertEquals(
                listOf(IntValue(1), IntValue(2), IntValue(3), IntValue(1), IntValue(2), IntValue(3), IntValue(1)),
                first7
            )
        }

        @Test
        fun `cycle of empty list produces empty sequence`() {
            val seq = LazySequenceValue.cycle(emptyList())
            assertEquals(emptyList<Value>(), seq.take(5))
        }
    }

    @Nested
    inner class LazySequenceProperties {
        @Test
        fun `lazy sequence is always truthy`() {
            assertTrue(LazySequenceValue.repeat(IntValue(1)).isTruthy())
        }

        @Test
        fun `lazy sequence is not hashable`() {
            assertFalse(LazySequenceValue.repeat(IntValue(1)).isHashable())
        }

        @Test
        fun `lazy sequence type name`() {
            assertEquals("LazySequence", LazySequenceValue.repeat(IntValue(1)).typeName())
        }
    }
}
