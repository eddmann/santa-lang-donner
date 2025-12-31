package santa.compiler.codegen

import org.objectweb.asm.ClassWriter
import org.objectweb.asm.Label
import org.objectweb.asm.MethodVisitor
import org.objectweb.asm.Opcodes.*
import santa.compiler.parser.*
import java.util.concurrent.atomic.AtomicLong

/**
 * Generates JVM bytecode from a santa-lang AST.
 *
 * Each program is compiled to a synthetic class with a static execute() method
 * that returns a Value.
 */
object CodeGenerator {
    private val classCounter = AtomicLong(0)

    fun generate(program: Program): CompiledScript {
        val className = "santa/Script${classCounter.incrementAndGet()}"
        val generator = ClassGenerator(className)
        return generator.generate(program)
    }
}

private class ClassGenerator(private val className: String) {
    private val cw = ClassWriter(ClassWriter.COMPUTE_FRAMES or ClassWriter.COMPUTE_MAXS)

    fun generate(program: Program): CompiledScript {
        cw.visit(V21, ACC_PUBLIC or ACC_FINAL, className, null, "java/lang/Object", null)

        generateExecuteMethod(program)

        cw.visitEnd()
        return CompiledScript(className.replace('/', '.'), cw.toByteArray())
    }

    private fun generateExecuteMethod(program: Program) {
        val mv = cw.visitMethod(
            ACC_PUBLIC or ACC_STATIC,
            "execute",
            "()L${VALUE_TYPE};",
            null,
            null
        )
        mv.visitCode()

        val exprGen = ExpressionGenerator(mv)

        if (program.items.isEmpty()) {
            exprGen.pushNil()
        } else {
            program.items.forEachIndexed { index, item ->
                when (item) {
                    is StatementItem -> exprGen.compileStatement(item.statement)
                    is Section -> TODO("Sections not yet implemented")
                }
                // Pop intermediate results, keep only last
                if (index < program.items.lastIndex) {
                    mv.visitInsn(POP)
                }
            }
        }

        mv.visitInsn(ARETURN)
        mv.visitMaxs(-1, -1) // Computed automatically
        mv.visitEnd()
    }

    companion object {
        const val VALUE_TYPE = "santa/runtime/value/Value"
    }
}

/**
 * Tracks a local variable binding.
 */
private data class LocalBinding(
    val name: String,
    val slot: Int,
    val isMutable: Boolean,
)

/**
 * Generates bytecode for expressions.
 *
 * Each expression leaves exactly one Value on the operand stack.
 */
private class ExpressionGenerator(private val mv: MethodVisitor) {
    // Scope stack: each scope is a map of name -> LocalBinding
    private val scopes = ArrayDeque<MutableMap<String, LocalBinding>>()
    private var nextSlot = 0

    init {
        pushScope()
    }

    fun compileStatement(statement: Statement) {
        when (statement) {
            is ExprStatement -> compileExpr(statement.expr)
            is LetExpr -> compileLetStatement(statement)
            is ReturnExpr -> TODO("Return statements not yet implemented")
            is BreakExpr -> TODO("Break statements not yet implemented")
        }
    }

    fun compileExpr(expr: Expr) {
        when (expr) {
            is IntLiteralExpr -> compileIntLiteral(expr)
            is DecimalLiteralExpr -> compileDecimalLiteral(expr)
            is StringLiteralExpr -> compileStringLiteral(expr)
            is BoolLiteralExpr -> compileBoolLiteral(expr)
            is NilLiteralExpr -> pushNil()
            is UnaryExpr -> compileUnaryExpr(expr)
            is BinaryExpr -> compileBinaryExpr(expr)
            is IdentifierExpr -> compileIdentifier(expr)
            is PlaceholderExpr -> TODO("Placeholders not yet implemented")
            is ListLiteralExpr -> compileListLiteral(expr)
            is SetLiteralExpr -> TODO("Set literals not yet implemented")
            is DictLiteralExpr -> TODO("Dict literals not yet implemented")
            is AssignmentExpr -> compileAssignment(expr)
            is LetExpr -> compileLetExpr(expr)
            is ReturnExpr -> TODO("Return expressions not yet implemented")
            is BreakExpr -> TODO("Break expressions not yet implemented")
            is RangeExpr -> TODO("Range expressions not yet implemented")
            is InfixCallExpr -> TODO("Infix calls not yet implemented")
            is CallExpr -> compileCall(expr)
            is IndexExpr -> compileIndex(expr)
            is FunctionExpr -> TODO("Function expressions not yet implemented")
            is BlockExpr -> compileBlock(expr)
            is IfExpr -> TODO("If expressions not yet implemented")
            is MatchExpr -> TODO("Match expressions not yet implemented")
        }
    }

