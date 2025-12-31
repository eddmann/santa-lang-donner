package santa.runtime.value

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

class ValueTest {

    @Nested
    inner class IntValueTests {
        @Test
        fun `creates integer value`() {
            val value = IntValue(42)
            assertEquals(42L, value.value)
        }

        @Test
        fun `integer equality`() {
            assertEquals(IntValue(42), IntValue(42))
            assertNotEquals(IntValue(42), IntValue(43))
        }

        @Test
        fun `integer is hashable`() {
            assertTrue(IntValue(42).isHashable())
        }

        @Test
        fun `zero is falsy`() {
            assertFalse(IntValue(0).isTruthy())
        }

        @Test
        fun `non-zero is truthy`() {
            assertTrue(IntValue(1).isTruthy())
            assertTrue(IntValue(-1).isTruthy())
            assertTrue(IntValue(42).isTruthy())
        }

        @Test
        fun `integer type name`() {
            assertEquals("Integer", IntValue(42).typeName())
        }
    }

    @Nested
    inner class DecimalValueTests {
        @Test
        fun `creates decimal value`() {
            val value = DecimalValue(3.14)
            assertEquals(3.14, value.value)
        }

        @Test
        fun `decimal equality`() {
            assertEquals(DecimalValue(3.14), DecimalValue(3.14))
            assertNotEquals(DecimalValue(3.14), DecimalValue(2.71))
        }

        @Test
        fun `decimal is hashable`() {
            assertTrue(DecimalValue(3.14).isHashable())
        }

        @Test
        fun `zero decimal is falsy`() {
            assertFalse(DecimalValue(0.0).isTruthy())
        }

        @Test
        fun `non-zero decimal is truthy`() {
            assertTrue(DecimalValue(0.1).isTruthy())
            assertTrue(DecimalValue(-0.1).isTruthy())
        }

        @Test
        fun `decimal type name`() {
            assertEquals("Decimal", DecimalValue(3.14).typeName())
        }
    }

    @Nested
    inner class StringValueTests {
        @Test
        fun `creates string value`() {
            val value = StringValue("hello")
            assertEquals("hello", value.value)
        }

        @Test
        fun `string equality`() {
            assertEquals(StringValue("hello"), StringValue("hello"))
            assertNotEquals(StringValue("hello"), StringValue("world"))
        }

        @Test
        fun `string is hashable`() {
            assertTrue(StringValue("hello").isHashable())
        }

        @Test
        fun `empty string is falsy`() {
            assertFalse(StringValue("").isTruthy())
        }

        @Test
        fun `non-empty string is truthy`() {
            assertTrue(StringValue("a").isTruthy())
            assertTrue(StringValue("hello").isTruthy())
        }

        @Test
        fun `string type name`() {
            assertEquals("String", StringValue("hello").typeName())
        }
    }

    @Nested
    inner class BoolValueTests {
        @Test
        fun `creates boolean values`() {
            assertEquals(true, BoolValue(true).value)
            assertEquals(false, BoolValue(false).value)
        }

        @Test
        fun `boolean equality`() {
            assertEquals(BoolValue(true), BoolValue(true))
            assertEquals(BoolValue(false), BoolValue(false))
            assertNotEquals(BoolValue(true), BoolValue(false))
        }

        @Test
        fun `boolean is hashable`() {
            assertTrue(BoolValue(true).isHashable())
            assertTrue(BoolValue(false).isHashable())
        }

        @Test
        fun `true is truthy`() {
            assertTrue(BoolValue(true).isTruthy())
        }

        @Test
        fun `false is falsy`() {
            assertFalse(BoolValue(false).isTruthy())
        }

        @Test
        fun `boolean type name`() {
            assertEquals("Boolean", BoolValue(true).typeName())
        }
    }

    @Nested
    inner class NilValueTests {
        @Test
        fun `nil is singleton`() {
            assertEquals(NilValue, NilValue)
        }

        @Test
        fun `nil is hashable`() {
            assertTrue(NilValue.isHashable())
        }

        @Test
        fun `nil is falsy`() {
            assertFalse(NilValue.isTruthy())
        }

        @Test
        fun `nil type name`() {
            assertEquals("Nil", NilValue.typeName())
        }
    }

    @Nested
    inner class ValuePolymorphismTests {
        @Test
        fun `all values implement Value interface`() {
            val values: List<Value> = listOf(
                IntValue(42),
                DecimalValue(3.14),
                StringValue("hello"),
                BoolValue(true),
                NilValue
            )
            assertEquals(5, values.size)
        }

        @Test
        fun `different value types are not equal`() {
            assertNotEquals(IntValue(1) as Value, DecimalValue(1.0) as Value)
            assertNotEquals(IntValue(0) as Value, BoolValue(false) as Value)
            assertNotEquals(StringValue("") as Value, NilValue as Value)
        }
    }
}
