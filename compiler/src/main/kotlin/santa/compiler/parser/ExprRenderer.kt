package santa.compiler.parser

object ExprRenderer {
    fun render(expr: Expr): String = when (expr) {
        is IntLiteralExpr -> expr.lexeme
        is DecimalLiteralExpr -> expr.lexeme
        is StringLiteralExpr -> expr.lexeme
        is BoolLiteralExpr -> expr.value.toString()
        is NilLiteralExpr -> "nil"
        is IdentifierExpr -> expr.name
        is PlaceholderExpr -> "_"
        is ListLiteralExpr -> renderCollection("[", "]", expr.elements)
        is SetLiteralExpr -> renderCollection("{", "}", expr.elements)
        is DictLiteralExpr -> renderDict(expr)
        is UnaryExpr -> "(${renderUnaryOperator(expr.operator)} ${render(expr.expr)})"
        is BinaryExpr -> "(${renderBinaryOperator(expr.operator)} ${render(expr.left)} ${render(expr.right)})"
        is AssignmentExpr -> "(= ${render(expr.target)} ${render(expr.value)})"
        is RangeExpr -> renderRange(expr)
        is InfixCallExpr -> "(infix ${expr.functionName} ${render(expr.left)} ${render(expr.right)})"
        is CallExpr -> renderCall(expr)
        is IndexExpr -> "(index ${render(expr.target)} ${render(expr.index)})"
        is FunctionExpr -> "(fn [${renderParams(expr.params)}] ${render(expr.body)})"
        is BlockExpr -> renderBlock(expr)
    }

    private fun renderCollection(open: String, close: String, elements: List<CollectionElement>): String {
        val rendered = elements.joinToString(", ") { element ->
            when (element) {
                is ExprElement -> render(element.expr)
                is SpreadElement -> "..${render(element.expr)}"
            }
        }
        return "$open$rendered$close"
    }

    private fun renderDict(expr: DictLiteralExpr): String {
        val rendered = expr.entries.joinToString(", ") { entry ->
            "${render(entry.key)}: ${render(entry.value)}"
        }
        return "#{$rendered}"
    }

    private fun renderRange(expr: RangeExpr): String {
        val op = if (expr.isInclusive) "..=" else ".."
        val end = expr.end?.let { " ${render(it)}" } ?: ""
        return "($op ${render(expr.start)}$end)"
    }

    private fun renderCall(expr: CallExpr): String {
        val args = expr.arguments.joinToString(" ") { argument ->
            when (argument) {
                is ExprArgument -> render(argument.expr)
                is SpreadArgument -> "..${render(argument.expr)}"
            }
        }
        return if (args.isEmpty()) {
            "(call ${render(expr.callee)})"
        } else {
            "(call ${render(expr.callee)} $args)"
        }
    }

    private fun renderParams(params: List<Param>): String {
        return params.joinToString(" ") { param ->
            when (param) {
                is NamedParam -> param.name
                is RestParam -> "..${param.name}"
                PlaceholderParam -> "_"
            }
        }
    }

    private fun renderBlock(expr: BlockExpr): String {
        if (expr.expressions.isEmpty()) return "{}"
        val rendered = expr.expressions.joinToString("; ") { render(it) }
        return "{ $rendered }"
    }

    private fun renderUnaryOperator(operator: UnaryOperator): String = when (operator) {
        UnaryOperator.NEGATE -> "-"
        UnaryOperator.NOT -> "!"
    }

    private fun renderBinaryOperator(operator: BinaryOperator): String = when (operator) {
        BinaryOperator.PLUS -> "+"
        BinaryOperator.MINUS -> "-"
        BinaryOperator.MULTIPLY -> "*"
        BinaryOperator.DIVIDE -> "/"
        BinaryOperator.MODULO -> "%"
        BinaryOperator.EQUAL -> "=="
        BinaryOperator.NOT_EQUAL -> "!="
        BinaryOperator.LESS -> "<"
        BinaryOperator.LESS_EQUAL -> "<="
        BinaryOperator.GREATER -> ">"
        BinaryOperator.GREATER_EQUAL -> ">="
        BinaryOperator.AND -> "&&"
        BinaryOperator.OR -> "||"
        BinaryOperator.PIPELINE -> "|>"
        BinaryOperator.COMPOSE -> ">>"
    }
}
