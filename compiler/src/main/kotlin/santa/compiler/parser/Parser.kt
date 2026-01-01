package santa.compiler.parser

import santa.compiler.lexer.Span
import santa.compiler.lexer.Token
import santa.compiler.lexer.TokenType

class Parser(private val tokens: List<Token>) {
    private var index = 0

    fun parseProgram(): Program {
        val items = mutableListOf<TopLevel>()

        skipLineBreaks()
        while (!check(TokenType.EOF)) {
            items.add(parseTopLevel())
            skipLineBreaks()
            if (match(TokenType.SEMICOLON)) {
                skipLineBreaks()
            }
        }

        return Program(items)
    }

    fun parseExpression(): Expr {
        skipLineBreaks()
        val expr = parseExpression(0)
        skipLineBreaks()
        if (match(TokenType.SEMICOLON)) {
            skipLineBreaks()
        }
        val end = peek()
        if (end.type != TokenType.EOF) {
            throw error(end, "Expected end of expression")
        }
        return expr
    }

    private fun parseTopLevel(): TopLevel {
        skipLineBreaks()

        // Check for @slow attribute
        val isSlow = if (check(TokenType.AT)) {
            advance()
            val attrName = expect(TokenType.IDENTIFIER, "Expected attribute name after '@'")
            if (attrName.lexeme != "slow") {
                throw error(attrName, "Unknown attribute: @${attrName.lexeme}")
            }
            skipLineBreaks()
            true
        } else {
            false
        }

        return if (isSectionStart()) {
            parseSection(isSlow)
        } else {
            if (isSlow) {
                throw error(peek(), "@slow attribute can only be applied to test sections")
            }
            StatementItem(parseStatement())
        }
    }

    private fun parseSection(isSlow: Boolean = false): Section {
        val nameToken = expect(TokenType.IDENTIFIER, "Expected section name")
        expect(TokenType.COLON, "Expected ':' after section name")

        // If this is a test section with a block, parse it specially
        val expr = if (nameToken.lexeme == "test" && check(TokenType.LBRACE)) {
            parseTestBlockExpression()
        } else {
            if (isSlow) {
                throw error(nameToken, "@slow attribute can only be applied to test sections")
            }
            parseExpression(0)
        }

        return Section(nameToken.lexeme, expr, isSlow, spanFrom(nameToken.span, expr.span))
    }

    /**
     * Parses a test block containing test entries.
     *
     * ```
     * {
     *   input: "test data"
     *   part_one: expected_value
     *   part_two: expected_value
     * }
     * ```
     */
    private fun parseTestBlockExpression(): TestBlockExpr {
        val startToken = expect(TokenType.LBRACE, "Expected '{' to start test block")
        val entries = mutableListOf<TestEntry>()

        skipLineBreaks()
        while (!check(TokenType.RBRACE)) {
            if (check(TokenType.EOF)) {
                throw error(peek(), "Expected '}' to close test block")
            }
            entries.add(parseTestEntry())
            skipLineBreaks()
            if (match(TokenType.SEMICOLON)) {
                skipLineBreaks()
            }
        }

        val endToken = expect(TokenType.RBRACE, "Expected '}' to close test block")
        return TestBlockExpr(entries, spanFrom(startToken.span, endToken.span))
    }

    /**
     * Parses a single test entry: `name: expr`
     */
    private fun parseTestEntry(): TestEntry {
        skipLineBreaks()
        val nameToken = expect(TokenType.IDENTIFIER, "Expected test entry name")
        expect(TokenType.COLON, "Expected ':' after test entry name")
        val expr = parseExpression(0)
        return TestEntry(nameToken.lexeme, expr, spanFrom(nameToken.span, expr.span))
    }

    private fun parseStatement(): Statement {
        skipLineBreaks()
        return when (peek().type) {
            TokenType.LET -> parseLetBinding()
            TokenType.RETURN -> parseReturnExpr()
            TokenType.BREAK -> parseBreakExpr()
            else -> {
                val expr = parseExpression(0)
                ExprStatement(expr, expr.span)
            }
        }
    }

