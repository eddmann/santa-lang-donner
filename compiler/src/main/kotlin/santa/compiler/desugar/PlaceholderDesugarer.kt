package santa.compiler.desugar

import santa.compiler.parser.*

/**
 * Desugars placeholder expressions (`_`) into lambda expressions.
 *
 * Examples:
 * - `_ + 1` → `|$0| $0 + 1`
 * - `10 - _` → `|$0| 10 - $0`
 * - `_ / _` → `|$0, $1| $0 / $1`
 *
 * Placeholders create anonymous functions where each `_` becomes a parameter.
 * Parameters are named `$0`, `$1`, etc. to avoid conflicts with user identifiers.
 */
object PlaceholderDesugarer {

    /**
     * Desugar all placeholder expressions in a program.
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
        is LetExpr -> desugarExpr(stmt) as LetExpr
        is ReturnExpr -> desugarExpr(stmt) as ReturnExpr
        is BreakExpr -> desugarExpr(stmt) as BreakExpr
    }

    private fun desugarExpr(expr: Expr): Expr {
        // Handle operator expressions (e.g., `<` → `|$0, $1| $0 < $1`)
        if (expr is OperatorExpr) {
            return desugarOperator(expr)
        }

        // First check if this expression contains placeholders at the top level
        // If so, wrap it in a lambda
        val placeholderCount = countPlaceholders(expr)
        if (placeholderCount > 0) {
            return wrapInLambda(expr, placeholderCount)
        }

        // Otherwise, recursively desugar nested expressions
        return desugarNested(expr)
    }

    /**
     * Desugar an operator expression to a lambda.
     * E.g., `<` → `|$0, $1| $0 < $1`
     */
    private fun desugarOperator(expr: OperatorExpr): Expr {
        val params = listOf(NamedParam("\$0"), NamedParam("\$1"))
        val binaryOp = operatorToBinaryOperator(expr.operator)
        val body = BinaryExpr(
            operator = binaryOp,
            left = IdentifierExpr("\$0", expr.span),
            right = IdentifierExpr("\$1", expr.span),
            span = expr.span
        )
        return FunctionExpr(params = params, body = body, span = expr.span)
    }

    private fun operatorToBinaryOperator(op: String): BinaryOperator = when (op) {
        "+" -> BinaryOperator.PLUS
        "-" -> BinaryOperator.MINUS
        "*" -> BinaryOperator.MULTIPLY
        "/" -> BinaryOperator.DIVIDE
        "%" -> BinaryOperator.MODULO
        "<" -> BinaryOperator.LESS
        ">" -> BinaryOperator.GREATER
        "<=" -> BinaryOperator.LESS_EQUAL
        ">=" -> BinaryOperator.GREATER_EQUAL
        "==" -> BinaryOperator.EQUAL
        "!=" -> BinaryOperator.NOT_EQUAL
        else -> throw IllegalArgumentException("Unknown operator: $op")
    }

    /**
     * Wrap an expression containing placeholders in a lambda.
     * Each placeholder becomes a parameter.
     */
    private fun wrapInLambda(expr: Expr, placeholderCount: Int): Expr {
        val params = (0 until placeholderCount).map { NamedParam("\$${it}") }
        var paramIndex = 0
        val body = replacePlaceholders(expr) { paramIndex++ }
        // After replacing placeholders, desugar any nested expressions in the body
        val desugaredBody = desugarNested(body)
        return FunctionExpr(
            params = params,
            body = desugaredBody,
            span = expr.span
        )
    }

    /**
     * Replace placeholders with identifier references to lambda parameters.
     *
     * Important: For call arguments, only replace BARE placeholders.
     * Expressions containing placeholders (like `_ + 1`) should be left alone
     * to be desugared independently at the argument level.
     */
    private fun replacePlaceholders(expr: Expr, nextIndex: () -> Int): Expr = when (expr) {
        is PlaceholderExpr -> {
            val index = nextIndex()
            IdentifierExpr("\$${index}", expr.span)
        }
        is BinaryExpr -> expr.copy(
            left = replacePlaceholders(expr.left, nextIndex),
            right = replacePlaceholders(expr.right, nextIndex)
        )
        is UnaryExpr -> expr.copy(
            expr = replacePlaceholders(expr.expr, nextIndex)
        )
        is CallExpr -> expr.copy(
            callee = replacePlaceholders(expr.callee, nextIndex),
            arguments = expr.arguments.map { arg ->
                when (arg) {
                    is ExprArgument -> {
                        // Only replace bare placeholders; expressions with placeholders
                        // will be desugared independently in desugarNested
                        if (arg.expr is PlaceholderExpr) {
                            ExprArgument(replacePlaceholders(arg.expr, nextIndex))
                        } else {
                            arg  // Leave non-bare args alone
                        }
                    }
                    is SpreadArgument -> {
                        if (arg.expr is PlaceholderExpr) {
                            SpreadArgument(replacePlaceholders(arg.expr, nextIndex))
                        } else {
                            arg
                        }
                    }
                }
            }
        )
        is IndexExpr -> expr.copy(
            target = replacePlaceholders(expr.target, nextIndex),
            index = replacePlaceholders(expr.index, nextIndex)
        )
        // Infix calls: only replace bare placeholders in left and right operands
        is InfixCallExpr -> expr.copy(
            left = if (expr.left is PlaceholderExpr) replacePlaceholders(expr.left, nextIndex) else expr.left,
            right = if (expr.right is PlaceholderExpr) replacePlaceholders(expr.right, nextIndex) else expr.right
        )
        // For other expression types, we don't expect placeholders at this level
        // but we need to handle them if they contain nested expressions
        else -> expr
    }

