package santa.runtime

import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import santa.runtime.value.NilValue
import santa.runtime.value.StringValue
import java.io.ByteArrayOutputStream
import java.io.PrintStream
import java.nio.file.Path
import kotlin.io.path.writeText

/**
 * Tests for external functions: read, puts.
 */
class ExternalFunctionsTest {

    @Test
    fun `puts with single string returns nil`() {
        val result = Builtins.puts(StringValue("hello"))
        result shouldBe NilValue
    }

    @Test
    fun `puts prints to stdout`() {
        val originalOut = System.out
        val outputStream = ByteArrayOutputStream()
        System.setOut(PrintStream(outputStream))
        try {
            Builtins.puts(StringValue("hello"))
            outputStream.toString() shouldContain "hello"
        } finally {
            System.setOut(originalOut)
        }
    }

    @Test
    fun `read local file returns content`(@TempDir tempDir: Path) {
        val testFile = tempDir.resolve("test.txt")
        testFile.writeText("hello world")

        val result = Builtins.read(StringValue(testFile.toString()))
        result shouldBe StringValue("hello world")
    }

    @Test
    fun `read non-existent file returns nil`() {
        val result = Builtins.read(StringValue("/non/existent/path.txt"))
        result shouldBe NilValue
    }
}