    private fun parseExpression(minBp: Int): Expr {
        skipLineBreaks()
        var left = parsePrefix()

        while (true) {
            skipLineBreaks()
            val token = peek()
            val binding = infixBindingPower(token) ?: break
            val (leftBp, rightBp) = binding
            if (leftBp < minBp) break

            advance()
            left = parseInfix(left, token, rightBp)
        }

        return left
    }

    private fun parseLetBinding(): LetExpr {
        val letToken = expect(TokenType.LET, "Expected 'let'")
        return parseLetBindingAfterLet(letToken)
    }

    private fun parseLetBindingAfterLet(letToken: Token): LetExpr {
        val isMutable = match(TokenType.MUT)
        val pattern = parsePattern()
        expect(TokenType.ASSIGN, "Expected '=' after let pattern")
        val value = parseExpression(0)
        return LetExpr(isMutable, pattern, value, spanFrom(letToken.span, value.span))
    }

    private fun parseReturnExpr(): ReturnExpr {
        val returnToken = expect(TokenType.RETURN, "Expected 'return'")
        return parseReturnAfterToken(returnToken)
    }

    private fun parseBreakExpr(): BreakExpr {
        val breakToken = expect(TokenType.BREAK, "Expected 'break'")
        return parseBreakAfterToken(breakToken)
    }

    private fun parseReturnAfterToken(returnToken: Token): ReturnExpr {
        val value = parseExpression(0)
        return ReturnExpr(value, spanFrom(returnToken.span, value.span))
    }

    private fun parseBreakAfterToken(breakToken: Token): BreakExpr {
        val value = parseExpression(0)
        return BreakExpr(value, spanFrom(breakToken.span, value.span))
    }

    private fun parsePrefix(): Expr {
        skipLineBreaks()
        val token = peek()
        return when (token.type) {
            TokenType.MINUS -> parseUnary(UnaryOperator.NEGATE)
            TokenType.BANG -> parseUnary(UnaryOperator.NOT)
            else -> parsePostfix(parsePrimary())
        }
    }

    private fun parseUnary(operator: UnaryOperator): Expr {
        val operatorToken = advance()
        val expr = parseExpression(PREFIX_BP)
        return UnaryExpr(operator, expr, spanFrom(operatorToken.span, expr.span))
    }

    private fun parsePrimary(): Expr {
        val token = advance()
        return when (token.type) {
            TokenType.INTEGER -> IntLiteralExpr(token.lexeme, token.span)
            TokenType.DECIMAL -> DecimalLiteralExpr(token.lexeme, token.span)
            TokenType.STRING -> StringLiteralExpr(token.lexeme, token.span)
            TokenType.TRUE -> BoolLiteralExpr(true, token.span)
            TokenType.FALSE -> BoolLiteralExpr(false, token.span)
            TokenType.NIL -> NilLiteralExpr(token.span)
            TokenType.IDENTIFIER -> IdentifierExpr(token.lexeme, token.span)
            TokenType.UNDERSCORE -> PlaceholderExpr(token.span)
            TokenType.LPAREN -> parseGroupedExpression(token)
            TokenType.LBRACKET -> parseListLiteral(token)
            TokenType.LBRACE -> parseBracedExpression(token)
            TokenType.DICT_START -> parseDictLiteral(token)
            TokenType.PIPE -> parseFunctionLiteral(token)
            TokenType.PIPE_PIPE -> parseEmptyParamsFunctionLiteral(token)
            TokenType.LET -> parseLetBindingAfterLet(token)
            TokenType.RETURN -> parseReturnAfterToken(token)
            TokenType.BREAK -> parseBreakAfterToken(token)
            TokenType.IF -> parseIfExpression(token)
            TokenType.MATCH -> parseMatchExpression(token)
            // Operators as function values (e.g., sort(<), reduce(+))
            TokenType.PLUS, TokenType.MINUS, TokenType.STAR, TokenType.SLASH, TokenType.PERCENT,
            TokenType.LESS, TokenType.GREATER, TokenType.LESS_EQUAL, TokenType.GREATER_EQUAL,
            TokenType.EQUAL_EQUAL, TokenType.BANG_EQUAL -> OperatorExpr(token.lexeme, token.span)
            else -> throw error(token, "Expected expression")
        }
    }

