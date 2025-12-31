package santa.compiler.parser

import org.junit.jupiter.api.Test
import santa.compiler.lexer.Lexer
import santa.compiler.testutil.SnapshotAssertions

class StatementParserSnapshotTest {
    @Test
    fun parses_statements_patterns_and_sections() {
        val cases = listOf(
            """
            let [first, ..rest, _] = [1, 2, 3];
            let mut [x, y] = [1, 2];
            """.trimIndent(),
            """
            return 1;
            break 2;
            """.trimIndent(),
            """
            if let [x, ..rest] = value { x } else { 0 }
            """.trimIndent(),
            """
            match list {
              [first, ..rest] if first > 0 { rest }
              _ { [] }
            }
            """.trimIndent(),
            """
            input: 1 + 2
            part_one: input |> sum;
            """.trimIndent(),
            """
            let x = {};
            if true { }
            let dict = #{a, b: 2};
            """.trimIndent(),
            // Test block parsing
            """
            test: {
              input: "hello"
              part_one: 10
              part_two: 20
            }
            """.trimIndent(),
            // Test block with @slow attribute
            """
            @slow
            test: {
              input: 42
              part_one: 100
            }
            """.trimIndent(),
            // Multiple test blocks
            """
            input: 1
            part_one: input + 1
            test: {
              input: 5
              part_one: 6
            }
            @slow
            test: {
              input: 10
              part_one: 11
            }
            """.trimIndent(),
        )

        val snapshot = cases.joinToString("\n\n") { source ->
            val tokens = Lexer(source).lex()
            val program = Parser(tokens).parseProgram()
            val rendered = ExprRenderer.renderProgram(program)
            buildString {
                appendLine(source)
                append(rendered)
            }
        } + "\n"

        SnapshotAssertions.assertMatches("parser_statements", snapshot)
    }
}