    private fun compileIntLiteral(expr: IntLiteralExpr) {
        val value = expr.lexeme.replace("_", "").toLong()
        pushLong(value)
        mv.visitMethodInsn(
            INVOKESTATIC,
            INT_VALUE_TYPE,
            "box",
            "(J)L${INT_VALUE_TYPE};",
            false
        )
    }

    private fun compileDecimalLiteral(expr: DecimalLiteralExpr) {
        val value = expr.lexeme.replace("_", "").toDouble()
        pushDouble(value)
        mv.visitMethodInsn(
            INVOKESTATIC,
            DECIMAL_VALUE_TYPE,
            "box",
            "(D)L${DECIMAL_VALUE_TYPE};",
            false
        )
    }

    private fun compileStringLiteral(expr: StringLiteralExpr) {
        // Lexeme includes surrounding quotes - strip them first
        val content = expr.lexeme.drop(1).dropLast(1)
        // Parse escape sequences
        val processed = processEscapes(content)
        mv.visitLdcInsn(processed)
        mv.visitMethodInsn(
            INVOKESTATIC,
            STRING_VALUE_TYPE,
            "box",
            "(Ljava/lang/String;)L${STRING_VALUE_TYPE};",
            false
        )
    }

    private fun compileBoolLiteral(expr: BoolLiteralExpr) {
        mv.visitFieldInsn(
            GETSTATIC,
            BOOL_VALUE_TYPE,
            if (expr.value) "TRUE" else "FALSE",
            "L${BOOL_VALUE_TYPE};"
        )
    }

    fun pushNil() {
        mv.visitFieldInsn(
            GETSTATIC,
            NIL_VALUE_TYPE,
            "INSTANCE",
            "L${NIL_VALUE_TYPE};"
        )
    }

    private fun compileUnaryExpr(expr: UnaryExpr) {
        when (expr.operator) {
            UnaryOperator.NEGATE -> compileNegate(expr.expr)
            UnaryOperator.NOT -> compileNot(expr.expr)
        }
    }

    private fun compileNegate(operand: Expr) {
        // For unary minus, we can optimize literal negation at compile time
        when (operand) {
            is IntLiteralExpr -> {
                val value = operand.lexeme.replace("_", "").toLong()
                pushLong(-value)
                mv.visitMethodInsn(
                    INVOKESTATIC,
                    INT_VALUE_TYPE,
                    "box",
                    "(J)L${INT_VALUE_TYPE};",
                    false
                )
            }
            is DecimalLiteralExpr -> {
                val value = operand.lexeme.replace("_", "").toDouble()
                pushDouble(-value)
                mv.visitMethodInsn(
                    INVOKESTATIC,
                    DECIMAL_VALUE_TYPE,
                    "box",
                    "(D)L${DECIMAL_VALUE_TYPE};",
                    false
                )
            }
            else -> {
                // Runtime negation - compile operand and call negate helper
                compileExpr(operand)
                mv.visitMethodInsn(
                    INVOKESTATIC,
                    OPERATORS_TYPE,
                    "negate",
                    "(L${VALUE_TYPE};)L${VALUE_TYPE};",
                    false
                )
            }
        }
    }

    private fun compileNot(operand: Expr) {
        compileExpr(operand)
        mv.visitMethodInsn(
            INVOKESTATIC,
            OPERATORS_TYPE,
            "not",
            "(L${VALUE_TYPE};)L${BOOL_VALUE_TYPE};",
            false
        )
    }