    private fun parseGroupedExpression(startToken: Token): Expr {
        val expr = parseExpression(0)
        skipLineBreaks()
        val endToken = expect(TokenType.RPAREN, "Expected ')' after expression")
        return expr.copyWithSpan(spanFrom(startToken.span, endToken.span))
    }

    private fun parseListLiteral(startToken: Token): Expr {
        val elements = parseDelimitedElements(TokenType.RBRACKET) { parseCollectionElement() }
        val endToken = expect(TokenType.RBRACKET, "Expected ']' after list literal")
        return ListLiteralExpr(elements, spanFrom(startToken.span, endToken.span))
    }

    private fun parseBracedExpression(startToken: Token): Expr {
        if (check(TokenType.RBRACE)) {
            return parseSetLiteral(startToken)
        }
        if (isBlockExpressionStart()) {
            return parseBlockExpression(startToken)
        }
        return parseSetLiteral(startToken)
    }

    private fun parseSetLiteral(startToken: Token): Expr {
        val elements = parseDelimitedElements(TokenType.RBRACE) { parseCollectionElement() }
        val endToken = expect(TokenType.RBRACE, "Expected '}' after set literal")
        return SetLiteralExpr(elements, spanFrom(startToken.span, endToken.span))
    }

    private fun parseDictLiteral(startToken: Token): Expr {
        val entries = parseDelimitedElements(TokenType.RBRACE) { parseDictEntry() }
        val endToken = expect(TokenType.RBRACE, "Expected '}' after dictionary literal")
        return DictLiteralExpr(entries, spanFrom(startToken.span, endToken.span))
    }

    private fun parseCollectionElement(): CollectionElement {
        return if (match(TokenType.DOT_DOT)) {
            val expr = parseExpression(0)
            SpreadElement(expr)
        } else {
            val expr = parseExpression(0)
            ExprElement(expr)
        }
    }

    private fun parseDictEntry(): DictEntry {
        val key = parseExpression(0)
        skipLineBreaks()
        if (match(TokenType.COLON)) {
            val value = parseExpression(0)
            return KeyValueEntry(key, value)
        }
        if (key is IdentifierExpr) {
            return ShorthandEntry(key.name)
        }
        throw error(peek(), "Expected ':' after dictionary key")
    }

    private fun parseFunctionLiteral(startToken: Token): Expr {
        val params = parseParams()
        val body = if (check(TokenType.LBRACE)) {
            parseBlockExpression()
        } else {
            parseExpression(0)
        }
        return FunctionExpr(params, body, spanFrom(startToken.span, body.span))
    }

    private fun parseEmptyParamsFunctionLiteral(startToken: Token): Expr {
        // || already consumed - empty params
        val body = if (check(TokenType.LBRACE)) {
            parseBlockExpression()
        } else {
            parseExpression(0)
        }
        return FunctionExpr(emptyList(), body, spanFrom(startToken.span, body.span))
    }

    private fun parseParams(): List<Param> {
        val params = mutableListOf<Param>()
        if (match(TokenType.PIPE)) {
            return params
        }

        var hasRest = false
        while (true) {
            if (match(TokenType.DOT_DOT)) {
                if (hasRest) {
                    throw error(peek(), "Rest parameter must be last")
                }
                val nameToken = expect(TokenType.IDENTIFIER, "Expected identifier after '..'")
                params.add(RestParam(nameToken.lexeme))
                hasRest = true
            } else if (match(TokenType.UNDERSCORE)) {
                params.add(PlaceholderParam)
            } else if (check(TokenType.LBRACKET)) {
                // Parse destructuring pattern parameter: |[a, b]| ...
                val pattern = parsePattern()
                params.add(PatternParam(pattern))
            } else {
                val nameToken = expect(TokenType.IDENTIFIER, "Expected parameter name")
                params.add(NamedParam(nameToken.lexeme))
            }

            if (!match(TokenType.COMMA)) break
            if (hasRest) {
                throw error(peek(), "Rest parameter must be last")
            }
            if (check(TokenType.PIPE)) break
        }

        expect(TokenType.PIPE, "Expected '|' after parameters")
        return params
    }

