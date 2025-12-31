package santa.runtime.value

import kotlinx.collections.immutable.persistentListOf
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
    inner class Zip {
        @Test
        fun `zip two finite lists`() {
            val list1 = ListValue(persistentListOf(IntValue(1), IntValue(2), IntValue(3)))
            val list2 = ListValue(persistentListOf(StringValue("a"), StringValue("b"), StringValue("c")))
            val result = LazySequenceValue.zip(listOf(list1, list2))

            // Should return a list (not lazy) since inputs are finite
            assertTrue(result is ListValue)
            val listResult = result as ListValue
            assertEquals(3, listResult.size())

            // Each element is a list of corresponding elements
            val first = listResult.get(0) as ListValue
            assertEquals(IntValue(1), first.get(0))
            assertEquals(StringValue("a"), first.get(1))
        }

        @Test
        fun `zip stops at shortest collection`() {
            val list1 = ListValue(persistentListOf(IntValue(1), IntValue(2)))
            val list2 = ListValue(persistentListOf(StringValue("a"), StringValue("b"), StringValue("c")))
            val result = LazySequenceValue.zip(listOf(list1, list2)) as ListValue

            assertEquals(2, result.size())  // stopped at shortest
        }

        @Test
        fun `zip with unbounded range returns lazy sequence`() {
            val unbounded = RangeValue.unbounded(0)
            val list = ListValue(persistentListOf(StringValue("a"), StringValue("b"), StringValue("c")))
            val result = LazySequenceValue.zip(listOf(unbounded, list))

            // One finite, one infinite -> should return List
            assertTrue(result is ListValue)
            val listResult = result as ListValue
            assertEquals(3, listResult.size())
        }

        @Test
        fun `zip two infinite sequences returns lazy sequence`() {
            val range1 = RangeValue.unbounded(0)
            val range2 = RangeValue.unbounded(1)
            val result = LazySequenceValue.zip(listOf(range1, range2))

            // Both infinite -> returns LazySequence
            assertTrue(result is LazySequenceValue)
            val lazyResult = result as LazySequenceValue
            val first3 = lazyResult.take(3)

            assertEquals(3, first3.size)
            val first = first3[0] as ListValue
            assertEquals(IntValue(0), first.get(0))
            assertEquals(IntValue(1), first.get(1))
        }

        @Test
        fun `zip with string`() {
            val range = RangeValue.unbounded(0)
            val str = StringValue("abc")
            val list = ListValue(persistentListOf(DecimalValue(1.5), DecimalValue(2.5), DecimalValue(3.5)))
            val result = LazySequenceValue.zip(listOf(range, str, list)) as ListValue

            assertEquals(3, result.size())
            val first = result.get(0) as ListValue
            assertEquals(IntValue(0), first.get(0))
            assertEquals(StringValue("a"), first.get(1))
            assertEquals(DecimalValue(1.5), first.get(2))
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
