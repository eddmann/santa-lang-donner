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

        // First pass: declare all forward-referenceable names
        // This includes section names and top-level function bindings
        for (item in program.items) {
            when (item) {
                is Section -> {
                    if (item.name in SECTION_NAMES) {
                        declareName(item.name)
                    }
                }
                is StatementItem -> {
                    val stmt = item.statement
                    // Declare top-level function bindings for forward reference
                    // This allows mutual recursion and forward references between functions
                    if (stmt is LetExpr && isTopLevelFunctionBinding(stmt)) {
                        declarePattern(stmt.pattern)
                    }
                }
            }
        }

        // Second pass: resolve all expressions
        program.items.forEach { resolveTopLevel(it) }
        popScope()
    }

    /**
     * Check if a let binding is a top-level function that may need forward reference support.
     * This includes direct function expressions and calls wrapping functions (like memoize).
     */
    private fun isTopLevelFunctionBinding(expr: LetExpr): Boolean {
        return expr.pattern is BindingPattern &&
            (expr.value is FunctionExpr || hasNestedFunctionArg(expr.value))
    }

    private fun resolveTopLevel(item: TopLevel) {
        when (item) {
            is Section -> resolveSection(item)
            is StatementItem -> resolveStatement(item.statement)
        }
    }

    private fun resolveSection(section: Section) {
        // Sections like input:, part_one:, part_two: are bound as variables
        // But we also need to resolve the expression
        resolveExpr(section.expr)
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
            is OperatorExpr -> {}  // No resolution needed - will be desugared
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
            is TestBlockExpr -> resolveTestBlockExpr(expr)
        }
    }

    private fun resolveTestBlockExpr(expr: TestBlockExpr) {
        // Test block entries are resolved in their own scope where `input` is defined
        pushScope()
        // Pre-declare test-specific section names
        declareName("input")
        declareName("part_one")
        declareName("part_two")
        expr.entries.forEach { entry ->
            resolveExpr(entry.expr)
        }
        popScope()
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
        // For function bindings with simple names, declare the name first
        // to allow recursive self-reference within the function body.
        // This also applies to calls with function arguments (e.g., memoize |n| ... fib(n-1) ...)
        val isFunctionBinding = (expr.value is FunctionExpr || hasNestedFunctionArg(expr.value)) &&
            expr.pattern is BindingPattern

        // At top-level (scopes.size == 1), function bindings are already declared in first pass.
        // In nested scopes, declare now for self-reference support.
        val isTopLevel = scopes.size == 1
        val needsDeclaration = isFunctionBinding && !isTopLevel

        if (needsDeclaration) {
            declarePattern(expr.pattern)
        }

        resolveExpr(expr.value)

        if (!isFunctionBinding) {
            declarePattern(expr.pattern)
        }
    }

    /**
     * Check if an expression is a call with a function argument, which may need
     * to reference the binding being defined (e.g., memoize |n| ... self(n-1) ...).
     */
    private fun hasNestedFunctionArg(expr: Expr): Boolean {
        return when (expr) {
            is CallExpr -> expr.arguments.any { it.expr is FunctionExpr || hasNestedFunctionArg(it.expr) }
            is BinaryExpr -> hasNestedFunctionArg(expr.left) || hasNestedFunctionArg(expr.right)
            else -> false
        }
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
                is PatternParam -> declarePattern(param.pattern)
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
        // Note: LANG.txt §14.6 says builtins can't be shadowed, but real-world
        // AOC solutions do shadow them (e.g., signum, cycle). We allow it for compatibility.
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
            "upper",
            "lower",
            "replace",
            "join",
            "last",
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
        // Known section names that get bound as variables
        val SECTION_NAMES = setOf("input", "part_one", "part_two")
    }
}
