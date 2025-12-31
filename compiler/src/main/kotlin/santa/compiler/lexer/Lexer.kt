package santa.compiler.lexer

class Lexer(source: String) {
    private val input = source
        .replace("\r\n", "\n")
        .replace("\r", "\n")

    private var index = 0
    private var line = 1
    private var column = 1
    private var previousTokenType: TokenType? = null

    fun lex(): List<Token> = lexInternal(includeComments = false)

    fun lexIncludingComments(): List<Token> = lexInternal(includeComments = true)

    private fun lexInternal(includeComments: Boolean): List<Token> {
        val tokens = mutableListOf<Token>()

        while (!isAtEnd()) {
            val c = peek()
            when {
                c == ' ' || c == '\t' -> {
                    advance()
                }
                c == '\n' -> emit(tokens, newLineToken(), includeComments)
                c == '/' && peekNext() == '/' -> emit(tokens, commentToken(), includeComments)
                c == '"' -> emit(tokens, stringToken(), includeComments)
                c == '-' && peekNext()?.isDigit() == true && shouldTreatMinusAsSign() -> {
                    emit(tokens, numberToken(), includeComments)
                }
                c.isDigit() -> emit(tokens, numberToken(), includeComments)
                isIdentifierStart(c) -> emit(tokens, identifierOrKeywordToken(), includeComments)
                c == '_' -> emit(tokens, singleCharToken(TokenType.UNDERSCORE), includeComments)
                c == '#' -> emit(tokens, dictStartToken(), includeComments)
                c == '@' -> emit(tokens, singleCharToken(TokenType.AT), includeComments)
                c == '.' -> emit(tokens, rangeToken(), includeComments)
                else -> emit(tokens, operatorOrDelimiterToken(), includeComments)
            }
        }

        val eofPosition = position()
        val eofToken = Token(TokenType.EOF, "", Span(eofPosition, eofPosition))
        tokens.add(eofToken)
        previousTokenType = TokenType.EOF

        return tokens
    }

    private fun emit(tokens: MutableList<Token>, token: Token, includeComments: Boolean) {
        if (token.type == TokenType.COMMENT && !includeComments) return

        tokens.add(token)
        if (token.type != TokenType.COMMENT) {
            previousTokenType = token.type
        }
    }

    private fun numberToken(): Token {
        val start = position()
        val startIndex = index

        if (peek() == '-') {
            advance()
        }

        val integerPartStart = index
        readDigitsAndUnderscores()
        val integerPart = input.substring(integerPartStart, index)
        validateNumberPart(integerPart, start)

        val isDecimal = if (!isAtEnd() && peek() == '.' && peekNext()?.isDigit() == true) {
            advance()
            val fractionalPartStart = index
            readDigitsAndUnderscores()
            val fractionalPart = input.substring(fractionalPartStart, index)
            validateNumberPart(fractionalPart, start)
            true
        } else {
            false
        }

        val end = position()
        val lexeme = input.substring(startIndex, index)
        val type = if (isDecimal) TokenType.DECIMAL else TokenType.INTEGER

        return Token(type, lexeme, Span(start, end))
    }

    private fun identifierOrKeywordToken(): Token {
        val start = position()
        val startIndex = index

        advance()
        while (!isAtEnd() && isIdentifierPart(peek())) {
            advance()
        }

        val lexeme = input.substring(startIndex, index)
        val type = keywordTypeFor(lexeme) ?: TokenType.IDENTIFIER
        val end = position()

        return Token(type, lexeme, Span(start, end))
    }

    private fun stringToken(): Token {
        val start = position()
        val startIndex = index

        advance()
        var isClosed = false
        while (!isAtEnd()) {
            val c = advance()
            if (c == '"') {
                isClosed = true
                break
            }
            if (c == '\n') {
                throw LexingException("Unterminated string literal", start)
            }
            if (c == '\\') {
                if (isAtEnd()) {
                    throw LexingException("Unterminated string literal", start)
                }
                val escaped = advance()
                if (!isValidEscape(escaped)) {
                    throw LexingException("Invalid escape sequence: \\$escaped", start)
                }
            }
        }

        if (!isClosed) {
            throw LexingException("Unterminated string literal", start)
        }

        val end = position()
        val lexeme = input.substring(startIndex, index)
        return Token(TokenType.STRING, lexeme, Span(start, end))
    }

    private fun commentToken(): Token {
        val start = position()
        val startIndex = index

        advance()
        advance()
        while (!isAtEnd() && peek() != '\n') {
            advance()
        }

        val end = position()
        val lexeme = input.substring(startIndex, index)
        return Token(TokenType.COMMENT, lexeme, Span(start, end))
    }

    private fun newLineToken(): Token {
        val start = position()
        val startIndex = index
        advance()
        val end = position()
        val lexeme = input.substring(startIndex, index)

        return Token(TokenType.NEWLINE, lexeme, Span(start, end))
    }

    private fun dictStartToken(): Token {
        val start = position()
        val startIndex = index
        if (peekNext() != '{') {
            throw LexingException("Unexpected character: '#'", start)
        }
        advance()
        advance()
        val end = position()
        val lexeme = input.substring(startIndex, index)
        return Token(TokenType.DICT_START, lexeme, Span(start, end))
    }

    private fun rangeToken(): Token {
        val start = position()
        val startIndex = index

        if (peekNext() != '.') {
            throw LexingException("Unexpected character: '.'", start)
        }

        advance()
        advance()

        val type = if (!isAtEnd() && peek() == '=') {
            advance()
            TokenType.DOT_DOT_EQUAL
        } else {
            TokenType.DOT_DOT
        }

        val end = position()
        val lexeme = input.substring(startIndex, index)
        return Token(type, lexeme, Span(start, end))
    }

