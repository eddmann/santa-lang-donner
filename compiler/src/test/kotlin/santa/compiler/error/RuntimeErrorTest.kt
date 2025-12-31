package santa.compiler.error

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.string.shouldContain
import org.junit.jupiter.api.Test
import santa.compiler.codegen.Compiler
import santa.runtime.SantaRuntimeException

/**
 * Tests verifying that runtime exceptions are properly propagated with clear messages.
 */
class RuntimeErrorTest {

    @Test
    fun `type mismatch throws SantaRuntimeException with message`() {
        val exception = shouldThrow<SantaRuntimeException> {
            Compiler.compile("1 + \"hello\"").execute()
        }
        exception.message shouldContain "Cannot add"
        exception.message shouldContain "Integer"
        exception.message shouldContain "String"
    }

    @Test
    fun `negating non-numeric throws SantaRuntimeException`() {
        val exception = shouldThrow<SantaRuntimeException> {
            Compiler.compile("-\"hello\"").execute()
        }
        exception.message shouldContain "negate"
    }

    @Test
    fun `comparing incompatible types throws SantaRuntimeException`() {
        val exception = shouldThrow<SantaRuntimeException> {
            Compiler.compile("1 < \"hello\"").execute()
        }
        exception.message shouldContain "compare"
    }
}
