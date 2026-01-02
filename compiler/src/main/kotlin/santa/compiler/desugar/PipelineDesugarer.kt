package santa.compiler.desugar

import santa.compiler.parser.*

/**
 * Desugars pipeline expressions by recursively transforming nested expressions.
 *
 * Pipelines are NOT desugared to function calls. Instead, all pipelines are kept
 * as binary expressions and handled at runtime by Operators.pipeline.
 *
 * This allows pipelines to work correctly with:
 * - Builtins like `x |> map(f)` where `map(f)` returns a PartiallyAppliedBuiltinValue
 * - User functions like `x |> make_fn(y)` where `make_fn(y)` returns a function
 *
 * At runtime, Operators.pipeline invokes the right-hand side as a function with
 * the left-hand side as the argument.
 */
object PipelineDesugarer {

    /**
     * Desugar all pipeline expressions in a program.
     */
    fun desugar(program: Program): Program {
        return program.copy(
            items = program.items.map { desugarTopLevel(it) }
        )
    }

    private fun desugarTopLevel(topLevel: TopLevel): TopLevel = when (topLevel) {
        is Section -> topLevel.copy(expr = desugarExpr(topLevel.expr))
        is StatementItem -> StatementItem(desugarStatement(topLevel.statement))
    }

    private fun desugarStatement(stmt: Statement): Statement = when (stmt) {
        is ExprStatement -> ExprStatement(desugarExpr(stmt.expr), stmt.span)
        is LetExpr -> stmt.copy(value = desugarExpr(stmt.value))
        is ReturnExpr -> stmt.copy(value = desugarExpr(stmt.value))
        is BreakExpr -> stmt.copy(value = desugarExpr(stmt.value))
    }

    private fun desugarExpr(expr: Expr): Expr = when (expr) {
        is BinaryExpr -> {
            if (expr.operator == BinaryOperator.PIPELINE) {
                desugarPipeline(expr)
            } else {
                expr.copy(
                    left = desugarExpr(expr.left),
                    right = desugarExpr(expr.right)
                )
            }
        }
        is UnaryExpr -> expr.copy(expr = desugarExpr(expr.expr))
        is CallExpr -> expr.copy(
            callee = desugarExpr(expr.callee),
            arguments = expr.arguments.map { desugarArg(it) }
        )
        is IndexExpr -> expr.copy(
            target = desugarExpr(expr.target),
            index = desugarExpr(expr.index)
        )
        is FunctionExpr -> expr.copy(body = desugarExpr(expr.body))
        is BlockExpr -> expr.copy(statements = expr.statements.map { desugarStatement(it) })
        is IfExpr -> expr.copy(
            condition = when (val cond = expr.condition) {
                is ExprCondition -> ExprCondition(desugarExpr(cond.expr), cond.span)
                is LetCondition -> LetCondition(cond.pattern, desugarExpr(cond.value), cond.span)
            },
            thenBranch = desugarExpr(expr.thenBranch) as BlockExpr,
            elseBranch = expr.elseBranch?.let { desugarExpr(it) }
        )
        is MatchExpr -> expr.copy(
            subject = desugarExpr(expr.subject),
            arms = expr.arms.map { arm ->
                arm.copy(
                    guard = arm.guard?.let { desugarExpr(it) },
                    body = desugarExpr(arm.body) as BlockExpr
                )
            }
        )
        is LetExpr -> expr.copy(value = desugarExpr(expr.value))
        is ReturnExpr -> expr.copy(value = desugarExpr(expr.value))
        is BreakExpr -> expr.copy(value = desugarExpr(expr.value))
        is AssignmentExpr -> expr.copy(value = desugarExpr(expr.value))
        is ListLiteralExpr -> expr.copy(elements = expr.elements.map { desugarElement(it) })
        is SetLiteralExpr -> expr.copy(elements = expr.elements.map { desugarElement(it) })
        is DictLiteralExpr -> expr.copy(entries = expr.entries.map { desugarDictEntry(it) })
        is RangeExpr -> expr.copy(
            start = desugarExpr(expr.start),
            end = expr.end?.let { desugarExpr(it) }
        )
        is InfixCallExpr -> expr.copy(
            left = desugarExpr(expr.left),
            right = desugarExpr(expr.right)
        )
        is TestBlockExpr -> expr.copy(
            entries = expr.entries.map { it.copy(expr = desugarExpr(it.expr)) }
        )
        // Terminals - no transformation needed
        is IntLiteralExpr, is DecimalLiteralExpr, is StringLiteralExpr,
        is BoolLiteralExpr, is NilLiteralExpr, is IdentifierExpr,
        is PlaceholderExpr, is OperatorExpr -> expr
    }

    private fun desugarArg(arg: CallArgument): CallArgument = when (arg) {
        is ExprArgument -> ExprArgument(desugarExpr(arg.expr))
        is SpreadArgument -> SpreadArgument(desugarExpr(arg.expr))
    }

    private fun desugarElement(elem: CollectionElement): CollectionElement = when (elem) {
        is ExprElement -> ExprElement(desugarExpr(elem.expr))
        is SpreadElement -> SpreadElement(desugarExpr(elem.expr))
    }

    private fun desugarDictEntry(entry: DictEntry): DictEntry = when (entry) {
        is KeyValueEntry -> entry.copy(
            key = desugarExpr(entry.key),
            value = desugarExpr(entry.value)
        )
        is ShorthandEntry -> entry
    }

    /**
     * Transform a pipeline expression by recursively desugaring its operands.
     * The pipeline itself is kept as a binary expression for runtime handling.
     */
    private fun desugarPipeline(expr: BinaryExpr): Expr {
        val left = desugarExpr(expr.left)
        val right = desugarExpr(expr.right)
        return expr.copy(left = left, right = right)
    }
}
