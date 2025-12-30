package santa.compiler.parser

import santa.compiler.lexer.Span

sealed interface Expr {
    val span: Span
}

data class IntLiteralExpr(
    val lexeme: String,
    override val span: Span,
) : Expr

data class DecimalLiteralExpr(
    val lexeme: String,
    override val span: Span,
) : Expr

data class StringLiteralExpr(
    val lexeme: String,
    override val span: Span,
) : Expr

data class BoolLiteralExpr(
    val value: Boolean,
    override val span: Span,
) : Expr

data class NilLiteralExpr(
    override val span: Span,
) : Expr

data class IdentifierExpr(
    val name: String,
    override val span: Span,
) : Expr

data class PlaceholderExpr(
    override val span: Span,
) : Expr

data class ListLiteralExpr(
    val elements: List<CollectionElement>,
    override val span: Span,
) : Expr

data class SetLiteralExpr(
    val elements: List<CollectionElement>,
    override val span: Span,
) : Expr

data class DictLiteralExpr(
    val entries: List<DictEntry>,
    override val span: Span,
) : Expr

data class UnaryExpr(
    val operator: UnaryOperator,
    val expr: Expr,
    override val span: Span,
) : Expr

data class BinaryExpr(
    val left: Expr,
    val operator: BinaryOperator,
    val right: Expr,
    override val span: Span,
) : Expr

data class AssignmentExpr(
    val target: IdentifierExpr,
    val value: Expr,
    override val span: Span,
) : Expr

data class RangeExpr(
    val start: Expr,
    val end: Expr?,
    val isInclusive: Boolean,
    override val span: Span,
) : Expr

data class InfixCallExpr(
    val left: Expr,
    val functionName: String,
    val right: Expr,
    override val span: Span,
) : Expr

data class CallExpr(
    val callee: Expr,
    val arguments: List<CallArgument>,
    override val span: Span,
) : Expr

data class IndexExpr(
    val target: Expr,
    val index: Expr,
    override val span: Span,
) : Expr

data class FunctionExpr(
    val params: List<Param>,
    val body: Expr,
    override val span: Span,
) : Expr

data class BlockExpr(
    val expressions: List<Expr>,
    override val span: Span,
) : Expr

sealed interface CollectionElement {
    val expr: Expr
}

data class ExprElement(
    override val expr: Expr,
) : CollectionElement

data class SpreadElement(
    override val expr: Expr,
) : CollectionElement

data class DictEntry(
    val key: Expr,
    val value: Expr,
)

sealed interface CallArgument {
    val expr: Expr
}

data class ExprArgument(
    override val expr: Expr,
) : CallArgument

data class SpreadArgument(
    override val expr: Expr,
) : CallArgument

sealed interface Param


data class NamedParam(
    val name: String,
) : Param

object PlaceholderParam : Param


data class RestParam(
    val name: String,
) : Param

enum class UnaryOperator {
    NEGATE,
    NOT,
}

enum class BinaryOperator {
    PLUS,
    MINUS,
    MULTIPLY,
    DIVIDE,
    MODULO,
    EQUAL,
    NOT_EQUAL,
    LESS,
    LESS_EQUAL,
    GREATER,
    GREATER_EQUAL,
    AND,
    OR,
    PIPELINE,
    COMPOSE,
}
