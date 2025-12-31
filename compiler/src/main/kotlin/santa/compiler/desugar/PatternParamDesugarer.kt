package santa.compiler.desugar

import santa.compiler.parser.*

/**
 * Desugars pattern parameters in lambdas into regular parameters with destructuring let bindings.
 *
 * Example:
 * - `|[a, b]| a + b` → `|$0| { let [a, b] = $0; a + b }`
 * - `|[x, y], z| x + y + z` → `|$0, z| { let [x, y] = $0; x + y + z }`
 *
 * This allows the existing let destructuring codegen to handle pattern matching.
 */
object PatternParamDesugarer {

    /**
     * Desugar all pattern parameters in a program.
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

    private fun desugarExpr(expr: Expr): Expr = when (expr) {
        is IntLiteralExpr, is DecimalLiteralExpr, is StringLiteralExpr,
        is BoolLiteralExpr, is NilLiteralExpr, is IdentifierExpr, is PlaceholderExpr -> expr

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

        is FunctionExpr -> desugarFunctionExpr(expr)

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

    /**
     * Desugar a function expression with pattern parameters.
     *
     * If any parameter is a PatternParam, convert it to a NamedParam with a synthetic name
     * and prepend a let destructuring statement to the body.
     */
    private fun desugarFunctionExpr(expr: FunctionExpr): FunctionExpr {
        val patternParams = expr.params.filterIsInstance<PatternParam>()
        if (patternParams.isEmpty()) {
            // No pattern params, just recurse into body
            return expr.copy(body = desugarExpr(expr.body))
        }

        // Generate new params with synthetic names for pattern params
        var paramIndex = 0
        val newParams = expr.params.map { param ->
            when (param) {
                is PatternParam -> {
                    val syntheticName = "\$arg${paramIndex++}"
                    NamedParam(syntheticName)
                }
                else -> param
            }
        }

        // Generate let statements for pattern destructuring
        paramIndex = 0
        val letStatements = mutableListOf<Statement>()
        for (param in expr.params) {
            if (param is PatternParam) {
                val syntheticName = "\$arg${paramIndex++}"
                val letExpr = LetExpr(
                    pattern = param.pattern,
                    value = IdentifierExpr(syntheticName, expr.span),
                    isMutable = false,
                    span = expr.span
                )
                letStatements.add(letExpr)
            }
        }

        // Wrap the body in a block with the let statements prepended
        val desugaredBody = desugarExpr(expr.body)
        val newBody = if (desugaredBody is BlockExpr) {
            BlockExpr(letStatements + desugaredBody.statements, desugaredBody.span)
        } else {
            // Convert expression body to block: { let bindings; expr }
            val bodyStatement = ExprStatement(desugaredBody, desugaredBody.span)
            BlockExpr(letStatements + bodyStatement, expr.span)
        }

        return FunctionExpr(newParams, newBody, expr.span)
    }
}