    /**
     * Count placeholders in an expression (non-recursively into nested lambdas/blocks/calls).
     * Only counts placeholders that would be captured by wrapping this expression.
     *
     * For call arguments:
     * - Bare placeholders (`_`) count toward partial application of the call
     * - Expressions containing placeholders (`_ + 1`) create their own lambdas
     */
    private fun countPlaceholders(expr: Expr): Int = when (expr) {
        is PlaceholderExpr -> 1
        is BinaryExpr -> {
            // Compose and pipeline operators are placeholder scope boundaries
            // Each side gets its own lambda independently
            when (expr.operator) {
                BinaryOperator.COMPOSE, BinaryOperator.PIPELINE -> 0
                else -> countPlaceholders(expr.left) + countPlaceholders(expr.right)
            }
        }
        is UnaryExpr -> countPlaceholders(expr.expr)
        // Count bare placeholders in call arguments, but not placeholders inside expressions
        is CallExpr -> countPlaceholders(expr.callee) + countPlaceholdersInCallArgs(expr.arguments)
        is IndexExpr -> countPlaceholders(expr.target) + countPlaceholders(expr.index)
        // Infix calls: count bare placeholders in left and right operands
        is InfixCallExpr -> {
            val leftCount = if (expr.left is PlaceholderExpr) 1 else 0
            val rightCount = if (expr.right is PlaceholderExpr) 1 else 0
            leftCount + rightCount
        }
        // Don't look inside functions, blocks, if, match - they have their own scope
        is FunctionExpr, is BlockExpr, is IfExpr, is MatchExpr -> 0
        // Literals and identifiers have no placeholders
        else -> 0
    }

    /**
     * Count only bare placeholders in call arguments (not placeholders inside expressions).
     * Bare placeholders create partial application of the call.
     * Expressions containing placeholders are desugared independently at the argument level.
     */
    private fun countPlaceholdersInCallArgs(arguments: List<CallArgument>): Int {
        return arguments.count { arg ->
            when (arg) {
                is ExprArgument -> arg.expr is PlaceholderExpr
                is SpreadArgument -> arg.expr is PlaceholderExpr
            }
        }
    }

    /**
     * Recursively desugar nested expressions (ones that don't directly contain placeholders).
     */
    private fun desugarNested(expr: Expr): Expr = when (expr) {
        is IntLiteralExpr, is DecimalLiteralExpr, is StringLiteralExpr,
        is BoolLiteralExpr, is NilLiteralExpr, is IdentifierExpr, is PlaceholderExpr,
        is OperatorExpr -> expr  // OperatorExpr should have been desugared already

        is UnaryExpr -> expr.copy(expr = desugarExpr(expr.expr))

        is BinaryExpr -> expr.copy(
            left = desugarExpr(expr.left),
            right = desugarExpr(expr.right)
        )

        is ListLiteralExpr -> expr.copy(
            elements = expr.elements.map { elem ->
                when (elem) {
                    is ExprElement -> ExprElement(desugarExpr(elem.expr))
                    is SpreadElement -> SpreadElement(desugarExpr(elem.expr))
                }
            }
        )

        is SetLiteralExpr -> expr.copy(
            elements = expr.elements.map { elem ->
                when (elem) {
                    is ExprElement -> ExprElement(desugarExpr(elem.expr))
                    is SpreadElement -> SpreadElement(desugarExpr(elem.expr))
                }
            }
        )

        is DictLiteralExpr -> expr.copy(
            entries = expr.entries.map { entry ->
                when (entry) {
                    is KeyValueEntry -> KeyValueEntry(
                        desugarExpr(entry.key),
                        desugarExpr(entry.value)
                    )
                    is ShorthandEntry -> entry
                }
            }
        )

        is CallExpr -> expr.copy(
            callee = desugarExpr(expr.callee),
            arguments = expr.arguments.map { arg ->
                when (arg) {
                    is ExprArgument -> ExprArgument(desugarExpr(arg.expr))
                    is SpreadArgument -> SpreadArgument(desugarExpr(arg.expr))
                }
            }
        )

        is IndexExpr -> expr.copy(
            target = desugarExpr(expr.target),
            index = desugarExpr(expr.index)
        )

        is FunctionExpr -> expr.copy(body = desugarExpr(expr.body))

        is BlockExpr -> expr.copy(
            statements = expr.statements.map { desugarStatement(it) }
        )

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

        is AssignmentExpr -> expr.copy(value = desugarExpr(expr.value))

        is LetExpr -> expr.copy(value = desugarExpr(expr.value))

        is ReturnExpr -> expr.copy(value = desugarExpr(expr.value))

        is BreakExpr -> expr.copy(value = desugarExpr(expr.value))

        is RangeExpr -> expr.copy(
            start = desugarExpr(expr.start),
            end = expr.end?.let { desugarExpr(it) }
        )

        is InfixCallExpr -> expr.copy(
            left = desugarExpr(expr.left),
            right = desugarExpr(expr.right)
        )

        is TestBlockExpr -> expr.copy(
            entries = expr.entries.map { entry ->
                TestEntry(entry.name, desugarExpr(entry.expr), entry.span)
            }
        )
    }
}
