package santa.runtime.value

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

/**
 * Tests for Range values per LANG.txt §3.4.
 *
 * Ranges represent lazy sequences of integers.
 */
class RangeValueTest {

    @Nested
    inner class ExclusiveRange {
        @Test
        fun `ascending exclusive range`() {
            // 1..5 produces 1, 2, 3, 4
            val range = RangeValue.exclusive(1, 5)
            assertEquals(listOf(1L, 2L, 3L, 4L), range.toList())
        }

        @Test
        fun `descending exclusive range auto-reverses`() {
            // 5..1 produces 5, 4, 3, 2
            val range = RangeValue.exclusive(5, 1)
            assertEquals(listOf(5L, 4L, 3L, 2L), range.toList())
        }

        @Test
        fun `empty exclusive range when start equals end`() {
            // 5..5 produces [] (empty)
            val range = RangeValue.exclusive(5, 5)
            assertEquals(emptyList<Long>(), range.toList())
        }

        @Test
        fun `negative exclusive range`() {
            val range = RangeValue.exclusive(-2, 2)
            assertEquals(listOf(-2L, -1L, 0L, 1L), range.toList())
        }
    }

    @Nested
    inner class InclusiveRange {
        @Test
        fun `ascending inclusive range`() {
            // 1..=5 produces 1, 2, 3, 4, 5
            val range = RangeValue.inclusive(1, 5)
            assertEquals(listOf(1L, 2L, 3L, 4L, 5L), range.toList())
        }

        @Test
        fun `descending inclusive range auto-reverses`() {
            // 5..=1 produces 5, 4, 3, 2, 1
            val range = RangeValue.inclusive(5, 1)
            assertEquals(listOf(5L, 4L, 3L, 2L, 1L), range.toList())
        }

        @Test
        fun `single element inclusive range`() {
            // 5..=5 produces [5]
            val range = RangeValue.inclusive(5, 5)
            assertEquals(listOf(5L), range.toList())
        }

        @Test
        fun `negative inclusive range`() {
            val range = RangeValue.inclusive(-2, 2)
            assertEquals(listOf(-2L, -1L, 0L, 1L, 2L), range.toList())
        }
    }

    @Nested
    inner class UnboundedRange {
        @Test
        fun `unbounded range starts at value`() {
            // 1.. produces 1, 2, 3, 4, ... (infinite)
            val range = RangeValue.unbounded(1)
            assertTrue(range.isUnbounded())
            assertEquals(listOf(1L, 2L, 3L, 4L, 5L), range.take(5))
        }

        @Test
        fun `negative unbounded range`() {
            val range = RangeValue.unbounded(-5)
            assertEquals(listOf(-5L, -4L, -3L, -2L, -1L), range.take(5))
        }
    }

    @Nested
    inner class RangeProperties {
        @Test
        fun `range is always truthy`() {
            // Lazy sequences are always truthy per LANG.txt §14.1
            assertTrue(RangeValue.exclusive(1, 5).isTruthy())
            assertTrue(RangeValue.exclusive(5, 5).isTruthy()) // even empty ranges
            assertTrue(RangeValue.unbounded(1).isTruthy())
        }

        @Test
        fun `range is not hashable`() {
            // Lazy sequences cannot be used as set elements or dict keys
            assertFalse(RangeValue.exclusive(1, 5).isHashable())
        }

        @Test
        fun `range type name`() {
            assertEquals("Range", RangeValue.exclusive(1, 5).typeName())
        }
    }
}