    private fun compileBinaryExpr(expr: BinaryExpr) {
        when (expr.operator) {
            BinaryOperator.AND -> compileLogicalAnd(expr)
            BinaryOperator.OR -> compileLogicalOr(expr)
            else -> {
                // For most operators, evaluate both operands then call runtime helper
                compileExpr(expr.left)
                compileExpr(expr.right)
                val method = when (expr.operator) {
                    BinaryOperator.PLUS -> "add"
                    BinaryOperator.MINUS -> "subtract"
                    BinaryOperator.MULTIPLY -> "multiply"
                    BinaryOperator.DIVIDE -> "divide"
                    BinaryOperator.MODULO -> "modulo"
                    BinaryOperator.EQUAL -> "equal"
                    BinaryOperator.NOT_EQUAL -> "notEqual"
                    BinaryOperator.LESS -> "lessThan"
                    BinaryOperator.LESS_EQUAL -> "lessOrEqual"
                    BinaryOperator.GREATER -> "greaterThan"
                    BinaryOperator.GREATER_EQUAL -> "greaterOrEqual"
                    BinaryOperator.PIPELINE -> TODO("Pipeline not yet implemented")
                    BinaryOperator.COMPOSE -> TODO("Compose not yet implemented")
                    BinaryOperator.AND, BinaryOperator.OR -> error("Handled above")
                }
                mv.visitMethodInsn(
                    INVOKESTATIC,
                    OPERATORS_TYPE,
                    method,
                    "(L${VALUE_TYPE};L${VALUE_TYPE};)L${VALUE_TYPE};",
                    false
                )
            }
        }
    }

    private fun compileLogicalAnd(expr: BinaryExpr) {
        // Short-circuit: if left is falsy, result is false; otherwise evaluate right
        val falseLabel = Label()
        val endLabel = Label()

        compileExpr(expr.left)
        mv.visitMethodInsn(
            INVOKESTATIC,
            OPERATORS_TYPE,
            "isTruthy",
            "(L${VALUE_TYPE};)Z",
            false
        )
        mv.visitJumpInsn(IFEQ, falseLabel) // Jump to false if left is falsy

        // Left was truthy, evaluate right and check its truthiness
        compileExpr(expr.right)
        mv.visitMethodInsn(
            INVOKESTATIC,
            OPERATORS_TYPE,
            "isTruthy",
            "(L${VALUE_TYPE};)Z",
            false
        )
        mv.visitJumpInsn(IFEQ, falseLabel) // Jump to false if right is falsy

        // Both truthy, push true
        mv.visitFieldInsn(
            GETSTATIC,
            BOOL_VALUE_TYPE,
            "TRUE",
            "L${BOOL_VALUE_TYPE};"
        )
        mv.visitJumpInsn(GOTO, endLabel)

        mv.visitLabel(falseLabel)
        mv.visitFieldInsn(
            GETSTATIC,
            BOOL_VALUE_TYPE,
            "FALSE",
            "L${BOOL_VALUE_TYPE};"
        )

        mv.visitLabel(endLabel)
    }

    private fun compileLogicalOr(expr: BinaryExpr) {
        // Short-circuit: if left is truthy, result is true; otherwise evaluate right
        val trueLabel = Label()
        val endLabel = Label()

        compileExpr(expr.left)
        mv.visitMethodInsn(
            INVOKESTATIC,
            OPERATORS_TYPE,
            "isTruthy",
            "(L${VALUE_TYPE};)Z",
            false
        )
        mv.visitJumpInsn(IFNE, trueLabel) // Jump to true if left is truthy

        // Left was falsy, evaluate right and check its truthiness
        compileExpr(expr.right)
        mv.visitMethodInsn(
            INVOKESTATIC,
            OPERATORS_TYPE,
            "isTruthy",
            "(L${VALUE_TYPE};)Z",
            false
        )
        mv.visitJumpInsn(IFNE, trueLabel) // Jump to true if right is truthy

        // Both falsy, push false
        mv.visitFieldInsn(
            GETSTATIC,
            BOOL_VALUE_TYPE,
            "FALSE",
            "L${BOOL_VALUE_TYPE};"
        )
        mv.visitJumpInsn(GOTO, endLabel)

        mv.visitLabel(trueLabel)
        mv.visitFieldInsn(
            GETSTATIC,
            BOOL_VALUE_TYPE,
            "TRUE",
            "L${BOOL_VALUE_TYPE};"
        )

        mv.visitLabel(endLabel)
    }

