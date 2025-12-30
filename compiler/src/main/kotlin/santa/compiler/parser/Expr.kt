package santa.compiler.parser

import santa.compiler.lexer.Span

sealed interface Expr {
    val span: Span
}

sealed interface Statement {
    val span: Span
}

sealed interface TopLevel {
    val span: Span
}

data class Program(
    val items: List<TopLevel>,
)

data class Section(
    val name: String,
    val expr: Expr,
    override val span: Span,
) : TopLevel

data class StatementItem(
    val statement: Statement,
) : TopLevel {
    override val span: Span = statement.span
}

data class ExprStatement(
    val expr: Expr,
    override val span: Span,
) : Statement

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

data class LetExpr(
    val isMutable: Boolean,
    val pattern: Pattern,
    val value: Expr,
    override val span: Span,
) : Expr, Statement

data class ReturnExpr(
    val value: Expr,
    override val span: Span,
) : Expr, Statement

data class BreakExpr(
    val value: Expr,
    override val span: Span,
) : Expr, Statement

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
    val statements: List<Statement>,
    override val span: Span,
) : Expr

data class IfExpr(
    val condition: IfCondition,
    val thenBranch: BlockExpr,
    val elseBranch: Expr?,
    override val span: Span,
) : Expr

data class MatchExpr(
    val subject: Expr,
    val arms: List<MatchArm>,
    override val span: Span,
) : Expr

data class MatchArm(
    val pattern: Pattern,
    val guard: Expr?,
    val body: BlockExpr,
    val span: Span,
)

sealed interface IfCondition {
    val span: Span
}

data class ExprCondition(
    val expr: Expr,
    override val span: Span,
) : IfCondition

data class LetCondition(
    val pattern: Pattern,
    val value: Expr,
    override val span: Span,
) : IfCondition

sealed interface Pattern {
    val span: Span
}

data class WildcardPattern(
    override val span: Span,
) : Pattern

data class BindingPattern(
    val name: String,
    override val span: Span,
) : Pattern

data class RestPattern(
    val name: String?,
    override val span: Span,
) : Pattern

data class ListPattern(
    val elements: List<Pattern>,
    override val span: Span,
) : Pattern

data class LiteralPattern(
    val literal: Expr,
    override val span: Span,
) : Pattern

data class RangePattern(
    val start: String,
    val end: String?,
    val isInclusive: Boolean,
    override val span: Span,
) : Pattern

sealed interface CollectionElement {
    val expr: Expr
}

data class ExprElement(
    override val expr: Expr,
) : CollectionElement

data class SpreadElement(
    override val expr: Expr,
) : CollectionElement

sealed interface DictEntry

data class KeyValueEntry(
    val key: Expr,
    val value: Expr,
) : DictEntry

data class ShorthandEntry(
    val name: String,
) : DictEntry

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
