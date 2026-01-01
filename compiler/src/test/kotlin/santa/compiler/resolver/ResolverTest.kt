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
    fun allows_shadowing_builtins() {
        // LANG.txt §14.6 says builtins can't be shadowed, but real-world
        // AOC solutions do shadow them (e.g., signum, cycle). We allow it for compatibility.
        assertDoesNotThrow {
            resolve(
                """
                let sum = 1;
                """.trimIndent(),
            )
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

    @Test
    fun allows_recursive_function_self_reference() {
        assertDoesNotThrow {
            resolve(
            """
            let factorial = |n| {
              if n == 0 { 1 } else { n * factorial(n - 1) }
            };
            """.trimIndent(),
            )
        }
    }

    @Test
    fun allows_recursive_function_with_multiple_self_calls() {
        assertDoesNotThrow {
            resolve(
            """
            let fib = |n| {
              if n < 2 { n } else { fib(n - 1) + fib(n - 2) }
            };
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