    private fun compileIdentifier(expr: IdentifierExpr) {
        val binding = lookupBinding(expr.name)
            ?: throw CodegenException("Undefined variable: ${expr.name}")
        mv.visitVarInsn(ALOAD, binding.slot)
    }

    private fun compileAssignment(expr: AssignmentExpr) {
        val binding = lookupBinding(expr.target.name)
            ?: throw CodegenException("Undefined variable: ${expr.target.name}")
        if (!binding.isMutable) {
            throw CodegenException("Cannot assign to immutable variable: ${expr.target.name}")
        }
        compileExpr(expr.value)
        // Duplicate the value so assignment expression returns the assigned value
        mv.visitInsn(DUP)
        mv.visitVarInsn(ASTORE, binding.slot)
    }

    private fun compileLetStatement(expr: LetExpr) {
        // Compile the value first
        compileExpr(expr.value)

        // Handle the pattern (for now, just simple binding patterns)
        when (val pattern = expr.pattern) {
            is BindingPattern -> {
                val slot = allocateSlot()
                declareBinding(pattern.name, slot, expr.isMutable)
                mv.visitVarInsn(ASTORE, slot)
                // Let statement as expression returns nil
                pushNil()
            }
            else -> TODO("Pattern ${pattern::class.simpleName} not yet implemented in let")
        }
    }

    private fun compileLetExpr(expr: LetExpr) {
        // Same as statement version
        compileLetStatement(expr)
    }

    private fun compileBlock(expr: BlockExpr) {
        pushScope()

        if (expr.statements.isEmpty()) {
            pushNil()
        } else {
            expr.statements.forEachIndexed { index, statement ->
                compileStatement(statement)
                // Pop intermediate results, keep only last
                if (index < expr.statements.lastIndex) {
                    mv.visitInsn(POP)
                }
            }
        }

        popScope()
    }

    private fun compileListLiteral(expr: ListLiteralExpr) {
        // Create a new PersistentList builder
        mv.visitMethodInsn(
            INVOKESTATIC,
            "kotlinx/collections/immutable/ExtensionsKt",
            "persistentListOf",
            "()Lkotlinx/collections/immutable/PersistentList;",
            false
        )

        // Add each element
        for (element in expr.elements) {
            when (element) {
                is ExprElement -> {
                    compileExpr(element.expr)
                    mv.visitMethodInsn(
                        INVOKEINTERFACE,
                        "kotlinx/collections/immutable/PersistentList",
                        "add",
                        "(Ljava/lang/Object;)Lkotlinx/collections/immutable/PersistentList;",
                        true
                    )
                }
                is SpreadElement -> TODO("Spread in list literals not yet implemented")
            }
        }

        // Wrap in ListValue
        mv.visitMethodInsn(
            INVOKESTATIC,
            LIST_VALUE_TYPE,
            "box",
            "(Lkotlinx/collections/immutable/PersistentList;)L${LIST_VALUE_TYPE};",
            false
        )
    }

    private fun compileIndex(expr: IndexExpr) {
        compileExpr(expr.target)
        compileExpr(expr.index)
        mv.visitMethodInsn(
            INVOKESTATIC,
            OPERATORS_TYPE,
            "index",
            "(L${VALUE_TYPE};L${VALUE_TYPE};)L${VALUE_TYPE};",
            false
        )
    }