    private fun parseBlockExpression(): BlockExpr {
        val startToken = expect(TokenType.LBRACE, "Expected '{' to start block")
        return parseBlockExpression(startToken)
    }

    private fun parseBlockExpression(startToken: Token): BlockExpr {
        val statements = mutableListOf<Statement>()

        skipLineBreaks()
        while (!check(TokenType.RBRACE)) {
            if (check(TokenType.EOF)) {
                throw error(peek(), "Expected '}' to close block")
            }
            statements.add(parseStatement())
            skipLineBreaks()
            if (match(TokenType.SEMICOLON)) {
                skipLineBreaks()
            }
        }

        val endToken = expect(TokenType.RBRACE, "Expected '}' to close block")
        return BlockExpr(statements, spanFrom(startToken.span, endToken.span))
    }

    private fun parseIfExpression(startToken: Token): Expr {
        val condition = parseIfCondition()
        val thenBranch = parseBlockExpression()
        val elseBranch = if (match(TokenType.ELSE)) {
            if (match(TokenType.IF)) {
                parseIfExpression(previous())
            } else {
                parseBlockExpression()
            }
        } else {
            null
        }
        val endSpan = elseBranch?.span ?: thenBranch.span
        return IfExpr(condition, thenBranch, elseBranch, spanFrom(startToken.span, endSpan))
    }

    private fun parseIfCondition(): IfCondition {
        if (match(TokenType.LET)) {
            val pattern = parsePattern()
            expect(TokenType.ASSIGN, "Expected '=' after if-let pattern")
            val value = parseExpression(0)
            return LetCondition(pattern, value, spanFrom(pattern.span, value.span))
        }
        val expr = parseExpression(0)
        return ExprCondition(expr, expr.span)
    }

    private fun parseMatchExpression(startToken: Token): Expr {
        val subject = parseExpression(0)
        skipLineBreaks()
        expect(TokenType.LBRACE, "Expected '{' after match subject")
        val arms = mutableListOf<MatchArm>()

        skipLineBreaks()
        while (!check(TokenType.RBRACE)) {
            if (check(TokenType.EOF)) {
                throw error(peek(), "Expected '}' after match arms")
            }
            val pattern = parsePattern()
            skipLineBreaks()
            val guard = if (match(TokenType.IF)) {
                parseExpression(0)
            } else {
                null
            }
            val body = parseBlockExpression()
            arms.add(MatchArm(pattern, guard, body, spanFrom(pattern.span, body.span)))
            skipLineBreaks()
        }

        val endToken = expect(TokenType.RBRACE, "Expected '}' after match arms")
        return MatchExpr(subject, arms, spanFrom(startToken.span, endToken.span))
    }

    private fun parsePattern(): Pattern {
        skipLineBreaks()
        val token = advance()
        return when (token.type) {
            TokenType.UNDERSCORE -> WildcardPattern(token.span)
            TokenType.IDENTIFIER -> BindingPattern(token.lexeme, token.span)
            TokenType.DOT_DOT -> parseRestPattern(token)
            TokenType.LBRACKET -> parseListPattern(token)
            TokenType.INTEGER -> parseRangeOrLiteralPattern(token)
            TokenType.DECIMAL -> LiteralPattern(DecimalLiteralExpr(token.lexeme, token.span), token.span)
            TokenType.STRING -> LiteralPattern(StringLiteralExpr(token.lexeme, token.span), token.span)
            TokenType.TRUE -> LiteralPattern(BoolLiteralExpr(true, token.span), token.span)
            TokenType.FALSE -> LiteralPattern(BoolLiteralExpr(false, token.span), token.span)
            TokenType.NIL -> LiteralPattern(NilLiteralExpr(token.span), token.span)
            else -> throw error(token, "Expected pattern")
        }
    }

