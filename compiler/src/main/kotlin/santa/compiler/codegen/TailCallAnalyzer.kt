package santa.compiler.codegen

import santa.compiler.parser.*

/**
 * Analyzes tail-call positions in santa-lang expressions.
 *
 * Per LANG.txt §8.9:
 * - TCO is available for self-recursion only (function calling itself in tail position)
 * - The recursive call must be in tail position (last expression evaluated)
 * - No operations after the recursive call
 *
 * An expression is in tail position if it is the last thing evaluated before returning.
 */
object TailCallAnalyzer {

    /**
     * Analyzes a function to determine if it's tail-recursive.
     *
     * A function is tail-recursive if:
     * 1. It references itself (self-recursion)
     * 2. All self-calls are in tail position
     *
     * @param funcName The name of the function being analyzed
     * @param body The function body expression
     * @return TailRecursionInfo if the function is tail-recursive, null otherwise
     */
    fun analyzeTailRecursion(funcName: String, body: Expr): TailRecursionInfo? {
        // Find all self-calls
        val selfCalls = findSelfCalls(funcName, body)
        if (selfCalls.isEmpty()) {
            return null // Not recursive at all
        }

        // Check if all self-calls are in tail position
        val tailPositionCalls = selfCalls.filter { isInTailPosition(it, body) }
        val nonTailCalls = selfCalls.filter { it !in tailPositionCalls }

        return if (nonTailCalls.isEmpty() && tailPositionCalls.isNotEmpty()) {
            TailRecursionInfo(funcName, tailPositionCalls)
        } else {
            null // Has non-tail self-calls, cannot optimize
        }
    }

    /**
     * Finds all calls to the specified function name within an expression.
     */
    private fun findSelfCalls(funcName: String, expr: Expr): List<CallExpr> {
        val calls = mutableListOf<CallExpr>()

        fun visit(e: Expr) {
            when (e) {
                is CallExpr -> {
                    val callee = e.callee
                    if (callee is IdentifierExpr && callee.name == funcName) {
                        calls.add(e)
                    }
                    visit(e.callee)
                    e.arguments.forEach { arg ->
                        when (arg) {
                            is ExprArgument -> visit(arg.expr)
                            is SpreadArgument -> visit(arg.expr)
                        }
                    }
                }
                is BinaryExpr -> { visit(e.left); visit(e.right) }
                is UnaryExpr -> visit(e.expr)
                is BlockExpr -> e.statements.forEach { stmt ->
                    when (stmt) {
                        is ExprStatement -> visit(stmt.expr)
                        is LetExpr -> visit(stmt.value)
                        is ReturnExpr -> stmt.value?.let { visit(it) }
                        is BreakExpr -> stmt.value?.let { visit(it) }
                    }
                }
                is IfExpr -> {
                    when (val cond = e.condition) {
                        is ExprCondition -> visit(cond.expr)
                        is LetCondition -> visit(cond.value)
                    }
                    visit(e.thenBranch)
                    e.elseBranch?.let { visit(it) }
                }
                is MatchExpr -> {
                    visit(e.subject)
                    e.arms.forEach { arm ->
                        arm.guard?.let { visit(it) }
                        visit(arm.body)
                    }
                }
                is FunctionExpr -> {
                    // Don't recurse into nested functions - they have their own context
                }
                is IndexExpr -> { visit(e.target); visit(e.index) }
                is AssignmentExpr -> visit(e.value)
                is ListLiteralExpr -> e.elements.forEach { elem ->
                    when (elem) {
                        is ExprElement -> visit(elem.expr)
                        is SpreadElement -> visit(elem.expr)
                    }
                }
                is SetLiteralExpr -> e.elements.forEach { elem ->
                    when (elem) {
                        is ExprElement -> visit(elem.expr)
                        is SpreadElement -> visit(elem.expr)
                    }
                }
                is DictLiteralExpr -> e.entries.forEach { entry ->
                    when (entry) {
                        is KeyValueEntry -> { visit(entry.key); visit(entry.value) }
                        is ShorthandEntry -> {}
                    }
                }
                is RangeExpr -> { visit(e.start); e.end?.let { visit(it) } }
                is LetExpr -> visit(e.value)
                is ReturnExpr -> e.value?.let { visit(it) }
                is BreakExpr -> e.value?.let { visit(it) }
                // Terminals
                is IntLiteralExpr, is DecimalLiteralExpr, is StringLiteralExpr,
                is BoolLiteralExpr, is NilLiteralExpr, is IdentifierExpr,
                is PlaceholderExpr, is OperatorExpr, is TestBlockExpr, is InfixCallExpr -> {}
            }
        }

        visit(expr)
        return calls
    }

    /**
     * Checks if a specific call expression is in tail position within the containing expression.
     *
     * Tail position means the call is the last thing evaluated before returning from the function.
     */
    private fun isInTailPosition(call: CallExpr, body: Expr): Boolean {
        return isExprInTailPosition(call, body)
    }

    /**
     * Recursively determines if a target expression is in tail position within a containing expression.
     *
     * @param target The expression we're checking
     * @param container The containing expression
     * @return true if target is in tail position within container
     */
    private fun isExprInTailPosition(target: Expr, container: Expr): Boolean {
        // If the container IS the target, it's in tail position (at the top level)
        if (container === target) {
            return true
        }

        return when (container) {
            // The last statement in a block is in tail position
            is BlockExpr -> {
                if (container.statements.isEmpty()) return false
                val lastStmt = container.statements.last()
                when (lastStmt) {
                    is ExprStatement -> isExprInTailPosition(target, lastStmt.expr)
                    is LetExpr -> false // Let is never in tail position (it returns nil)
                    is ReturnExpr -> lastStmt.value?.let { isExprInTailPosition(target, it) } ?: false
                    is BreakExpr -> false
                }
            }

            // Both branches of an if-expression are in tail position if the if itself is
            is IfExpr -> {
                isExprInTailPosition(target, container.thenBranch) ||
                        (container.elseBranch != null && isExprInTailPosition(target, container.elseBranch))
            }

            // Match arm bodies are in tail position if the match is
            is MatchExpr -> {
                container.arms.any { arm -> isExprInTailPosition(target, arm.body) }
            }

            // Binary expressions: target is NOT in tail position (operation happens after)
            is BinaryExpr -> false

            // Unary expressions: target is NOT in tail position (operation happens after)
            is UnaryExpr -> false

            // Call expressions: target is NOT in tail position unless it IS the call
            is CallExpr -> container === target

            // Index expressions: target is NOT in tail position
            is IndexExpr -> false

            // Assignment: target is NOT in tail position
            is AssignmentExpr -> false

            // Literals and identifiers can only be in tail position if they ARE the target
            is IntLiteralExpr, is DecimalLiteralExpr, is StringLiteralExpr,
            is BoolLiteralExpr, is NilLiteralExpr, is IdentifierExpr -> false

            // Other expressions are not in tail position
            else -> false
        }
    }
}

/**
 * Information about a tail-recursive function.
 */
data class TailRecursionInfo(
    /** The name of the recursive function */
    val funcName: String,
    /** All tail-position self-calls in the function body */
    val tailCalls: List<CallExpr>
)
