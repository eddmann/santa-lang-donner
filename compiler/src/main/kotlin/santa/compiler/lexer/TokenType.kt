package santa.compiler.lexer

enum class TokenType {
    // Identifiers and literals
    IDENTIFIER,
    INTEGER,
    DECIMAL,
    STRING,
    TRUE,
    FALSE,
    NIL,

    // Keywords
    LET,
    MUT,
    IF,
    ELSE,
    MATCH,
    RETURN,
    BREAK,

    // Operators
    PLUS,
    MINUS,
    STAR,
    SLASH,
    PERCENT,
    BANG,
    ASSIGN,
    EQUAL_EQUAL,
    BANG_EQUAL,
    LESS,
    LESS_EQUAL,
    GREATER,
    GREATER_EQUAL,
    PIPE_PIPE,
    AMP_AMP,
    DOT_DOT,
    DOT_DOT_EQUAL,
    PIPE,
    PIPE_GREATER,
    SHIFT_RIGHT,
    BACKTICK,

    // Delimiters
    LPAREN,
    RPAREN,
    LBRACE,
    RBRACE,
    LBRACKET,
    RBRACKET,
    COMMA,
    COLON,
    SEMICOLON,

    // Other
    UNDERSCORE,
    DICT_START,
    AT,
    NEWLINE,
    COMMENT,
    EOF,
}