    private fun parseRestPattern(startToken: Token): Pattern {
        val nameToken = if (check(TokenType.IDENTIFIER)) {
            advance()
        } else {
            null
        }
        val endSpan = nameToken?.span ?: startToken.span
        return RestPattern(nameToken?.lexeme, spanFrom(startToken.span, endSpan))
    }

    private fun parseListPattern(startToken: Token): Pattern {
        val elements = parseDelimitedElements(TokenType.RBRACKET) { parsePattern() }
        val endToken = expect(TokenType.RBRACKET, "Expected ']' after list pattern")
        return ListPattern(elements, spanFrom(startToken.span, endToken.span))
    }

    private fun parseRangeOrLiteralPattern(startToken: Token): Pattern {
        if (check(TokenType.DOT_DOT) || check(TokenType.DOT_DOT_EQUAL)) {
            val rangeToken = advance()
            val isInclusive = rangeToken.type == TokenType.DOT_DOT_EQUAL
            val endToken = if (check(TokenType.INTEGER)) {
                advance()
            } else {
                null
            }
            if (isInclusive && endToken == null) {
                throw error(peek(), "Expected integer after '..=' in range pattern")
            }
            val endSpan = endToken?.span ?: rangeToken.span
            return RangePattern(startToken.lexeme, endToken?.lexeme, isInclusive, spanFrom(startToken.span, endSpan))
        }
        return LiteralPattern(IntLiteralExpr(startToken.lexeme, startToken.span), startToken.span)
    }

    private fun parsePostfix(expr: Expr): Expr {
        var current = expr
        current = parseIndexing(current)
        current = parseCalls(current)
        return current
    }

    private fun parseIndexing(expr: Expr): Expr {
        var current = expr
        while (match(TokenType.LBRACKET)) {
            val indexExpr = parseExpression(0)
            skipLineBreaks()
            val endToken = expect(TokenType.RBRACKET, "Expected ']' after index expression")
            current = IndexExpr(current, indexExpr, spanFrom(current.span, endToken.span))
        }
        return current
    }

    private fun parseCalls(expr: Expr): Expr {
        var current = expr
        while (true) {
            skipLineBreaks()
            if (match(TokenType.LPAREN)) {
                val args = parseDelimitedElements(TokenType.RPAREN) { parseCallArgument() }
                val endToken = expect(TokenType.RPAREN, "Expected ')' after arguments")
                current = CallExpr(current, args, spanFrom(current.span, endToken.span))
                continue
            }

            if (check(TokenType.PIPE)) {
                val lambda = parseFunctionLiteral(advance())
                current = when (current) {
                    is CallExpr -> CallExpr(
                        current.callee,
                        current.arguments + ExprArgument(lambda),
                        spanFrom(current.span, lambda.span),
                    )
                    else -> CallExpr(current, listOf(ExprArgument(lambda)), spanFrom(current.span, lambda.span))
                }
                continue
            }

            break
        }

        return current
    }

    private fun parseCallArgument(): CallArgument {
        return if (match(TokenType.DOT_DOT)) {
            val expr = parseExpression(0)
            SpreadArgument(expr)
        } else {
            val expr = parseExpression(0)
            ExprArgument(expr)
        }
    }

