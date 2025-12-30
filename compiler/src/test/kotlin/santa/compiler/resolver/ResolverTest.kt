package santa.compiler.resolver

import org.junit.jupiter.api.Assertions.assertDoesNotThrow
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import santa.compiler.lexer.Lexer
import santa.compiler.parser.Parser

class ResolverTest {
    @Test
    fun allows_shadowing_in_nested_scopes() {
        assertDoesNotThrow {
            resolve(
            """
            let x = 1;
            let y = {
              let x = 2;
              x
            };
            x;
            """.trimIndent(),
            )
        }
    }

    @Test
    fun rejects_shadowing_protected_builtins() {
        val error = assertThrows(ResolveException::class.java) {
            resolve(
                """
                let sum = 1;
                """.trimIndent(),
            )
        }
        if (error.message?.contains("built-in") != true) {
            throw AssertionError("Expected error message to mention built-in")
        }
    }

    @Test
    fun rejects_return_outside_function() {
        assertThrows(ResolveException::class.java) {
            resolve(
                """
                return 1;
                """.trimIndent(),
            )
        }
    }

    @Test
    fun rejects_break_outside_iteration() {
        assertThrows(ResolveException::class.java) {
            resolve(
                """
                break 1;
                """.trimIndent(),
            )
        }
    }

    @Test
    fun allows_return_inside_function_body() {
        assertDoesNotThrow {
            resolve(
            """
            let f = |x| { return x; };
            """.trimIndent(),
            )
        }
    }

    private fun resolve(source: String) {
        val tokens = Lexer(source).lex()
        val program = Parser(tokens).parseProgram()
        Resolver().resolve(program)
    }
}
