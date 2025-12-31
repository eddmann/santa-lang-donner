package santa.runtime.value

import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.persistentSetOf
import kotlinx.collections.immutable.persistentMapOf
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

class CollectionValueTest {

    @Nested
    inner class ListValueTests {
        @Test
        fun `creates list value`() {
            val list = ListValue(persistentListOf(IntValue(1), IntValue(2), IntValue(3)))
            assertEquals(3, list.elements.size)
        }

        @Test
        fun `empty list is falsy`() {
            assertFalse(ListValue(persistentListOf()).isTruthy())
        }

        @Test
        fun `non-empty list is truthy`() {
            assertTrue(ListValue(persistentListOf(IntValue(1))).isTruthy())
        }

        @Test
        fun `list with hashable elements is hashable`() {
            val list = ListValue(persistentListOf(IntValue(1), StringValue("a")))
            assertTrue(list.isHashable())
        }

        @Test
        fun `list type name`() {
            assertEquals("List", ListValue(persistentListOf()).typeName())
        }

        @Test
        fun `list equality`() {
            val list1 = ListValue(persistentListOf(IntValue(1), IntValue(2)))
            val list2 = ListValue(persistentListOf(IntValue(1), IntValue(2)))
            val list3 = ListValue(persistentListOf(IntValue(1), IntValue(3)))
            assertEquals(list1, list2)
            assertNotEquals(list1, list3)
        }

        @Test
        fun `list size`() {
            val list = ListValue(persistentListOf(IntValue(1), IntValue(2), IntValue(3)))
            assertEquals(3, list.size())
        }

        @Test
        fun `list get by index`() {
            val list = ListValue(persistentListOf(IntValue(10), IntValue(20), IntValue(30)))
            assertEquals(IntValue(10), list.get(0))
            assertEquals(IntValue(20), list.get(1))
            assertEquals(IntValue(30), list.get(2))
        }

        @Test
        fun `list get with negative index`() {
            val list = ListValue(persistentListOf(IntValue(10), IntValue(20), IntValue(30)))
            assertEquals(IntValue(30), list.get(-1))
            assertEquals(IntValue(20), list.get(-2))
            assertEquals(IntValue(10), list.get(-3))
        }

        @Test
        fun `list get out of bounds returns nil`() {
            val list = ListValue(persistentListOf(IntValue(10), IntValue(20)))
            assertEquals(NilValue, list.get(5))
            assertEquals(NilValue, list.get(-5))
        }
    }

    @Nested
    inner class SetValueTests {
        @Test
        fun `creates set value`() {
            val set = SetValue(persistentSetOf(IntValue(1), IntValue(2), IntValue(3)))
            assertEquals(3, set.elements.size)
        }

        @Test
        fun `empty set is falsy`() {
            assertFalse(SetValue(persistentSetOf()).isTruthy())
        }

        @Test
        fun `non-empty set is truthy`() {
            assertTrue(SetValue(persistentSetOf(IntValue(1))).isTruthy())
        }

        @Test
        fun `set is always hashable`() {
            assertTrue(SetValue(persistentSetOf(IntValue(1))).isHashable())
        }

        @Test
        fun `set type name`() {
            assertEquals("Set", SetValue(persistentSetOf()).typeName())
        }

        @Test
        fun `set equality ignores order`() {
            val set1 = SetValue(persistentSetOf(IntValue(1), IntValue(2)))
            val set2 = SetValue(persistentSetOf(IntValue(2), IntValue(1)))
            assertEquals(set1, set2)
        }

        @Test
        fun `set size`() {
            val set = SetValue(persistentSetOf(IntValue(1), IntValue(2), IntValue(3)))
            assertEquals(3, set.size())
        }

        @Test
        fun `set contains`() {
            val set = SetValue(persistentSetOf(IntValue(1), IntValue(2)))
            assertTrue(set.contains(IntValue(1)))
            assertTrue(set.contains(IntValue(2)))
            assertFalse(set.contains(IntValue(3)))
        }
    }

    @Nested
    inner class DictValueTests {
        @Test
        fun `creates dict value`() {
            val dict = DictValue(persistentMapOf(
                StringValue("a") to IntValue(1),
                StringValue("b") to IntValue(2)
            ))
            assertEquals(2, dict.entries.size)
        }

        @Test
        fun `empty dict is falsy`() {
            assertFalse(DictValue(persistentMapOf()).isTruthy())
        }

        @Test
        fun `non-empty dict is truthy`() {
            assertTrue(DictValue(persistentMapOf(StringValue("a") to IntValue(1))).isTruthy())
        }

        @Test
        fun `dict is not hashable`() {
            assertFalse(DictValue(persistentMapOf()).isHashable())
        }

        @Test
        fun `dict type name`() {
            assertEquals("Dictionary", DictValue(persistentMapOf()).typeName())
        }

        @Test
        fun `dict get existing key`() {
            val dict = DictValue(persistentMapOf(
                StringValue("a") to IntValue(1),
                StringValue("b") to IntValue(2)
            ))
            assertEquals(IntValue(1), dict.get(StringValue("a")))
            assertEquals(IntValue(2), dict.get(StringValue("b")))
        }

        @Test
        fun `dict get missing key returns nil`() {
            val dict = DictValue(persistentMapOf(StringValue("a") to IntValue(1)))
            assertEquals(NilValue, dict.get(StringValue("missing")))
        }

        @Test
        fun `dict size`() {
            val dict = DictValue(persistentMapOf(
                StringValue("a") to IntValue(1),
                StringValue("b") to IntValue(2)
            ))
            assertEquals(2, dict.size())
        }
    }
}