    private fun parseInfix(left: Expr, operatorToken: Token, rightBp: Int): Expr {
        return when (operatorToken.type) {
            TokenType.ASSIGN -> {
                if (left !is IdentifierExpr) {
                    throw error(operatorToken, "Invalid assignment target")
                }
                val value = parseExpression(rightBp)
                AssignmentExpr(left, value, spanFrom(left.span, value.span))
            }
            TokenType.DOT_DOT, TokenType.DOT_DOT_EQUAL -> {
                val isInclusive = operatorToken.type == TokenType.DOT_DOT_EQUAL
                val end = if (isInclusive || !isRangeTerminator(peek())) {
                    parseExpression(rightBp)
                } else {
                    null
                }
                val endSpan = end?.span ?: operatorToken.span
                RangeExpr(left, end, isInclusive, spanFrom(left.span, endSpan))
            }
            TokenType.BACKTICK -> {
                val nameToken = expect(TokenType.IDENTIFIER, "Expected function name after backtick")
                expect(TokenType.BACKTICK, "Expected closing backtick")
                val right = parseExpression(rightBp)
                InfixCallExpr(left, nameToken.lexeme, right, spanFrom(left.span, right.span))
            }
            else -> {
                val operator = binaryOperatorFor(operatorToken)
                    ?: throw error(operatorToken, "Unsupported operator")
                val right = parseExpression(rightBp)
                BinaryExpr(left, operator, right, spanFrom(left.span, right.span))
            }
        }
    }

    private fun binaryOperatorFor(token: Token): BinaryOperator? = when (token.type) {
        TokenType.PLUS -> BinaryOperator.PLUS
        TokenType.MINUS -> BinaryOperator.MINUS
        TokenType.STAR -> BinaryOperator.MULTIPLY
        TokenType.SLASH -> BinaryOperator.DIVIDE
        TokenType.PERCENT -> BinaryOperator.MODULO
        TokenType.EQUAL_EQUAL -> BinaryOperator.EQUAL
        TokenType.BANG_EQUAL -> BinaryOperator.NOT_EQUAL
        TokenType.LESS -> BinaryOperator.LESS
        TokenType.LESS_EQUAL -> BinaryOperator.LESS_EQUAL
        TokenType.GREATER -> BinaryOperator.GREATER
        TokenType.GREATER_EQUAL -> BinaryOperator.GREATER_EQUAL
        TokenType.AMP_AMP -> BinaryOperator.AND
        TokenType.PIPE_PIPE -> BinaryOperator.OR
        TokenType.PIPE_GREATER -> BinaryOperator.PIPELINE
        TokenType.SHIFT_RIGHT -> BinaryOperator.COMPOSE
        else -> null
    }

    private fun infixBindingPower(token: Token): Pair<Int, Int>? = when (token.type) {
        TokenType.ASSIGN -> rightAssoc(ASSIGN_BP)
        TokenType.PIPE_PIPE -> leftAssoc(OR_BP)
        TokenType.AMP_AMP -> leftAssoc(AND_BP)
        TokenType.EQUAL_EQUAL, TokenType.BANG_EQUAL -> leftAssoc(EQUALITY_BP)
        TokenType.LESS, TokenType.LESS_EQUAL, TokenType.GREATER, TokenType.GREATER_EQUAL -> leftAssoc(COMPARISON_BP)
        TokenType.SHIFT_RIGHT, TokenType.PIPE_GREATER, TokenType.DOT_DOT, TokenType.DOT_DOT_EQUAL -> leftAssoc(COMPOSITION_BP)
        TokenType.PLUS, TokenType.MINUS -> leftAssoc(SUM_BP)
        TokenType.STAR, TokenType.SLASH, TokenType.PERCENT, TokenType.BACKTICK -> leftAssoc(PRODUCT_BP)
        else -> null
    }

    private fun leftAssoc(bp: Int): Pair<Int, Int> = bp to bp + 1

    private fun rightAssoc(bp: Int): Pair<Int, Int> = bp to bp

    private fun isRangeTerminator(token: Token): Boolean = when (token.type) {
        TokenType.RPAREN,
        TokenType.RBRACKET,
        TokenType.RBRACE,
        TokenType.COMMA,
        TokenType.SEMICOLON,
        TokenType.NEWLINE,
        TokenType.EOF,
        TokenType.PIPE_GREATER,  // For unbounded ranges in pipelines: 1.. |> take(5)
        TokenType.SHIFT_RIGHT,   // For composition: 1.. >> take(5)
        -> true
        else -> false
    }