    private fun operatorOrDelimiterToken(): Token {
        val start = position()
        return when (val c = peek()) {
            '(' -> singleCharToken(TokenType.LPAREN)
            ')' -> singleCharToken(TokenType.RPAREN)
            '{' -> singleCharToken(TokenType.LBRACE)
            '}' -> singleCharToken(TokenType.RBRACE)
            '[' -> singleCharToken(TokenType.LBRACKET)
            ']' -> singleCharToken(TokenType.RBRACKET)
            ',' -> singleCharToken(TokenType.COMMA)
            ':' -> singleCharToken(TokenType.COLON)
            ';' -> singleCharToken(TokenType.SEMICOLON)
            '+' -> singleCharToken(TokenType.PLUS)
            '-' -> singleCharToken(TokenType.MINUS)
            '*' -> singleCharToken(TokenType.STAR)
            '/' -> singleCharToken(TokenType.SLASH)
            '%' -> singleCharToken(TokenType.PERCENT)
            '`' -> singleCharToken(TokenType.BACKTICK)
            '!' -> matchNext('=', TokenType.BANG_EQUAL, TokenType.BANG)
            '=' -> matchNext('=', TokenType.EQUAL_EQUAL, TokenType.ASSIGN)
            '<' -> matchNext('=', TokenType.LESS_EQUAL, TokenType.LESS)
            '>' -> when {
                peekNext() == '>' -> doubleCharToken(TokenType.SHIFT_RIGHT)
                peekNext() == '=' -> doubleCharToken(TokenType.GREATER_EQUAL)
                else -> singleCharToken(TokenType.GREATER)
            }
            '|' -> when {
                peekNext() == '|' -> doubleCharToken(TokenType.PIPE_PIPE)
                peekNext() == '>' -> doubleCharToken(TokenType.PIPE_GREATER)
                else -> singleCharToken(TokenType.PIPE)
            }
            '&' -> if (peekNext() == '&') {
                doubleCharToken(TokenType.AMP_AMP)
            } else {
                throw LexingException("Unexpected character: '&'", start)
            }
            else -> throw LexingException("Unexpected character: '$c'", start)
        }
    }

    private fun singleCharToken(type: TokenType): Token {
        val start = position()
        val startIndex = index
        advance()
        val end = position()
        val lexeme = input.substring(startIndex, index)
        return Token(type, lexeme, Span(start, end))
    }

    private fun doubleCharToken(type: TokenType): Token {
        val start = position()
        val startIndex = index
        advance()
        advance()
        val end = position()
        val lexeme = input.substring(startIndex, index)
        return Token(type, lexeme, Span(start, end))
    }

    private fun matchNext(expected: Char, matchType: TokenType, fallbackType: TokenType): Token {
        val start = position()
        val startIndex = index
        advance()
        val type = if (!isAtEnd() && peek() == expected) {
            advance()
            matchType
        } else {
            fallbackType
        }
        val end = position()
        val lexeme = input.substring(startIndex, index)
        return Token(type, lexeme, Span(start, end))
    }

    private fun readDigitsAndUnderscores() {
        if (isAtEnd()) {
            throw LexingException("Expected digit", position())
        }

        while (!isAtEnd() && (peek().isDigit() || peek() == '_')) {
            advance()
        }
    }

    private fun validateNumberPart(part: String, start: SourcePosition) {
        if (part.isEmpty()) {
            throw LexingException("Invalid number literal", start)
        }
        if (part.startsWith('_') || part.endsWith('_') || "__" in part || part.none { it.isDigit() }) {
            throw LexingException("Invalid number literal", start)
        }
    }

    private fun keywordTypeFor(lexeme: String): TokenType? = when (lexeme) {
        "let" -> TokenType.LET
        "mut" -> TokenType.MUT
        "if" -> TokenType.IF
        "else" -> TokenType.ELSE
        "match" -> TokenType.MATCH
        "return" -> TokenType.RETURN
        "break" -> TokenType.BREAK
        "true" -> TokenType.TRUE
        "false" -> TokenType.FALSE
        "nil" -> TokenType.NIL
        else -> null
    }

    private fun isValidEscape(c: Char): Boolean = when (c) {
        'n', 't', 'r', 'b', 'f', '"', '\\' -> true
        else -> false
    }

    private fun isIdentifierStart(c: Char): Boolean = c in 'a'..'z' || c in 'A'..'Z'

    private fun isIdentifierPart(c: Char): Boolean = isIdentifierStart(c) || c.isDigit() || c == '_' || c == '?'

    private fun shouldTreatMinusAsSign(): Boolean {
        val previous = previousTokenType ?: return true
        return previous !in expressionEndingTokens
    }

    private val expressionEndingTokens = setOf(
        TokenType.IDENTIFIER,
        TokenType.INTEGER,
        TokenType.DECIMAL,
        TokenType.STRING,
        TokenType.TRUE,
        TokenType.FALSE,
        TokenType.NIL,
        TokenType.UNDERSCORE,
        TokenType.RPAREN,
        TokenType.RBRACE,
        TokenType.RBRACKET,
    )

    private fun position(): SourcePosition = SourcePosition(line, column)

    private fun isAtEnd(): Boolean = index >= input.length

    private fun peek(): Char = input[index]

    private fun peekNext(): Char? = if (index + 1 < input.length) input[index + 1] else null

    private fun advance(): Char {
        val c = input[index]
        index += 1
        if (c == '\n') {
            line += 1
            column = 1
        } else {
            column += 1
        }
        return c
    }
}
