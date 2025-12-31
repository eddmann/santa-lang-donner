package santa.runtime.value

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

/**
 * Tests for Function values per LANG.txt §3.9.
 *
 * Functions are first-class values that can be passed around, stored, and invoked.
 */
class FunctionValueTest {

    @Nested
    inner class FunctionProperties {
        @Test
        fun `function is always truthy`() {
            val fn = object : FunctionValue() {
                override fun invoke(args: List<Value>): Value = NilValue
            }
            assertTrue(fn.isTruthy())
        }

        @Test
        fun `function is not hashable`() {
            val fn = object : FunctionValue() {
                override fun invoke(args: List<Value>): Value = NilValue
            }
            assertFalse(fn.isHashable())
        }

        @Test
        fun `function type name`() {
            val fn = object : FunctionValue() {
                override fun invoke(args: List<Value>): Value = NilValue
            }
            assertEquals("Function", fn.typeName())
        }
    }

    @Nested
    inner class FunctionInvocation {
        @Test
        fun `invoke function with no arguments`() {
            val fn = object : FunctionValue() {
                override fun invoke(args: List<Value>): Value = IntValue(42)
            }
            assertEquals(IntValue(42), fn.invoke(emptyList()))
        }

        @Test
        fun `invoke function with arguments`() {
            val fn = object : FunctionValue() {
                override fun invoke(args: List<Value>): Value {
                    val a = (args[0] as IntValue).value
                    val b = (args[1] as IntValue).value
                    return IntValue(a + b)
                }
            }
            assertEquals(IntValue(3), fn.invoke(listOf(IntValue(1), IntValue(2))))
        }
    }

    @Nested
    inner class FunctionArity {
        @Test
        fun `function with fixed arity`() {
            val fn = object : FunctionValue(arity = 2) {
                override fun invoke(args: List<Value>): Value = NilValue
            }
            assertEquals(2, fn.arity)
        }

        @Test
        fun `function with variadic arity`() {
            val fn = object : FunctionValue(arity = -1) {
                override fun invoke(args: List<Value>): Value = NilValue
            }
            assertEquals(-1, fn.arity)
            assertTrue(fn.isVariadic())
        }
    }
}