    private fun <T> parseDelimitedElements(
        terminator: TokenType,
        parseElement: () -> T,
    ): List<T> {
        val elements = mutableListOf<T>()
        skipLineBreaks()
        if (check(terminator)) return elements

        while (true) {
            elements.add(parseElement())
            skipLineBreaks()
            if (!match(TokenType.COMMA)) break
            skipLineBreaks()
            if (check(terminator)) break
        }

        return elements
    }

    private fun isSectionStart(): Boolean {
        return check(TokenType.IDENTIFIER) && peekNext().type == TokenType.COLON
    }

    private fun isBlockExpressionStart(): Boolean {
        var depth = 0
        var cursor = index
        while (cursor < tokens.size) {
            val token = tokens[cursor]
            if (depth == 0) {
                when (token.type) {
                    TokenType.RBRACE -> return false
                    TokenType.SEMICOLON, TokenType.NEWLINE -> return true
                    TokenType.COMMA -> return false
                    TokenType.LET, TokenType.RETURN, TokenType.BREAK -> return true
                    else -> {}
                }
            }
            when (token.type) {
                TokenType.LPAREN, TokenType.LBRACKET, TokenType.LBRACE -> depth += 1
                TokenType.RPAREN, TokenType.RBRACKET, TokenType.RBRACE -> {
                    if (depth > 0) {
                        depth -= 1
                    } else {
                        return false
                    }
                }
                else -> {}
            }
            cursor += 1
        }
        return false
    }

    private fun skipLineBreaks() {
        while (match(TokenType.NEWLINE)) {
            // Skip line breaks between tokens.
        }
    }

    private fun match(type: TokenType): Boolean {
        if (!check(type)) return false
        advance()
        return true
    }

    private fun check(type: TokenType): Boolean = peek().type == type

    private fun expect(type: TokenType, message: String): Token {
        val token = peek()
        if (token.type != type) {
            throw error(token, message)
        }
        return advance()
    }

    private fun advance(): Token {
        if (!isAtEnd()) index += 1
        return previous()
    }

    private fun isAtEnd(): Boolean = peek().type == TokenType.EOF

    private fun peek(): Token = tokens[index]

    private fun peekNext(): Token = tokens.getOrElse(index + 1) { tokens.last() }

    private fun previous(): Token = tokens[index - 1]

    private fun error(token: Token, message: String): ParseException = ParseException(message, token.span.start)

    private fun spanFrom(start: Span, end: Span): Span = Span(start.start, end.end)

    private fun Expr.copyWithSpan(span: Span): Expr = when (this) {
        is IntLiteralExpr -> copy(span = span)
        is DecimalLiteralExpr -> copy(span = span)
        is StringLiteralExpr -> copy(span = span)
        is BoolLiteralExpr -> copy(span = span)
        is NilLiteralExpr -> copy(span = span)
        is IdentifierExpr -> copy(span = span)
        is PlaceholderExpr -> copy(span = span)
        is OperatorExpr -> copy(span = span)
        is ListLiteralExpr -> copy(span = span)
        is SetLiteralExpr -> copy(span = span)
        is DictLiteralExpr -> copy(span = span)
        is UnaryExpr -> copy(span = span)
        is BinaryExpr -> copy(span = span)
        is AssignmentExpr -> copy(span = span)
        is LetExpr -> copy(span = span)
        is ReturnExpr -> copy(span = span)
        is BreakExpr -> copy(span = span)
        is RangeExpr -> copy(span = span)
        is InfixCallExpr -> copy(span = span)
        is CallExpr -> copy(span = span)
        is IndexExpr -> copy(span = span)
        is FunctionExpr -> copy(span = span)
        is BlockExpr -> copy(span = span)
        is IfExpr -> copy(span = span)
        is MatchExpr -> copy(span = span)
        is TestBlockExpr -> copy(span = span)
    }

    private companion object {
        private const val ASSIGN_BP = 1
        private const val OR_BP = 2
        private const val AND_BP = 3
        private const val EQUALITY_BP = 4
        private const val COMPARISON_BP = 5
        private const val COMPOSITION_BP = 6
        private const val SUM_BP = 7
        private const val PRODUCT_BP = 8
        private const val PREFIX_BP = 9
    }
}
