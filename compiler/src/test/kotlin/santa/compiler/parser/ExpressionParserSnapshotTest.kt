package santa.compiler.parser

import org.junit.jupiter.api.Test
import santa.compiler.lexer.Lexer
import santa.compiler.testutil.SnapshotAssertions

class ExpressionParserSnapshotTest {
    @Test
    fun parses_expressions_with_precedence_and_literals() {
        val cases = listOf(
            "1 + 2 * 3",
            "1..5 + 1",
            "a || b && c",
            "a == b < c",
            "-foo * 2",
            "[1, 2] `includes?` 2",
            "input |> lines |> map(int)",
            "_ + 1 >> _ * 2",
            "1..",
            "[1, ..rest, 3]",
            "{1, 2, 3}",
            "#{\"a\": 1, 2: \"b\"}",
            "[\"hi\", 3.14, true, nil]",
            "|x, ..rest| x + 1",
            "map([1, 2]) |x| x * 2",
            "[1, 2, 3][0]",
            "x = 1 + 2",
        )

        val snapshot = cases.joinToString("\n\n") { source ->
            val tokens = Lexer(source).lex()
            val expr = Parser(tokens).parseExpression()
            val rendered = ExprRenderer.render(expr)
            buildString {
                appendLine(source)
                append(rendered)
            }
        } + "\n"

        SnapshotAssertions.assertMatches("parser_expressions", snapshot)
    }
}
