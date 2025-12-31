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
 * that returns a Value. Lambda functions compile to inner classes extending FunctionValue.
 */
object CodeGenerator {
    private val classCounter = AtomicLong(0)

    fun generate(program: Program): CompiledScript {
        val className = "santa/Script${classCounter.incrementAndGet()}"
        val generator = ClassGenerator(className)
        return generator.generate(program)
    }
}

/**
 * Information about a captured variable.
 */
private data class CaptureInfo(
    val name: String,
    val slot: Int,  // Slot in the enclosing scope
)

private class ClassGenerator(private val className: String) {
    private val cw = ClassWriter(ClassWriter.COMPUTE_FRAMES or ClassWriter.COMPUTE_MAXS)
    private val lambdaClasses = mutableMapOf<String, ByteArray>()
    private var lambdaCounter = 0

    fun generate(program: Program): CompiledScript {
        cw.visit(V21, ACC_PUBLIC or ACC_FINAL, className, null, "java/lang/Object", null)

        generateExecuteMethod(program)

        cw.visitEnd()
        return CompiledScript(
            className.replace('/', '.'),
            cw.toByteArray(),
            lambdaClasses.mapKeys { it.key.replace('/', '.') }
        )
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

        val exprGen = ExpressionGenerator(mv, this::generateLambdaClass)

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

    /**
     * Generate a lambda class and return its name.
     */
    fun generateLambdaClass(
        params: List<Param>,
        body: Expr,
        captures: List<CaptureInfo>,
    ): String {
        val lambdaClassName = "${className}\$Lambda${++lambdaCounter}"
        val lcw = ClassWriter(ClassWriter.COMPUTE_FRAMES or ClassWriter.COMPUTE_MAXS)

        lcw.visit(
            V21,
            ACC_PUBLIC or ACC_FINAL,
            lambdaClassName,
            null,
            FUNCTION_VALUE_TYPE,
            null
        )

        // Generate fields for captured variables
        for (capture in captures) {
            lcw.visitField(
                ACC_PRIVATE or ACC_FINAL,
                capture.name,
                "L${VALUE_TYPE};",
                null,
                null
            ).visitEnd()
        }

        // Generate constructor
        generateLambdaConstructor(lcw, lambdaClassName, params, captures)

        // Generate invoke method
        generateLambdaInvoke(lcw, lambdaClassName, params, body, captures)

        lcw.visitEnd()
        lambdaClasses[lambdaClassName] = lcw.toByteArray()
        return lambdaClassName
    }

    private fun generateLambdaConstructor(
        lcw: ClassWriter,
        lambdaClassName: String,
        params: List<Param>,
        captures: List<CaptureInfo>,
    ) {
        // Constructor signature: (capture1, capture2, ...) -> void
        val desc = buildString {
            append('(')
            repeat(captures.size) { append("L${VALUE_TYPE};") }
            append(")V")
        }

        val mv = lcw.visitMethod(ACC_PUBLIC, "<init>", desc, null, null)
        mv.visitCode()

        // Call super(arity)
        mv.visitVarInsn(ALOAD, 0)
        val arity = params.count { it is NamedParam }
        pushInt(mv, arity)
        mv.visitMethodInsn(INVOKESPECIAL, FUNCTION_VALUE_TYPE, "<init>", "(I)V", false)

        // Store captured values in fields
        for ((index, capture) in captures.withIndex()) {
            mv.visitVarInsn(ALOAD, 0)  // this
            mv.visitVarInsn(ALOAD, index + 1)  // captured value
            mv.visitFieldInsn(PUTFIELD, lambdaClassName, capture.name, "L${VALUE_TYPE};")
        }

        mv.visitInsn(RETURN)
        mv.visitMaxs(-1, -1)
        mv.visitEnd()
    }

    private fun generateLambdaInvoke(
        lcw: ClassWriter,
        lambdaClassName: String,
        params: List<Param>,
        body: Expr,
        captures: List<CaptureInfo>,
    ) {
        val mv = lcw.visitMethod(
            ACC_PUBLIC,
            "invoke",
            "(Ljava/util/List;)L${VALUE_TYPE};",
            null,
            null
        )
        mv.visitCode()

        // Create expression generator for lambda body
        // We need a special generator that knows about:
        // 1. Captured variables (accessed via this.field)
        // 2. Parameters (extracted from args list)
        val exprGen = LambdaExpressionGenerator(mv, lambdaClassName, params, captures, this::generateLambdaClass)

        // Compile the body
        exprGen.compileExpr(body)

        mv.visitInsn(ARETURN)
        mv.visitMaxs(-1, -1)
        mv.visitEnd()
    }

    companion object {
        const val VALUE_TYPE = "santa/runtime/value/Value"
        const val FUNCTION_VALUE_TYPE = "santa/runtime/value/FunctionValue"
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
 * Type alias for lambda class generator function.
 */
private typealias LambdaGenerator = (params: List<Param>, body: Expr, captures: List<CaptureInfo>) -> String

/**
 * Helper to push int constant onto stack.
 */
private fun pushInt(mv: MethodVisitor, value: Int) {
    when (value) {
        -1 -> mv.visitInsn(ICONST_M1)
        0 -> mv.visitInsn(ICONST_0)
        1 -> mv.visitInsn(ICONST_1)
        2 -> mv.visitInsn(ICONST_2)
        3 -> mv.visitInsn(ICONST_3)
        4 -> mv.visitInsn(ICONST_4)
        5 -> mv.visitInsn(ICONST_5)
        in Byte.MIN_VALUE..Byte.MAX_VALUE -> mv.visitIntInsn(BIPUSH, value)
        in Short.MIN_VALUE..Short.MAX_VALUE -> mv.visitIntInsn(SIPUSH, value)
        else -> mv.visitLdcInsn(value)
    }
}

/**
 * Generates bytecode for expressions.
 *
 * Each expression leaves exactly one Value on the operand stack.
 */
private open class ExpressionGenerator(
    protected val mv: MethodVisitor,
    private val lambdaGenerator: LambdaGenerator,
) {
    // Scope stack: each scope is a map of name -> LocalBinding
    protected val scopes = ArrayDeque<MutableMap<String, LocalBinding>>()
    protected var nextSlot = 0

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

    open fun compileExpr(expr: Expr) {
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
            is FunctionExpr -> compileFunctionExpr(expr)
            is BlockExpr -> compileBlock(expr)
            is IfExpr -> compileIfExpr(expr)
            is MatchExpr -> compileMatchExpr(expr)
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

    private fun compileIfExpr(expr: IfExpr) {
        val elseLabel = Label()
        val endLabel = Label()

        // Evaluate condition based on type
        when (val condition = expr.condition) {
            is ExprCondition -> {
                compileExpr(condition.expr)
                mv.visitMethodInsn(
                    INVOKESTATIC,
                    OPERATORS_TYPE,
                    "isTruthy",
                    "(L${VALUE_TYPE};)Z",
                    false
                )
                mv.visitJumpInsn(IFEQ, elseLabel) // Jump to else if falsy

                // Then branch
                compileExpr(expr.thenBranch)
                mv.visitJumpInsn(GOTO, endLabel)

                // Else branch (or nil if no else)
                mv.visitLabel(elseLabel)
                if (expr.elseBranch != null) {
                    compileExpr(expr.elseBranch)
                } else {
                    pushNil()
                }

                mv.visitLabel(endLabel)
            }
            is LetCondition -> {
                // Evaluate value and check truthiness
                compileExpr(condition.value)
                mv.visitInsn(DUP) // Keep value for later binding

                mv.visitMethodInsn(
                    INVOKESTATIC,
                    OPERATORS_TYPE,
                    "isTruthy",
                    "(L${VALUE_TYPE};)Z",
                    false
                )
                mv.visitJumpInsn(IFEQ, elseLabel) // Jump to else if falsy

                // Value was truthy - bind it in then branch scope
                pushScope()
                when (val pattern = condition.pattern) {
                    is BindingPattern -> {
                        val slot = allocateSlot()
                        declareBinding(pattern.name, slot, isMutable = false)
                        mv.visitVarInsn(ASTORE, slot)
                    }
                    else -> TODO("Pattern ${pattern::class.simpleName} not yet implemented in if-let")
                }

                // Then branch
                compileExpr(expr.thenBranch)
                popScope()
                mv.visitJumpInsn(GOTO, endLabel)

                // Else branch (or nil if no else) - pop the unused value first
                mv.visitLabel(elseLabel)
                mv.visitInsn(POP) // Pop the duplicated value that wasn't bound
                if (expr.elseBranch != null) {
                    compileExpr(expr.elseBranch)
                } else {
                    pushNil()
                }

                mv.visitLabel(endLabel)
            }
        }
    }

    private fun compileMatchExpr(expr: MatchExpr) {
        val endLabel = Label()

        // Compile subject once and store in a local
        compileExpr(expr.subject)
        val subjectSlot = allocateSlot()
        mv.visitVarInsn(ASTORE, subjectSlot)

        for (arm in expr.arms) {
            val nextArmLabel = Label()

            // Each arm gets its own scope for pattern bindings
            pushScope()

            // Compile pattern matching - uses subject from local slot
            compilePatternMatch(arm.pattern, subjectSlot, nextArmLabel)

            // If there's a guard, evaluate it
            if (arm.guard != null) {
                compileExpr(arm.guard)
                mv.visitMethodInsn(
                    INVOKESTATIC,
                    OPERATORS_TYPE,
                    "isTruthy",
                    "(L${VALUE_TYPE};)Z",
                    false
                )
                mv.visitJumpInsn(IFEQ, nextArmLabel) // Jump if guard is false
            }

            // Pattern matched (and guard passed if present) - compile body
            compileExpr(arm.body)

            // Pop scope BEFORE generating GOTO - we're done with this arm's bindings
            popScope()

            mv.visitJumpInsn(GOTO, endLabel)

            // Label for next arm (or no-match case)
            mv.visitLabel(nextArmLabel)
        }

        // No pattern matched - return nil
        pushNil()

        mv.visitLabel(endLabel)
    }

    /**
     * Compiles pattern matching code.
     * Subject is in the local slot, not on stack.
     * Jumps to failLabel if pattern doesn't match.
     * Binds pattern variables in the current scope.
     */
    private fun compilePatternMatch(pattern: Pattern, subjectSlot: Int, failLabel: Label) {
        when (pattern) {
            is WildcardPattern -> {
                // Wildcard always matches, nothing to do
            }
            is BindingPattern -> {
                // Binding always matches and captures the value
                mv.visitVarInsn(ALOAD, subjectSlot)
                val slot = allocateSlot()
                declareBinding(pattern.name, slot, isMutable = false)
                mv.visitVarInsn(ASTORE, slot)
            }
            is LiteralPattern -> {
                // Compare subject with literal
                mv.visitVarInsn(ALOAD, subjectSlot)
                compileExpr(pattern.literal)
                mv.visitMethodInsn(
                    INVOKESTATIC,
                    OPERATORS_TYPE,
                    "equal",
                    "(L${VALUE_TYPE};L${VALUE_TYPE};)L${VALUE_TYPE};",
                    false
                )
                mv.visitMethodInsn(
                    INVOKESTATIC,
                    OPERATORS_TYPE,
                    "isTruthy",
                    "(L${VALUE_TYPE};)Z",
                    false
                )
                mv.visitJumpInsn(IFEQ, failLabel) // Jump to next arm if not equal
            }
            is ListPattern -> {
                compileListPatternMatch(pattern, subjectSlot, failLabel)
            }
            is RestPattern -> {
                // Rest pattern at top level just binds remaining (whole subject)
                if (pattern.name != null) {
                    mv.visitVarInsn(ALOAD, subjectSlot)
                    val slot = allocateSlot()
                    declareBinding(pattern.name, slot, isMutable = false)
                    mv.visitVarInsn(ASTORE, slot)
                }
            }
            is RangePattern -> {
                TODO("Range patterns not yet implemented")
            }
        }
    }

    private fun compileListPatternMatch(pattern: ListPattern, subjectSlot: Int, failLabel: Label) {
        // Check if subject is a list
        mv.visitVarInsn(ALOAD, subjectSlot)
        mv.visitTypeInsn(INSTANCEOF, LIST_VALUE_TYPE)
        mv.visitJumpInsn(IFEQ, failLabel) // Not a list, fail

        // Load and cast to ListValue, store in a new slot for element access
        mv.visitVarInsn(ALOAD, subjectSlot)
        mv.visitTypeInsn(CHECKCAST, LIST_VALUE_TYPE)
        val listSlot = allocateSlot()
        mv.visitVarInsn(ASTORE, listSlot)

        // Check for rest pattern
        val hasRest = pattern.elements.any { it is RestPattern }
        val nonRestCount = pattern.elements.count { it !is RestPattern }

        // Get size once and store it
        mv.visitVarInsn(ALOAD, listSlot)
        mv.visitMethodInsn(
            INVOKEVIRTUAL,
            LIST_VALUE_TYPE,
            "size",
            "()I",
            false
        )
        val sizeSlot = allocateSlot()
        mv.visitVarInsn(ISTORE, sizeSlot)

        if (hasRest) {
            // With rest: need at least (nonRestCount) elements
            mv.visitVarInsn(ILOAD, sizeSlot)
            pushIntValue(nonRestCount)
            mv.visitJumpInsn(IF_ICMPLT, failLabel) // Fail if size < nonRestCount
        } else {
            // Without rest: need exactly pattern.elements.size elements
            mv.visitVarInsn(ILOAD, sizeSlot)
            pushIntValue(pattern.elements.size)
            mv.visitJumpInsn(IF_ICMPNE, failLabel) // Fail if size != pattern size
        }

        // Match each element
        var elementIndex = 0
        for (element in pattern.elements) {
            when (element) {
                is RestPattern -> {
                    // Capture remaining elements as a list using slice(startIndex, size)
                    if (element.name != null) {
                        mv.visitVarInsn(ALOAD, listSlot)
                        pushIntValue(elementIndex)
                        mv.visitVarInsn(ILOAD, sizeSlot)
                        mv.visitMethodInsn(
                            INVOKEVIRTUAL,
                            LIST_VALUE_TYPE,
                            "slice",
                            "(II)L${LIST_VALUE_TYPE};",
                            false
                        )
                        val slot = allocateSlot()
                        declareBinding(element.name, slot, isMutable = false)
                        mv.visitVarInsn(ASTORE, slot)
                    }
                    // Rest consumes all remaining elements, stop here
                    break
                }
                is BindingPattern -> {
                    // Get element at index and bind
                    mv.visitVarInsn(ALOAD, listSlot)
                    pushIntValue(elementIndex)
                    mv.visitMethodInsn(
                        INVOKEVIRTUAL,
                        LIST_VALUE_TYPE,
                        "get",
                        "(I)L${VALUE_TYPE};",
                        false
                    )
                    val slot = allocateSlot()
                    declareBinding(element.name, slot, isMutable = false)
                    mv.visitVarInsn(ASTORE, slot)
                    elementIndex++
                }
                is WildcardPattern -> {
                    // Skip this element
                    elementIndex++
                }
                is LiteralPattern -> {
                    // Get element and compare with literal
                    mv.visitVarInsn(ALOAD, listSlot)
                    pushIntValue(elementIndex)
                    mv.visitMethodInsn(
                        INVOKEVIRTUAL,
                        LIST_VALUE_TYPE,
                        "get",
                        "(I)L${VALUE_TYPE};",
                        false
                    )
                    compileExpr(element.literal)
                    mv.visitMethodInsn(
                        INVOKESTATIC,
                        OPERATORS_TYPE,
                        "equal",
                        "(L${VALUE_TYPE};L${VALUE_TYPE};)L${VALUE_TYPE};",
                        false
                    )
                    mv.visitMethodInsn(
                        INVOKESTATIC,
                        OPERATORS_TYPE,
                        "isTruthy",
                        "(L${VALUE_TYPE};)Z",
                        false
                    )
                    mv.visitJumpInsn(IFEQ, failLabel)
                    elementIndex++
                }
                is ListPattern -> {
                    // Nested list pattern - get element and recursively match
                    mv.visitVarInsn(ALOAD, listSlot)
                    pushIntValue(elementIndex)
                    mv.visitMethodInsn(
                        INVOKEVIRTUAL,
                        LIST_VALUE_TYPE,
                        "get",
                        "(I)L${VALUE_TYPE};",
                        false
                    )
                    // Store nested element in a temp slot
                    val nestedSlot = allocateSlot()
                    mv.visitVarInsn(ASTORE, nestedSlot)
                    compileListPatternMatch(element, nestedSlot, failLabel)
                    elementIndex++
                }
                is RangePattern -> TODO("Range patterns in lists not yet implemented")
            }
        }
    }

    private fun pushIntValue(value: Int) = pushInt(mv, value)

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

    private fun compileFunctionExpr(expr: FunctionExpr) {
        // Find captured variables (free variables in body that are bound in enclosing scope)
        val freeVars = findFreeVariables(expr.body, expr.params)
        val captures = freeVars.mapNotNull { name ->
            lookupBinding(name)?.let { binding ->
                CaptureInfo(name, binding.slot)
            }
        }

        // Generate lambda class
        val lambdaClassName = lambdaGenerator(expr.params, expr.body, captures)

        // Instantiate the lambda: new LambdaClass(capture1, capture2, ...)
        mv.visitTypeInsn(NEW, lambdaClassName)
        mv.visitInsn(DUP)

        // Push captured values
        for (capture in captures) {
            mv.visitVarInsn(ALOAD, capture.slot)
        }

        // Constructor descriptor
        val constructorDesc = buildString {
            append('(')
            repeat(captures.size) { append("L${VALUE_TYPE};") }
            append(")V")
        }

        mv.visitMethodInsn(INVOKESPECIAL, lambdaClassName, "<init>", constructorDesc, false)
    }

    /**
     * Find free variables in an expression that are not bound by the given parameters.
     */
    private fun findFreeVariables(expr: Expr, params: List<Param>): Set<String> {
        val paramNames = params.filterIsInstance<NamedParam>().map { it.name }.toSet()
        val freeVars = mutableSetOf<String>()

        fun visit(e: Expr, bound: Set<String>) {
            when (e) {
                is IdentifierExpr -> {
                    if (e.name !in bound && e.name !in BUILTIN_FUNCTIONS) {
                        freeVars.add(e.name)
                    }
                }
                is BinaryExpr -> { visit(e.left, bound); visit(e.right, bound) }
                is UnaryExpr -> visit(e.expr, bound)
                is LetExpr -> {
                    visit(e.value, bound)
                    val newBound = when (val p = e.pattern) {
                        is BindingPattern -> bound + p.name
                        else -> bound
                    }
                    // Let doesn't have a continuation expression in AST, handled by block
                }
                is BlockExpr -> {
                    var currentBound = bound
                    for (stmt in e.statements) {
                        when (stmt) {
                            is ExprStatement -> visit(stmt.expr, currentBound)
                            is LetExpr -> {
                                visit(stmt.value, currentBound)
                                val p = stmt.pattern
                                if (p is BindingPattern) {
                                    currentBound = currentBound + p.name
                                }
                            }
                            is ReturnExpr -> stmt.value?.let { visit(it, currentBound) }
                            is BreakExpr -> stmt.value?.let { visit(it, currentBound) }
                        }
                    }
                }
                is CallExpr -> {
                    visit(e.callee, bound)
                    for (arg in e.arguments) {
                        visit(arg.expr, bound)
                    }
                }
                is IfExpr -> {
                    when (val cond = e.condition) {
                        is ExprCondition -> visit(cond.expr, bound)
                        is LetCondition -> {
                            visit(cond.value, bound)
                            // Let binding in condition binds in then branch
                        }
                    }
                    visit(e.thenBranch, bound)
                    e.elseBranch?.let { visit(it, bound) }
                }
                is MatchExpr -> {
                    visit(e.subject, bound)
                    for (arm in e.arms) {
                        val patternBound = collectPatternBindings(arm.pattern)
                        arm.guard?.let { visit(it, bound + patternBound) }
                        visit(arm.body, bound + patternBound)
                    }
                }
                is FunctionExpr -> {
                    val fnParams = e.params.filterIsInstance<NamedParam>().map { it.name }.toSet()
                    visit(e.body, bound + fnParams)
                }
                is IndexExpr -> { visit(e.target, bound); visit(e.index, bound) }
                is AssignmentExpr -> visit(e.value, bound)
                is ListLiteralExpr -> e.elements.forEach { elem ->
                    when (elem) {
                        is ExprElement -> visit(elem.expr, bound)
                        is SpreadElement -> visit(elem.expr, bound)
                    }
                }
                // Terminals that don't contain expressions
                is IntLiteralExpr, is DecimalLiteralExpr, is StringLiteralExpr,
                is BoolLiteralExpr, is NilLiteralExpr, is PlaceholderExpr -> { }
                is SetLiteralExpr, is DictLiteralExpr, is RangeExpr, is InfixCallExpr -> { }
                is ReturnExpr -> e.value?.let { visit(it, bound) }
                is BreakExpr -> e.value?.let { visit(it, bound) }
            }
        }

        visit(expr, paramNames)
        return freeVars
    }

    private fun collectPatternBindings(pattern: Pattern): Set<String> {
        return when (pattern) {
            is BindingPattern -> setOf(pattern.name)
            is ListPattern -> pattern.elements.flatMap { collectPatternBindings(it) }.toSet()
            is RestPattern -> if (pattern.name != null) setOf(pattern.name) else emptySet()
            is WildcardPattern, is LiteralPattern, is RangePattern -> emptySet()
        }
    }

    private fun compileCall(expr: CallExpr) {
        // Check if it's a call to a built-in function
        val callee = expr.callee
        if (callee is IdentifierExpr && callee.name in BUILTIN_FUNCTIONS) {
            compileBuiltinCall(callee.name, expr.arguments)
        } else {
            // General function call - evaluate callee (should be FunctionValue)
            compileExpr(callee)

            // Build argument list
            val plainArgs = expr.arguments.filterIsInstance<ExprArgument>()

            // Create List<Value> for arguments: Arrays.asList(arg1, arg2, ...)
            pushIntValue(plainArgs.size)
            mv.visitTypeInsn(ANEWARRAY, VALUE_TYPE)
            for ((index, arg) in plainArgs.withIndex()) {
                mv.visitInsn(DUP)
                pushIntValue(index)
                compileExpr(arg.expr)
                mv.visitInsn(AASTORE)
            }
            mv.visitMethodInsn(
                INVOKESTATIC,
                "java/util/Arrays",
                "asList",
                "([Ljava/lang/Object;)Ljava/util/List;",
                false
            )

            // Cast callee to FunctionValue and invoke
            mv.visitInsn(SWAP) // Move args list below callee
            mv.visitTypeInsn(CHECKCAST, FUNCTION_VALUE_TYPE)
            mv.visitInsn(SWAP) // Move callee below args list again
            mv.visitMethodInsn(
                INVOKEVIRTUAL,
                FUNCTION_VALUE_TYPE,
                "invoke",
                "(Ljava/util/List;)L${VALUE_TYPE};",
                false
            )
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

    protected fun pushScope() {
        scopes.addLast(mutableMapOf())
    }

    protected fun popScope() {
        scopes.removeLast()
    }

    protected fun declareBinding(name: String, slot: Int, isMutable: Boolean) {
        scopes.last()[name] = LocalBinding(name, slot, isMutable)
    }

    protected fun lookupBinding(name: String): LocalBinding? {
        for (scope in scopes.asReversed()) {
            scope[name]?.let { return it }
        }
        return null
    }

    protected fun allocateSlot(): Int = nextSlot++

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
        const val FUNCTION_VALUE_TYPE = "santa/runtime/value/FunctionValue"
        const val OPERATORS_TYPE = "santa/runtime/Operators"
        const val BUILTINS_TYPE = "santa/runtime/Builtins"

        val BUILTIN_FUNCTIONS = setOf(
            "size", "first", "rest", "push", "int", "type",
            "keys", "values", "abs",
        )
    }
}

/**
 * Generates bytecode for expressions inside lambda bodies.
 * Handles parameter access and captured variable access.
 */
private class LambdaExpressionGenerator(
    mv: MethodVisitor,
    private val lambdaClassName: String,
    private val params: List<Param>,
    private val captures: List<CaptureInfo>,
    lambdaGenerator: LambdaGenerator,
) : ExpressionGenerator(mv, lambdaGenerator) {

    init {
        // Clear the default scope and set up lambda-specific bindings
        scopes.clear()
        pushScope()

        // Slot 0 is 'this', slot 1 is 'args' list
        nextSlot = 2

        // Bind parameters: extract from args list
        val namedParams = params.filterIsInstance<NamedParam>()
        for ((index, param) in namedParams.withIndex()) {
            // args.get(index) -> store in local slot
            mv.visitVarInsn(ALOAD, 1) // args
            pushInt(mv, index)
            mv.visitMethodInsn(
                INVOKEINTERFACE,
                "java/util/List",
                "get",
                "(I)Ljava/lang/Object;",
                true
            )
            mv.visitTypeInsn(CHECKCAST, VALUE_TYPE)
            val slot = nextSlot++
            mv.visitVarInsn(ASTORE, slot)
            declareBinding(param.name, slot, isMutable = false)
        }
    }

    override fun compileExpr(expr: Expr) {
        when (expr) {
            is IdentifierExpr -> {
                // Check if it's a captured variable
                val capture = captures.find { it.name == expr.name }
                if (capture != null) {
                    // Load from this.fieldName
                    mv.visitVarInsn(ALOAD, 0) // this
                    mv.visitFieldInsn(
                        GETFIELD,
                        lambdaClassName,
                        capture.name,
                        "L${VALUE_TYPE};"
                    )
                } else {
                    // Fall back to local variable lookup
                    super.compileExpr(expr)
                }
            }
            else -> super.compileExpr(expr)
        }
    }
}

/**
 * Exception thrown during code generation.
 */
class CodegenException(message: String) : RuntimeException(message)