    private fun compileCall(expr: CallExpr) {
        // Check if it's a call to a built-in function
        val callee = expr.callee
        if (callee is IdentifierExpr && callee.name in BUILTIN_FUNCTIONS) {
            compileBuiltinCall(callee.name, expr.arguments)
        } else {
            // General function call - need to evaluate callee and invoke
            TODO("General function calls not yet implemented")
        }
    }

    private fun compileBuiltinCall(name: String, arguments: List<CallArgument>) {
        // Push arguments
        val plainArgs = arguments.filterIsInstance<ExprArgument>()
        for (arg in plainArgs) {
            compileExpr(arg.expr)
        }

        // Generate method signature based on arity
        val descriptor = when (plainArgs.size) {
            0 -> "()L${VALUE_TYPE};"
            1 -> "(L${VALUE_TYPE};)L${VALUE_TYPE};"
            2 -> "(L${VALUE_TYPE};L${VALUE_TYPE};)L${VALUE_TYPE};"
            3 -> "(L${VALUE_TYPE};L${VALUE_TYPE};L${VALUE_TYPE};)L${VALUE_TYPE};"
            else -> throw CodegenException("Builtin $name: too many arguments (${plainArgs.size})")
        }

        mv.visitMethodInsn(
            INVOKESTATIC,
            BUILTINS_TYPE,
            name,
            descriptor,
            false
        )
    }

    // Scope management

    private fun pushScope() {
        scopes.addLast(mutableMapOf())
    }

    private fun popScope() {
        scopes.removeLast()
    }

    private fun declareBinding(name: String, slot: Int, isMutable: Boolean) {
        scopes.last()[name] = LocalBinding(name, slot, isMutable)
    }

    private fun lookupBinding(name: String): LocalBinding? {
        for (scope in scopes.asReversed()) {
            scope[name]?.let { return it }
        }
        return null
    }

    private fun allocateSlot(): Int = nextSlot++

    // Helpers

    private fun pushLong(value: Long) {
        when (value) {
            0L -> mv.visitInsn(LCONST_0)
            1L -> mv.visitInsn(LCONST_1)
            else -> mv.visitLdcInsn(value)
        }
    }

    private fun pushDouble(value: Double) {
        when (value) {
            0.0 -> mv.visitInsn(DCONST_0)
            1.0 -> mv.visitInsn(DCONST_1)
            else -> mv.visitLdcInsn(value)
        }
    }

    private fun processEscapes(s: String): String {
        val result = StringBuilder()
        var i = 0
        while (i < s.length) {
            if (s[i] == '\\' && i + 1 < s.length) {
                when (s[i + 1]) {
                    'n' -> { result.append('\n'); i += 2 }
                    't' -> { result.append('\t'); i += 2 }
                    'r' -> { result.append('\r'); i += 2 }
                    'b' -> { result.append('\b'); i += 2 }
                    'f' -> { result.append('\u000C'); i += 2 }
                    '"' -> { result.append('"'); i += 2 }
                    '\\' -> { result.append('\\'); i += 2 }
                    else -> { result.append(s[i]); i += 1 }
                }
            } else {
                result.append(s[i])
                i += 1
            }
        }
        return result.toString()
    }

    companion object {
        const val VALUE_TYPE = "santa/runtime/value/Value"
        const val INT_VALUE_TYPE = "santa/runtime/value/IntValue"
        const val DECIMAL_VALUE_TYPE = "santa/runtime/value/DecimalValue"
        const val STRING_VALUE_TYPE = "santa/runtime/value/StringValue"
        const val BOOL_VALUE_TYPE = "santa/runtime/value/BoolValue"
        const val NIL_VALUE_TYPE = "santa/runtime/value/NilValue"
        const val LIST_VALUE_TYPE = "santa/runtime/value/ListValue"
        const val OPERATORS_TYPE = "santa/runtime/Operators"
        const val BUILTINS_TYPE = "santa/runtime/Builtins"

        val BUILTIN_FUNCTIONS = setOf(
            "size", "first", "rest", "push", "int", "type",
            "keys", "values", "abs",
        )
    }
}

/**
 * Exception thrown during code generation.
 */
class CodegenException(message: String) : RuntimeException(message)
