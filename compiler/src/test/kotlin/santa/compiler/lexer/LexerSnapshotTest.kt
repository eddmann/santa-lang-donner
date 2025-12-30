package santa.compiler.lexer

import org.junit.jupiter.api.Test
import santa.compiler.testutil.SnapshotAssertions

class LexerSnapshotTest {
    @Test
    fun lexes_all_token_types() {
        val source = """
            let mut count = -42;
            let pi = 3.14
            // greeting
            let name = "hi\n\"there\"";
            let predicate? = true;
            let empty = nil
            math = 1 + 2 - 3 * 4 / 5 % 2
            compare = 1 < 2 <= 3 > 2 >= 1 == 1 != 2
            logic = true && false || !false
            ranges = 1..2 1..=2 ..rest
            pipe = value |> f >> g
            infix = 1 `add` 2
            dict = #{"a": 1, b: 2, name}
            list = [1, 2, 3]
            block = { return count; break nil }
            tuple = (1)
            placeholder = _
        """.trimIndent()

        val tokens = Lexer(source).lexIncludingComments()
        val snapshot = TokenSnapshot.render(tokens)

        SnapshotAssertions.assertMatches("lexer_tokens", snapshot)
    }
}
