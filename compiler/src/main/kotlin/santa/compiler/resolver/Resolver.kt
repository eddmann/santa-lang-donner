package santa.compiler.resolver

import santa.compiler.lexer.SourcePosition
import santa.compiler.parser.*

class Resolver(
    private val protectedNames: Set<String> = ProtectedNames.all,
) {
    private val scopes = ArrayDeque<MutableSet<String>>()
    private val functionBreakability = ArrayDeque<Boolean>()

    fun resolve(program: Program) {
        pushScope()
        program.items.forEach { resolveTopLevel(it) }
        popScope()
    }

    private fun resolveTopLevel(item: TopLevel) {
        when (item) {
            is Section -> resolveExpr(item.expr)
            is StatementItem -> resolveStatement(item.statement)
        }
    }

    private fun resolveStatement(statement: Statement) {
        when (statement) {
            is ExprStatement -> resolveExpr(statement.expr)
            is LetExpr -> resolveLetExpr(statement)
            is ReturnExpr -> resolveReturnExpr(statement)
            is BreakExpr -> resolveBreakExpr(statement)
        }
    }

    private fun resolveExpr(expr: Expr) {
        when (expr) {
            is IntLiteralExpr -> {}
            is DecimalLiteralExpr -> {}
            is StringLiteralExpr -> {}
            is BoolLiteralExpr -> {}
            is NilLiteralExpr -> {}
            is IdentifierExpr -> resolveIdentifier(expr)
            is PlaceholderExpr -> {}
            is ListLiteralExpr -> expr.elements.forEach { resolveExpr(it.expr) }
            is SetLiteralExpr -> expr.elements.forEach { resolveExpr(it.expr) }
            is DictLiteralExpr -> expr.entries.forEach { resolveDictEntry(it) }
            is UnaryExpr -> resolveExpr(expr.expr)
            is BinaryExpr -> resolveBinaryExpr(expr)
            is AssignmentExpr -> resolveAssignmentExpr(expr)
            is LetExpr -> resolveLetExpr(expr)
            is ReturnExpr -> resolveReturnExpr(expr)
            is BreakExpr -> resolveBreakExpr(expr)
            is RangeExpr -> resolveRangeExpr(expr)
            is InfixCallExpr -> resolveInfixCallExpr(expr)
            is CallExpr -> resolveCallExpr(expr)
            is IndexExpr -> resolveIndexExpr(expr)
            is FunctionExpr -> resolveFunctionExpr(expr, isBreakable = false)
            is BlockExpr -> resolveBlockExpr(expr)
            is IfExpr -> resolveIfExpr(expr)
            is MatchExpr -> resolveMatchExpr(expr)
        }
    }

    private fun resolveIdentifier(expr: IdentifierExpr) {
        if (isNameDefined(expr.name)) return
        throw ResolveException("Undefined identifier '${expr.name}'", expr.span.start)
    }

    private fun resolveDictEntry(entry: DictEntry) {
        when (entry) {
            is KeyValueEntry -> {
                resolveExpr(entry.key)
                resolveExpr(entry.value)
            }
            is ShorthandEntry -> resolveShorthandEntry(entry)
        }
    }

    private fun resolveShorthandEntry(entry: ShorthandEntry) {
        if (isNameDefined(entry.name)) return
        throw ResolveException("Undefined identifier '${entry.name}'", unknownPosition)
    }

    private fun resolveBinaryExpr(expr: BinaryExpr) {
        resolveExpr(expr.left)
        resolveExpr(expr.right)
    }

    private fun resolveAssignmentExpr(expr: AssignmentExpr) {
        if (!isNameDefinedInScopes(expr.target.name)) {
            throw ResolveException("Undefined identifier '${expr.target.name}'", expr.target.span.start)
        }
        resolveExpr(expr.value)
    }

    private fun resolveLetExpr(expr: LetExpr) {
        resolveExpr(expr.value)
        declarePattern(expr.pattern)
    }

    private fun resolveReturnExpr(expr: ReturnExpr) {
        if (functionBreakability.isEmpty()) {
            throw ResolveException("Return outside function", expr.span.start)
        }
        resolveExpr(expr.value)
    }

    private fun resolveBreakExpr(expr: BreakExpr) {
        if (functionBreakability.lastOrNull() != true) {
            throw ResolveException("Break outside iteration", expr.span.start)
        }
        resolveExpr(expr.value)
    }

    private fun resolveRangeExpr(expr: RangeExpr) {
        resolveExpr(expr.start)
        expr.end?.let { resolveExpr(it) }
    }

    private fun resolveInfixCallExpr(expr: InfixCallExpr) {
        resolveExpr(expr.left)
        resolveExpr(expr.right)
    }

    private fun resolveCallExpr(expr: CallExpr) {
        resolveExpr(expr.callee)
        val isBreakable = isBreakableCall(expr.callee)
        expr.arguments.forEach { argument ->
            val value = argument.expr
            if (value is FunctionExpr) {
                resolveFunctionExpr(value, isBreakable)
            } else {
                resolveExpr(value)
            }
        }
    }

    private fun resolveIndexExpr(expr: IndexExpr) {
        resolveExpr(expr.target)
        resolveExpr(expr.index)
    }

    private fun resolveFunctionExpr(expr: FunctionExpr, isBreakable: Boolean) {
        functionBreakability.addLast(isBreakable)
        pushScope()
        declareParams(expr.params)
        resolveExpr(expr.body)
        popScope()
        functionBreakability.removeLast()
    }

    private fun resolveBlockExpr(expr: BlockExpr) {
        pushScope()
        expr.statements.forEach { resolveStatement(it) }
        popScope()
    }

    private fun resolveIfExpr(expr: IfExpr) {
        when (val condition = expr.condition) {
            is ExprCondition -> resolveExpr(condition.expr)
            is LetCondition -> resolveExpr(condition.value)
        }

        when (val condition = expr.condition) {
            is ExprCondition -> resolveBlockExpr(expr.thenBranch)
            is LetCondition -> {
                pushScope()
                declarePattern(condition.pattern)
                resolveBlockExpr(expr.thenBranch)
                popScope()
            }
        }

        expr.elseBranch?.let { resolveExpr(it) }
    }

    private fun resolveMatchExpr(expr: MatchExpr) {
        resolveExpr(expr.subject)
        expr.arms.forEach { resolveMatchArm(it) }
    }

    private fun resolveMatchArm(arm: MatchArm) {
        pushScope()
        declarePattern(arm.pattern)
        arm.guard?.let { resolveExpr(it) }
        resolveBlockExpr(arm.body)
        popScope()
    }

    private fun declareParams(params: List<Param>) {
        params.forEach { param ->
            when (param) {
                is NamedParam -> declareName(param.name)
                is RestParam -> declareName(param.name)
                PlaceholderParam -> {}
            }
        }
    }

    private fun declarePattern(pattern: Pattern) {
        when (pattern) {
            is WildcardPattern -> {}
            is BindingPattern -> declareName(pattern.name)
            is RestPattern -> pattern.name?.let { declareName(it) }
            is ListPattern -> pattern.elements.forEach { declarePattern(it) }
            is LiteralPattern -> {}
            is RangePattern -> {}
        }
    }

    private fun declareName(name: String) {
        if (name in protectedNames) {
            throw ResolveException("Cannot shadow built-in '$name'", unknownPosition)
        }
        scopes.last().add(name)
    }

    private fun isNameDefined(name: String): Boolean {
        return isNameDefinedInScopes(name) || name in protectedNames
    }

    private fun isNameDefinedInScopes(name: String): Boolean {
        return scopes.any { name in it }
    }

    private fun isBreakableCall(callee: Expr): Boolean {
        return callee is IdentifierExpr && callee.name in BreakableCalls.names
    }

    private fun pushScope() {
        scopes.addLast(mutableSetOf())
    }

    private fun popScope() {
        scopes.removeLast()
    }

    private object ProtectedNames {
        val all: Set<String> = setOf(
            "int",
            "ints",
            "list",
            "set",
            "dict",
            "get",
            "size",
            "first",
            "second",
            "rest",
            "keys",
            "values",
            "push",
            "assoc",
            "update",
            "update_d",
            "map",
            "filter",
            "flat_map",
            "filter_map",
            "find_map",
            "reduce",
            "fold",
            "fold_s",
            "scan",
            "each",
            "find",
            "count",
            "sum",
            "max",
            "min",
            "skip",
            "take",
            "sort",
            "reverse",
            "rotate",
            "chunk",
            "union",
            "intersection",
            "includes?",
            "excludes?",
            "any?",
            "all?",
            "zip",
            "repeat",
            "cycle",
            "iterate",
            "combinations",
            "range",
            "lines",
            "split",
            "regex_match",
            "regex_match_all",
            "md5",
            "abs",
            "signum",
            "vec_add",
            "bit_and",
            "bit_or",
            "bit_xor",
            "bit_not",
            "bit_shift_left",
            "bit_shift_right",
            "id",
            "type",
            "memoize",
            "evaluate",
            "read",
            "puts",
            "env",
        )
    }

    private object BreakableCalls {
        val names: Set<String> = setOf(
            "reduce",
            "fold",
            "fold_s",
            "scan",
            "each",
        )
    }

    private companion object {
        private val unknownPosition = SourcePosition(0, 0)
    }
}
