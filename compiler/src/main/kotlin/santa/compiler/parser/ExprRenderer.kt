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
        is LetExpr -> renderLet(expr)
        is ReturnExpr -> renderReturn(expr)
        is BreakExpr -> renderBreak(expr)
        is RangeExpr -> renderRange(expr)
        is InfixCallExpr -> "(infix ${expr.functionName} ${render(expr.left)} ${render(expr.right)})"
        is CallExpr -> renderCall(expr)
        is IndexExpr -> "(index ${render(expr.target)} ${render(expr.index)})"
        is FunctionExpr -> "(fn [${renderParams(expr.params)}] ${render(expr.body)})"
        is BlockExpr -> renderBlock(expr)
        is IfExpr -> renderIf(expr)
        is MatchExpr -> renderMatch(expr)
        is TestBlockExpr -> renderTestBlock(expr)
    }

    fun renderProgram(program: Program): String {
        return program.items.joinToString("\n") { renderTopLevel(it) }
    }

    private fun renderTopLevel(item: TopLevel): String = when (item) {
        is Section -> {
            val slow = if (item.isSlow) "@slow " else ""
            "$slow(section ${item.name} ${render(item.expr)})"
        }
        is StatementItem -> renderStatement(item.statement)
    }

    private fun renderTestBlock(expr: TestBlockExpr): String {
        if (expr.entries.isEmpty()) return "(test-block)"
        val entries = expr.entries.joinToString(" ") { entry ->
            "(${entry.name} ${render(entry.expr)})"
        }
        return "(test-block $entries)"
    }

    private fun renderStatement(statement: Statement): String = when (statement) {
        is ExprStatement -> render(statement.expr)
        is LetExpr -> renderLet(statement)
        is ReturnExpr -> renderReturn(statement)
        is BreakExpr -> renderBreak(statement)
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
            when (entry) {
                is KeyValueEntry -> "${render(entry.key)}: ${render(entry.value)}"
                is ShorthandEntry -> entry.name
            }
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
        if (expr.statements.isEmpty()) return "{}"
        val rendered = expr.statements.joinToString("; ") { renderStatement(it) }
        return "{ $rendered }"
    }

    private fun renderLet(expr: LetExpr): String {
        val mutability = if (expr.isMutable) "mut " else ""
        return "(let ${mutability}${renderPattern(expr.pattern)} ${render(expr.value)})"
    }

    private fun renderReturn(expr: ReturnExpr): String = "(return ${render(expr.value)})"

    private fun renderBreak(expr: BreakExpr): String = "(break ${render(expr.value)})"

    private fun renderIf(expr: IfExpr): String {
        val elseBranch = expr.elseBranch?.let { " ${render(it)}" } ?: ""
        return "(if ${renderCondition(expr.condition)} ${render(expr.thenBranch)}$elseBranch)"
    }

    private fun renderMatch(expr: MatchExpr): String {
        val arms = expr.arms.joinToString(" ") { renderMatchArm(it) }
        return if (arms.isEmpty()) {
            "(match ${render(expr.subject)})"
        } else {
            "(match ${render(expr.subject)} $arms)"
        }
    }

    private fun renderMatchArm(arm: MatchArm): String {
        val guard = arm.guard?.let { " (if ${render(it)})" } ?: ""
        return "(arm ${renderPattern(arm.pattern)}$guard ${render(arm.body)})"
    }

    private fun renderCondition(condition: IfCondition): String = when (condition) {
        is ExprCondition -> render(condition.expr)
        is LetCondition -> "(if-let ${renderPattern(condition.pattern)} ${render(condition.value)})"
    }

    private fun renderPattern(pattern: Pattern): String = when (pattern) {
        is WildcardPattern -> "_"
        is BindingPattern -> pattern.name
        is RestPattern -> pattern.name?.let { "..$it" } ?: ".."
        is ListPattern -> "[${pattern.elements.joinToString(", ") { renderPattern(it) }}]"
        is LiteralPattern -> render(pattern.literal)
        is RangePattern -> renderRangePattern(pattern)
    }

    private fun renderRangePattern(pattern: RangePattern): String {
        val op = if (pattern.isInclusive) "..=" else ".."
        val end = pattern.end?.let { " $it" } ?: ""
        return "($op ${pattern.start}$end)"
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
