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
    val isSelfRef: Boolean = false,  // True if this is a self-reference for recursion
)

/**
 * Custom ClassWriter that handles our lambda classes without loading them.
 *
 * When ASM computes stack frames, it needs to find common superclasses.
 * Since our lambda classes haven't been loaded into the JVM yet,
 * we need to tell ASM that they all extend FunctionValue.
 */
private class SantaClassWriter(private val scriptPrefix: String) : ClassWriter(COMPUTE_FRAMES or COMPUTE_MAXS) {
    companion object {
        private const val FUNCTION_VALUE = "santa/runtime/value/FunctionValue"
    }

    override fun getCommonSuperClass(type1: String, type2: String): String {
        // If either type is one of our lambda classes, we know it extends FunctionValue
        val isLambda1 = type1.startsWith(scriptPrefix) && type1.contains("\$Lambda")
        val isLambda2 = type2.startsWith(scriptPrefix) && type2.contains("\$Lambda")

        return when {
            // Both are our lambdas - common superclass is FunctionValue
            isLambda1 && isLambda2 -> FUNCTION_VALUE
            // One is our lambda, one is FunctionValue - common is FunctionValue
            isLambda1 && type2 == FUNCTION_VALUE -> FUNCTION_VALUE
            isLambda2 && type1 == FUNCTION_VALUE -> FUNCTION_VALUE
            // One is our lambda - the other must be a superclass of FunctionValue
            isLambda1 || isLambda2 -> {
                // FunctionValue extends Object, so Object is the common superclass
                "java/lang/Object"
            }
            // Neither is our lambda - use default implementation
            else -> super.getCommonSuperClass(type1, type2)
        }
    }
}

private class ClassGenerator(private val className: String) {
    private val cw = SantaClassWriter(className.substringBefore('$'))
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
            // Group items by type
            val sections = program.items.filterIsInstance<Section>()
            val inputSection = sections.find { it.name == "input" }
            val partSections = sections.filter { it.name in setOf("part_one", "part_two") }
            val hasParts = partSections.isNotEmpty()

            // Process all items in order, binding sections as variables
            program.items.forEachIndexed { index, item ->
                when (item) {
                    is StatementItem -> {
                        exprGen.compileStatement(item.statement)
                        // Pop statement result unless it's the last item (and no part sections)
                        if (!hasParts && index < program.items.lastIndex) {
                            mv.visitInsn(POP)
                        } else if (hasParts) {
                            mv.visitInsn(POP)
                        }
                    }
                    is Section -> {
                        // Compile the section expression
                        exprGen.compileExpr(item.expr)

                        // If this is a known section, bind it as a variable
                        if (item.name in SECTION_NAMES) {
                            // Store in a local slot and register binding
                            val slot = exprGen.allocateSlot()
                            exprGen.declareBinding(item.name, slot, isMutable = false)
                            mv.visitInsn(DUP)  // Keep value on stack
                            mv.visitVarInsn(ASTORE, slot)
                        }

                        // Pop intermediate results
                        if (index < program.items.lastIndex) {
                            mv.visitInsn(POP)
                        }
                    }
                }
            }

            // If there are no items, push nil
            if (program.items.isEmpty()) {
                exprGen.pushNil()
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
        val lcw = SantaClassWriter(className)

        lcw.visit(
            V21,
            ACC_PUBLIC or ACC_FINAL,
            lambdaClassName,
            null,
            FUNCTION_VALUE_TYPE,
            null
        )

        // Generate fields for captured variables (excluding self-references which use base class field)
        val nonSelfCaptures = captures.filter { !it.isSelfRef }
        for (capture in nonSelfCaptures) {
            lcw.visitField(
                ACC_PRIVATE or ACC_FINAL,
                capture.name,
                "L${VALUE_TYPE};",
                null,
                null
            ).visitEnd()
        }

        // Generate constructor
        generateLambdaConstructor(lcw, lambdaClassName, params, nonSelfCaptures)

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

        // Check if this function is tail-recursive
        val selfRefCapture = captures.find { it.isSelfRef }
        val tailRecursionInfo = selfRefCapture?.let {
            TailCallAnalyzer.analyzeTailRecursion(it.name, body)
        }

        if (tailRecursionInfo != null) {
            // Generate tail-recursive loop-based code
            generateTailRecursiveInvoke(mv, lambdaClassName, params, body, captures, tailRecursionInfo)
        } else {
            // Generate normal invoke code
            generateNormalInvoke(mv, lambdaClassName, params, body, captures)
        }

        mv.visitMaxs(-1, -1)
        mv.visitEnd()
    }

    private fun generateNormalInvoke(
        mv: MethodVisitor,
        lambdaClassName: String,
        params: List<Param>,
        body: Expr,
        captures: List<CaptureInfo>,
    ) {
        // Create expression generator for lambda body
        val exprGen = LambdaExpressionGenerator(mv, lambdaClassName, params, captures, this::generateLambdaClass)

        // Wrap body in try-catch for ReturnException to support early return
        val tryStart = Label()
        val tryEnd = Label()
        val catchHandler = Label()

        mv.visitTryCatchBlock(tryStart, tryEnd, catchHandler, RETURN_EXCEPTION_TYPE)

        mv.visitLabel(tryStart)
        // Compile the body
        exprGen.compileExpr(body)
        mv.visitLabel(tryEnd)
        // Normal exit - return the body's value
        mv.visitInsn(ARETURN)

        // Catch ReturnException and extract its value, then return it
        mv.visitLabel(catchHandler)
        // Stack has ReturnException, get its value field
        mv.visitFieldInsn(GETFIELD, RETURN_EXCEPTION_TYPE, "value", "L${VALUE_TYPE};")
        mv.visitInsn(ARETURN)
    }

    private fun generateTailRecursiveInvoke(
        mv: MethodVisitor,
        lambdaClassName: String,
        params: List<Param>,
        body: Expr,
        captures: List<CaptureInfo>,
        tailRecursionInfo: TailRecursionInfo,
    ) {
        // Create specialized expression generator that handles tail calls as loop iterations
        val loopStartLabel = Label()
        val exprGen = TailRecursiveLambdaExpressionGenerator(
            mv, lambdaClassName, params, captures, this::generateLambdaClass,
            tailRecursionInfo, loopStartLabel
        )

        // Wrap body in try-catch for ReturnException to support early return
        val tryStart = Label()
        val tryEnd = Label()
        val catchHandler = Label()

        mv.visitTryCatchBlock(tryStart, tryEnd, catchHandler, RETURN_EXCEPTION_TYPE)

        // Loop start label - tail calls will jump back here
        mv.visitLabel(loopStartLabel)

        mv.visitLabel(tryStart)
        // Compile the body - tail calls will update params and goto loopStartLabel
        exprGen.compileExpr(body)
        mv.visitLabel(tryEnd)
        // Normal exit - return the body's value (for non-tail-call return paths)
        mv.visitInsn(ARETURN)

        // Catch ReturnException and extract its value, then return it
        mv.visitLabel(catchHandler)
        mv.visitFieldInsn(GETFIELD, RETURN_EXCEPTION_TYPE, "value", "L${VALUE_TYPE};")
        mv.visitInsn(ARETURN)
    }

    companion object {
        const val VALUE_TYPE = "santa/runtime/value/Value"
        const val FUNCTION_VALUE_TYPE = "santa/runtime/value/FunctionValue"
        const val RETURN_EXCEPTION_TYPE = "santa/runtime/value/ReturnException"
        const val BREAK_EXCEPTION_TYPE = "santa/runtime/value/BreakException"
        val SECTION_NAMES = setOf("input", "part_one", "part_two")
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

    // Tracks the name of a self-referential binding being compiled (for recursive functions)
    protected var currentSelfRefName: String? = null

    init {
        pushScope()
    }

    fun compileStatement(statement: Statement) {
        when (statement) {
            is ExprStatement -> compileExpr(statement.expr)
            is LetExpr -> compileLetStatement(statement)
            is ReturnExpr -> compileReturnExpr(statement)
            is BreakExpr -> compileBreakExpr(statement)
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
            is PlaceholderExpr -> throw IllegalStateException(
                "PlaceholderExpr should have been desugared before codegen"
            )
            is OperatorExpr -> throw IllegalStateException(
                "OperatorExpr should have been desugared before codegen"
            )
            is ListLiteralExpr -> compileListLiteral(expr)
            is SetLiteralExpr -> compileSetLiteral(expr)
            is DictLiteralExpr -> compileDictLiteral(expr)
            is AssignmentExpr -> compileAssignment(expr)
            is LetExpr -> compileLetExpr(expr)
            is ReturnExpr -> compileReturnExpr(expr)
            is BreakExpr -> compileBreakExpr(expr)
            is RangeExpr -> compileRangeExpr(expr)
            is InfixCallExpr -> compileInfixCall(expr)
            is CallExpr -> compileCall(expr)
            is IndexExpr -> compileIndex(expr)
            is FunctionExpr -> compileFunctionExpr(expr)
            is BlockExpr -> compileBlock(expr)
            is IfExpr -> compileIfExpr(expr)
            is MatchExpr -> compileMatchExpr(expr)
            is TestBlockExpr -> {
                // Test blocks are not executed during normal compilation;
                // they are only validated during test mode in the CLI.
                // Just push nil as a placeholder value.
                pushNil()
            }
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
                    BinaryOperator.PIPELINE -> "pipeline"
                    BinaryOperator.COMPOSE -> "compose"
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
        // Check local bindings first - allows shadowing of builtins
        val binding = lookupBinding(expr.name)
        if (binding != null) {
            mv.visitVarInsn(ALOAD, binding.slot)
            return
        }

        // Check if it's a builtin function reference (for pipeline/compose)
        if (expr.name in BUILTIN_FUNCTIONS) {
            // Load a BuiltinFunctionValue wrapper for this builtin
            mv.visitLdcInsn(expr.name)
            mv.visitMethodInsn(
                INVOKESTATIC,
                "santa/runtime/BuiltinFunctionValue",
                "get",
                "(Ljava/lang/String;)Lsanta/runtime/BuiltinFunctionValue;",
                false
            )
            return
        }

        throw CodegenException("Undefined variable: ${expr.name}")
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
        // Handle the pattern (for now, just simple binding patterns)
        when (val pattern = expr.pattern) {
            is BindingPattern -> {
                val slot = allocateSlot()

                // Check if this is a self-referential function binding.
                // This can be a direct function (`let f = |x| ... f(...) ...`)
                // or a call with a lambda that references the binding
                // (`let f = memoize(|x| ... f(...) ...)`).
                val isSelfReferential = isValueSelfReferential(expr.value, pattern.name)

                if (isSelfReferential) {
                    // For self-referential functions, we need to:
                    // 1. Create a placeholder (nil) in the slot first
                    // 2. Declare the binding
                    // 3. Compile the expression (which will capture the slot in any nested lambdas)
                    // 4. Store the result in the slot
                    // 5. For direct functions, update the closure's self-reference

                    // Push nil as placeholder and store
                    pushNil()
                    mv.visitVarInsn(ASTORE, slot)

                    // Declare binding so functions can find it during compilation
                    declareBinding(pattern.name, slot, expr.isMutable)

                    // Set context for self-reference and compile the expression
                    currentSelfRefName = pattern.name
                    compileExpr(expr.value)
                    currentSelfRefName = null

                    // For both direct and wrapped functions, we need to call setSelfRef.
                    // For wrapped functions (like memoize), setSelfRef is forwarded to the
                    // inner lambda, allowing recursive calls to go through the wrapper.

                    // Cast to FunctionValue (memoize etc. return FunctionValue subclasses)
                    mv.visitTypeInsn(CHECKCAST, FUNCTION_VALUE_TYPE)

                    // Duplicate the function value (one for storage, one for setSelfRef call)
                    mv.visitInsn(DUP)

                    // Store in slot
                    mv.visitVarInsn(ASTORE, slot)

                    // Call setSelfRef on the function to update its captured self-reference
                    // Stack: function
                    // Load the stored function again for the argument
                    mv.visitVarInsn(ALOAD, slot)
                    mv.visitMethodInsn(
                        INVOKEVIRTUAL,
                        FUNCTION_VALUE_TYPE,
                        "setSelfRef",
                        "(L${VALUE_TYPE};)V",
                        false
                    )

                    // Let statement as expression returns nil
                    pushNil()
                } else {
                    // Non-self-referential binding: standard approach
                    compileExpr(expr.value)
                    declareBinding(pattern.name, slot, expr.isMutable)
                    mv.visitVarInsn(ASTORE, slot)
                    pushNil()
                }
            }
            is ListPattern -> {
                // Compile the value
                compileExpr(expr.value)

                // Cast to ListValue and store in temp slot
                mv.visitTypeInsn(CHECKCAST, LIST_VALUE_TYPE)
                val listSlot = allocateSlot()
                mv.visitVarInsn(ASTORE, listSlot)

                // Bind each element of the list pattern
                compileListPatternBindings(pattern, listSlot)

                // Let statement returns nil
                pushNil()
            }
            else -> TODO("Pattern ${pattern::class.simpleName} not yet implemented in let")
        }
    }

    private fun compileLetExpr(expr: LetExpr) {
        // Same as statement version
        compileLetStatement(expr)
    }

    private fun compileReturnExpr(expr: ReturnExpr) {
        // Compile the return value
        compileExpr(expr.value)

        // Create new ReturnException(value) and throw it
        mv.visitTypeInsn(NEW, RETURN_EXCEPTION_TYPE)
        mv.visitInsn(DUP_X1)  // Stack: exception, value, exception
        mv.visitInsn(SWAP)     // Stack: exception, exception, value
        mv.visitMethodInsn(
            INVOKESPECIAL,
            RETURN_EXCEPTION_TYPE,
            "<init>",
            "(L${VALUE_TYPE};)V",
            false
        )
        mv.visitInsn(ATHROW)
    }

    private fun compileBreakExpr(expr: BreakExpr) {
        // Compile the break value
        compileExpr(expr.value)

        // Create new BreakException(value) and throw it
        mv.visitTypeInsn(NEW, BREAK_EXCEPTION_TYPE)
        mv.visitInsn(DUP_X1)  // Stack: exception, value, exception
        mv.visitInsn(SWAP)     // Stack: exception, exception, value
        mv.visitMethodInsn(
            INVOKESPECIAL,
            BREAK_EXCEPTION_TYPE,
            "<init>",
            "(L${VALUE_TYPE};)V",
            false
        )
        mv.visitInsn(ATHROW)
    }

    private fun compileRangeExpr(expr: RangeExpr) {
        // Compile start expression - must be integer
        compileExpr(expr.start)
        // Extract the long value from IntValue
        mv.visitTypeInsn(CHECKCAST, INT_VALUE_TYPE)
        mv.visitMethodInsn(
            INVOKEVIRTUAL,
            INT_VALUE_TYPE,
            "getValue",
            "()J",
            false
        )

        when {
            expr.end == null -> {
                // Unbounded range: start..
                mv.visitMethodInsn(
                    INVOKESTATIC,
                    RANGE_VALUE_TYPE,
                    "unbounded",
                    "(J)L${RANGE_VALUE_TYPE};",
                    false
                )
            }
            expr.isInclusive -> {
                // Inclusive range: start..=end
                compileExpr(expr.end)
                mv.visitTypeInsn(CHECKCAST, INT_VALUE_TYPE)
                mv.visitMethodInsn(
                    INVOKEVIRTUAL,
                    INT_VALUE_TYPE,
                    "getValue",
                    "()J",
                    false
                )
                mv.visitMethodInsn(
                    INVOKESTATIC,
                    RANGE_VALUE_TYPE,
                    "inclusive",
                    "(JJ)L${RANGE_VALUE_TYPE};",
                    false
                )
            }
            else -> {
                // Exclusive range: start..end
                compileExpr(expr.end)
                mv.visitTypeInsn(CHECKCAST, INT_VALUE_TYPE)
                mv.visitMethodInsn(
                    INVOKEVIRTUAL,
                    INT_VALUE_TYPE,
                    "getValue",
                    "()J",
                    false
                )
                mv.visitMethodInsn(
                    INVOKESTATIC,
                    RANGE_VALUE_TYPE,
                    "exclusive",
                    "(JJ)L${RANGE_VALUE_TYPE};",
                    false
                )
            }
        }
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

    /**
     * Bind pattern elements from a list without failure checking.
     * Used for let destructuring where the pattern is expected to always match.
     */
    protected fun compileListPatternBindings(pattern: ListPattern, listSlot: Int) {
        // Get size for rest patterns
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

        var elementIndex = 0
        for (element in pattern.elements) {
            when (element) {
                is RestPattern -> {
                    // Capture remaining elements as a list
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
                    elementIndex++
                }
                is ListPattern -> {
                    // Nested list: get element, cast, and recursively bind
                    mv.visitVarInsn(ALOAD, listSlot)
                    pushIntValue(elementIndex)
                    mv.visitMethodInsn(
                        INVOKEVIRTUAL,
                        LIST_VALUE_TYPE,
                        "get",
                        "(I)L${VALUE_TYPE};",
                        false
                    )
                    mv.visitTypeInsn(CHECKCAST, LIST_VALUE_TYPE)
                    val nestedSlot = allocateSlot()
                    mv.visitVarInsn(ASTORE, nestedSlot)
                    compileListPatternBindings(element, nestedSlot)
                    elementIndex++
                }
                is LiteralPattern, is RangePattern -> {
                    // Skip literal/range patterns in binding context (no variable to bind)
                    elementIndex++
                }
            }
        }
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
                is SpreadElement -> {
                    // Evaluate the spread expression (should be a collection)
                    compileExpr(element.expr)
                    // Call Operators.spreadIntoList to add all elements
                    mv.visitMethodInsn(
                        INVOKESTATIC,
                        OPERATORS_TYPE,
                        "spreadIntoList",
                        "(Lkotlinx/collections/immutable/PersistentList;L${VALUE_TYPE};)Lkotlinx/collections/immutable/PersistentList;",
                        false
                    )
                }
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

    private fun compileSetLiteral(expr: SetLiteralExpr) {
        // Create a new PersistentSet builder
        mv.visitMethodInsn(
            INVOKESTATIC,
            "kotlinx/collections/immutable/ExtensionsKt",
            "persistentSetOf",
            "()Lkotlinx/collections/immutable/PersistentSet;",
            false
        )

        // Add each element
        for (element in expr.elements) {
            when (element) {
                is ExprElement -> {
                    compileExpr(element.expr)
                    mv.visitMethodInsn(
                        INVOKEINTERFACE,
                        "kotlinx/collections/immutable/PersistentSet",
                        "add",
                        "(Ljava/lang/Object;)Lkotlinx/collections/immutable/PersistentSet;",
                        true
                    )
                }
                is SpreadElement -> {
                    // Evaluate the spread expression (should be a collection)
                    compileExpr(element.expr)
                    // Call Operators.spreadIntoSet to add all elements
                    mv.visitMethodInsn(
                        INVOKESTATIC,
                        OPERATORS_TYPE,
                        "spreadIntoSet",
                        "(Lkotlinx/collections/immutable/PersistentSet;L${VALUE_TYPE};)Lkotlinx/collections/immutable/PersistentSet;",
                        false
                    )
                }
            }
        }

        // Wrap in SetValue
        mv.visitMethodInsn(
            INVOKESTATIC,
            SET_VALUE_TYPE,
            "box",
            "(Lkotlinx/collections/immutable/PersistentSet;)L${SET_VALUE_TYPE};",
            false
        )
    }

    private fun compileDictLiteral(expr: DictLiteralExpr) {
        // Create a new PersistentMap builder
        mv.visitMethodInsn(
            INVOKESTATIC,
            "kotlinx/collections/immutable/ExtensionsKt",
            "persistentMapOf",
            "()Lkotlinx/collections/immutable/PersistentMap;",
            false
        )

        // Add each entry
        for (entry in expr.entries) {
            when (entry) {
                is KeyValueEntry -> {
                    compileExpr(entry.key)
                    compileExpr(entry.value)
                    mv.visitMethodInsn(
                        INVOKEINTERFACE,
                        "kotlinx/collections/immutable/PersistentMap",
                        "put",
                        "(Ljava/lang/Object;Ljava/lang/Object;)Lkotlinx/collections/immutable/PersistentMap;",
                        true
                    )
                }
                is ShorthandEntry -> {
                    // Key is the name as a string literal
                    mv.visitLdcInsn(entry.name)
                    mv.visitMethodInsn(
                        INVOKESTATIC,
                        STRING_VALUE_TYPE,
                        "box",
                        "(Ljava/lang/String;)L${STRING_VALUE_TYPE};",
                        false
                    )
                    // Value is the variable with that name
                    compileExpr(IdentifierExpr(entry.name, expr.span))
                    mv.visitMethodInsn(
                        INVOKEINTERFACE,
                        "kotlinx/collections/immutable/PersistentMap",
                        "put",
                        "(Ljava/lang/Object;Ljava/lang/Object;)Lkotlinx/collections/immutable/PersistentMap;",
                        true
                    )
                }
            }
        }

        // Wrap in DictValue
        mv.visitMethodInsn(
            INVOKESTATIC,
            DICT_VALUE_TYPE,
            "box",
            "(Lkotlinx/collections/immutable/PersistentMap;)L${DICT_VALUE_TYPE};",
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
                CaptureInfo(name, binding.slot, isSelfRef = name == currentSelfRefName)
            }
        }

        // Generate lambda class
        val lambdaClassName = lambdaGenerator(expr.params, expr.body, captures)

        // Instantiate the lambda: new LambdaClass(capture1, capture2, ...)
        mv.visitTypeInsn(NEW, lambdaClassName)
        mv.visitInsn(DUP)

        // Push captured values (excluding self-references which use base class selfRef field)
        val nonSelfCaptures = captures.filter { !it.isSelfRef }
        for (capture in nonSelfCaptures) {
            mv.visitVarInsn(ALOAD, capture.slot)
        }

        // Constructor descriptor
        val constructorDesc = buildString {
            append('(')
            repeat(nonSelfCaptures.size) { append("L${VALUE_TYPE};") }
            append(")V")
        }

        mv.visitMethodInsn(INVOKESPECIAL, lambdaClassName, "<init>", constructorDesc, false)
    }

    /**
     * Find free variables in an expression that are not bound by the given parameters.
     */
    private fun findFreeVariables(expr: Expr, params: List<Param>): Set<String> {
        val paramNames = collectParamBindings(params)
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
                    // Let doesn't have a continuation expression in AST, handled by block
                }
                is BlockExpr -> {
                    var currentBound = bound
                    for (stmt in e.statements) {
                        when (stmt) {
                            is ExprStatement -> visit(stmt.expr, currentBound)
                            is LetExpr -> {
                                visit(stmt.value, currentBound)
                                currentBound = currentBound + collectPatternBindings(stmt.pattern)
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
                    val thenBound = when (val cond = e.condition) {
                        is ExprCondition -> {
                            visit(cond.expr, bound)
                            bound
                        }
                        is LetCondition -> {
                            visit(cond.value, bound)
                            bound + collectPatternBindings(cond.pattern)
                        }
                    }
                    visit(e.thenBranch, thenBound)
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
                    val fnParams = collectParamBindings(e.params)
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
                is SetLiteralExpr -> e.elements.forEach { elem ->
                    when (elem) {
                        is ExprElement -> visit(elem.expr, bound)
                        is SpreadElement -> visit(elem.expr, bound)
                    }
                }
                is DictLiteralExpr -> e.entries.forEach { entry ->
                    when (entry) {
                        is KeyValueEntry -> {
                            visit(entry.key, bound)
                            visit(entry.value, bound)
                        }
                        is ShorthandEntry -> {
                            if (entry.name !in bound && entry.name !in BUILTIN_FUNCTIONS) {
                                freeVars.add(entry.name)
                            }
                        }
                    }
                }
                is RangeExpr -> {
                    visit(e.start, bound)
                    e.end?.let { visit(it, bound) }
                }
                // Terminals that don't contain expressions
                is IntLiteralExpr, is DecimalLiteralExpr, is StringLiteralExpr,
                is BoolLiteralExpr, is NilLiteralExpr, is PlaceholderExpr, is OperatorExpr -> { }
                is InfixCallExpr -> {
                    // Function name could be a user-defined function
                    if (e.functionName !in bound && e.functionName !in BUILTIN_FUNCTIONS) {
                        freeVars.add(e.functionName)
                    }
                    visit(e.left, bound)
                    visit(e.right, bound)
                }
                is ReturnExpr -> e.value?.let { visit(it, bound) }
                is BreakExpr -> e.value?.let { visit(it, bound) }
                is TestBlockExpr -> {
                    // Test blocks don't capture variables (they're validated at CLI level)
                }
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

    private fun collectParamBindings(params: List<Param>): Set<String> {
        return params.flatMap { param ->
            when (param) {
                is NamedParam -> listOf(param.name)
                is RestParam -> listOf(param.name)
                is PatternParam -> collectPatternBindings(param.pattern).toList()
                PlaceholderParam -> emptyList()
            }
        }.toSet()
    }

    /**
     * Check if a value expression contains a self-referential binding.
     * This is true for:
     * - Direct function expression: `|x| ... name(x) ...`
     * - Call with lambda argument: `memoize(|x| ... name(x) ...)`
     * - Binary expression with lambda: `f >> (|x| ... name(x) ...)`
     */
    private fun isValueSelfReferential(value: Expr, name: String): Boolean {
        return when (value) {
            is FunctionExpr -> {
                val freeVars = findFreeVariables(value.body, value.params)
                name in freeVars
            }
            is CallExpr -> {
                // Check if any argument is a lambda that references the name
                value.arguments.any { arg ->
                    isValueSelfReferential(arg.expr, name)
                }
            }
            is BinaryExpr -> {
                // Handle composition/pipeline with self-referential lambda
                isValueSelfReferential(value.left, name) ||
                    isValueSelfReferential(value.right, name)
            }
            else -> false
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

            // Check if there are any spread arguments
            val hasSpread = expr.arguments.any { it is SpreadArgument }

            if (hasSpread) {
                // With spreads, build argument list dynamically using ArrayList
                mv.visitTypeInsn(NEW, "java/util/ArrayList")
                mv.visitInsn(DUP)
                mv.visitMethodInsn(
                    INVOKESPECIAL,
                    "java/util/ArrayList",
                    "<init>",
                    "()V",
                    false
                )

                for (arg in expr.arguments) {
                    when (arg) {
                        is ExprArgument -> {
                            // list.add(value)
                            mv.visitInsn(DUP)
                            compileExpr(arg.expr)
                            mv.visitMethodInsn(
                                INVOKEVIRTUAL,
                                "java/util/ArrayList",
                                "add",
                                "(Ljava/lang/Object;)Z",
                                false
                            )
                            mv.visitInsn(POP) // pop the boolean result
                        }
                        is SpreadArgument -> {
                            // Spread collection into the argument list
                            // Stack: [callee, list]
                            mv.visitInsn(DUP)
                            // Stack: [callee, list, list]
                            compileExpr(arg.expr)
                            // Stack: [callee, list, list, spread_value]
                            // spreadIntoJavaList(list, spread_value) mutates list and returns void
                            mv.visitMethodInsn(
                                INVOKESTATIC,
                                OPERATORS_TYPE,
                                "spreadIntoJavaList",
                                "(Ljava/util/ArrayList;L${VALUE_TYPE};)V",
                                false
                            )
                            // Stack: [callee, list] - the DUPed reference remains
                        }
                    }
                }
            } else {
                // No spreads: use efficient array approach
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
            }

            // Cast callee to FunctionValue and invoke through Operators.invokeFunction
            // for partial application support
            mv.visitInsn(SWAP) // Move args list below callee
            mv.visitTypeInsn(CHECKCAST, FUNCTION_VALUE_TYPE)
            mv.visitInsn(SWAP) // Move callee below args list again
            mv.visitMethodInsn(
                INVOKESTATIC,
                OPERATORS_TYPE,
                "invokeFunction",
                "(L${FUNCTION_VALUE_TYPE};Ljava/util/List;)L${VALUE_TYPE};",
                false
            )
        }
    }

    /**
     * Compile infix function call: `left \`func\` right` → `func(left, right)`
     */
    private fun compileInfixCall(expr: InfixCallExpr) {
        val functionName = expr.functionName
        val arguments = listOf(ExprArgument(expr.left), ExprArgument(expr.right))

        // Check if it's a builtin function
        if (functionName in BUILTIN_FUNCTIONS) {
            compileBuiltinCall(functionName, arguments)
        } else {
            // User-defined function - look up by name and call
            compileExpr(IdentifierExpr(functionName, expr.span))

            // Build argument list
            pushIntValue(2)
            mv.visitTypeInsn(ANEWARRAY, VALUE_TYPE)
            mv.visitInsn(DUP)
            pushIntValue(0)
            compileExpr(expr.left)
            mv.visitInsn(AASTORE)
            mv.visitInsn(DUP)
            pushIntValue(1)
            compileExpr(expr.right)
            mv.visitInsn(AASTORE)
            mv.visitMethodInsn(
                INVOKESTATIC,
                "java/util/Arrays",
                "asList",
                "([Ljava/lang/Object;)Ljava/util/List;",
                false
            )

            // Cast callee to FunctionValue and invoke through Operators.invokeFunction
            // for partial application support
            mv.visitInsn(SWAP)
            mv.visitTypeInsn(CHECKCAST, FUNCTION_VALUE_TYPE)
            mv.visitInsn(SWAP)
            mv.visitMethodInsn(
                INVOKESTATIC,
                OPERATORS_TYPE,
                "invokeFunction",
                "(L${FUNCTION_VALUE_TYPE};Ljava/util/List;)L${VALUE_TYPE};",
                false
            )
        }
    }

    private fun compileBuiltinCall(name: String, arguments: List<CallArgument>) {
        // If there are spread arguments, fall back to general function call mechanism
        // which handles spreads properly via BuiltinFunctionValue.invoke
        if (arguments.any { it is SpreadArgument }) {
            compileBuiltinCallWithSpreads(name, arguments)
            return
        }

        val plainArgs = arguments.filterIsInstance<ExprArgument>()
        val expectedArity = BUILTIN_ARITIES[name] ?: throw CodegenException("Unknown builtin: $name")

        // Special handling for variadic functions
        if (name == "puts") {
            // Create a Value[] array for varargs
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
                BUILTINS_TYPE,
                name,
                "([L${VALUE_TYPE};)L${VALUE_TYPE};",
                false
            )
            return
        }

        // Partial application: if fewer args than expected, return PartiallyAppliedBuiltinValue
        if (plainArgs.size < expectedArity) {
            // Create a PartiallyAppliedBuiltinValue with the partial arguments
            mv.visitTypeInsn(NEW, "santa/runtime/PartiallyAppliedBuiltinValue")
            mv.visitInsn(DUP)
            mv.visitLdcInsn(name)

            // Create List<Value> for partial args
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

            // Call constructor: PartiallyAppliedBuiltinValue(String, List<Value>)
            mv.visitMethodInsn(
                INVOKESPECIAL,
                "santa/runtime/PartiallyAppliedBuiltinValue",
                "<init>",
                "(Ljava/lang/String;Ljava/util/List;)V",
                false
            )
            return
        }

        // Push arguments for fixed-arity builtins
        for (arg in plainArgs) {
            compileExpr(arg.expr)
        }

        // Generate method signature based on arity
        val descriptor = when (plainArgs.size) {
            0 -> "()L${VALUE_TYPE};"
            1 -> "(L${VALUE_TYPE};)L${VALUE_TYPE};"
            2 -> "(L${VALUE_TYPE};L${VALUE_TYPE};)L${VALUE_TYPE};"
            3 -> "(L${VALUE_TYPE};L${VALUE_TYPE};L${VALUE_TYPE};)L${VALUE_TYPE};"
            4 -> "(L${VALUE_TYPE};L${VALUE_TYPE};L${VALUE_TYPE};L${VALUE_TYPE};)L${VALUE_TYPE};"
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

    /**
     * Handle builtin calls with spread arguments by pushing a BuiltinFunctionValue
     * and calling it with a dynamically constructed argument list.
     */
    private fun compileBuiltinCallWithSpreads(name: String, arguments: List<CallArgument>) {
        // Push BuiltinFunctionValue for the builtin
        mv.visitTypeInsn(NEW, "santa/runtime/BuiltinFunctionValue")
        mv.visitInsn(DUP)
        mv.visitLdcInsn(name)
        mv.visitMethodInsn(
            INVOKESPECIAL,
            "santa/runtime/BuiltinFunctionValue",
            "<init>",
            "(Ljava/lang/String;)V",
            false
        )

        // Build argument list dynamically using ArrayList
        mv.visitTypeInsn(NEW, "java/util/ArrayList")
        mv.visitInsn(DUP)
        mv.visitMethodInsn(
            INVOKESPECIAL,
            "java/util/ArrayList",
            "<init>",
            "()V",
            false
        )

        for (arg in arguments) {
            when (arg) {
                is ExprArgument -> {
                    mv.visitInsn(DUP)
                    compileExpr(arg.expr)
                    mv.visitMethodInsn(
                        INVOKEVIRTUAL,
                        "java/util/ArrayList",
                        "add",
                        "(Ljava/lang/Object;)Z",
                        false
                    )
                    mv.visitInsn(POP) // pop the boolean result
                }
                is SpreadArgument -> {
                    mv.visitInsn(DUP)
                    compileExpr(arg.expr)
                    mv.visitMethodInsn(
                        INVOKESTATIC,
                        OPERATORS_TYPE,
                        "spreadIntoJavaList",
                        "(Ljava/util/ArrayList;L${VALUE_TYPE};)V",
                        false
                    )
                }
            }
        }

        // Call FunctionValue.invoke(List<Value>)
        mv.visitMethodInsn(
            INVOKEVIRTUAL,
            FUNCTION_VALUE_TYPE,
            "invoke",
            "(Ljava/util/List;)L${VALUE_TYPE};",
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

    fun declareBinding(name: String, slot: Int, isMutable: Boolean) {
        scopes.last()[name] = LocalBinding(name, slot, isMutable)
    }

    protected fun lookupBinding(name: String): LocalBinding? {
        for (scope in scopes.asReversed()) {
            scope[name]?.let { return it }
        }
        return null
    }

    fun allocateSlot(): Int = nextSlot++

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
        const val SET_VALUE_TYPE = "santa/runtime/value/SetValue"
        const val DICT_VALUE_TYPE = "santa/runtime/value/DictValue"
        const val RANGE_VALUE_TYPE = "santa/runtime/value/RangeValue"
        const val FUNCTION_VALUE_TYPE = "santa/runtime/value/FunctionValue"
        const val RETURN_EXCEPTION_TYPE = "santa/runtime/value/ReturnException"
        const val BREAK_EXCEPTION_TYPE = "santa/runtime/value/BreakException"
        const val OPERATORS_TYPE = "santa/runtime/Operators"
        const val BUILTINS_TYPE = "santa/runtime/Builtins"

        val BUILTIN_FUNCTIONS = setOf(
            // Existing core
            "size", "first", "rest", "push", "int", "type",
            "keys", "values", "abs",
            // Type conversion
            "ints", "list", "set", "dict",
            // Collection access
            "get", "second", "last",
            // Collection modification
            "assoc", "update", "update_d",
            // Transformation
            "map", "filter", "flat_map", "filter_map", "find_map",
            // Reduction
            "reduce", "fold", "fold_s", "scan", "each",
            // Search
            "find", "count",
            // Aggregation
            "sum", "max", "min",
            // Sequence manipulation
            "skip", "take", "sort", "reverse", "rotate", "chunk",
            // Set operations
            "union", "intersection",
            // Predicates
            "includes?", "excludes?", "any?", "all?",
            // Lazy sequences
            "repeat", "cycle", "iterate", "zip", "combinations", "range",
            // String functions
            "lines", "split", "upper", "lower", "replace", "join",
            "regex_match", "regex_match_all", "md5",
            // Math
            "signum", "vec_add",
            // Bitwise operations
            "bit_and", "bit_or", "bit_xor", "bit_not", "bit_shift_left", "bit_shift_right",
            // Utility
            "id", "memoize",
            // External functions
            "read", "puts",
        )

        /** Arity of each builtin function for partial application support. */
        val BUILTIN_ARITIES = mapOf(
            // 1-arity functions
            "size" to 1, "first" to 1, "rest" to 1, "int" to 1, "type" to 1,
            "keys" to 1, "values" to 1, "abs" to 1, "ints" to 1, "list" to 1,
            "set" to 1, "dict" to 1, "second" to 1, "last" to 1,
            "sum" to 1, "max" to 1, "min" to 1, "reverse" to 1,
            "union" to 1, "intersection" to 1,
            "repeat" to 1, "cycle" to 1, "lines" to 1, "upper" to 1, "lower" to 1,
            "md5" to 1, "signum" to 1, "bit_not" to 1, "id" to 1, "memoize" to 1,
            "read" to 1, "zip" to 1,
            // 2-arity functions
            "push" to 2, "get" to 2, "map" to 2, "filter" to 2, "flat_map" to 2,
            "filter_map" to 2, "find_map" to 2, "reduce" to 2, "each" to 2,
            "find" to 2, "count" to 2, "skip" to 2, "take" to 2, "sort" to 2,
            "rotate" to 2, "chunk" to 2,
            "includes?" to 2, "excludes?" to 2, "any?" to 2, "all?" to 2,
            "iterate" to 2, "combinations" to 2, "split" to 2, "join" to 2,
            "regex_match" to 2, "regex_match_all" to 2, "vec_add" to 2,
            "bit_and" to 2, "bit_or" to 2, "bit_xor" to 2,
            "bit_shift_left" to 2, "bit_shift_right" to 2,
            // 3-arity functions
            "assoc" to 3, "update" to 3, "fold" to 3, "fold_s" to 3, "scan" to 3,
            "replace" to 3, "range" to 3,
            // 4-arity functions
            "update_d" to 4,
            // Variadic (-1 indicates variadic)
            "puts" to -1,
        )
    }
}

/**
 * Generates bytecode for expressions inside lambda bodies.
 * Handles parameter access and captured variable access.
 */
private open class LambdaExpressionGenerator(
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

        // Count non-rest params for rest param calculation
        val nonRestParams = params.filter { it !is RestParam }

        // Bind parameters in order, extracting from args list
        var argIndex = 0
        for (param in params) {
            when (param) {
                is NamedParam -> {
                    // args.get(argIndex) -> store in local slot
                    mv.visitVarInsn(ALOAD, 1) // args
                    pushInt(mv, argIndex)
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
                    argIndex++
                }
                is PatternParam -> {
                    // Get value from args, cast to ListValue, and destructure
                    mv.visitVarInsn(ALOAD, 1) // args
                    pushInt(mv, argIndex)
                    mv.visitMethodInsn(
                        INVOKEINTERFACE,
                        "java/util/List",
                        "get",
                        "(I)Ljava/lang/Object;",
                        true
                    )
                    mv.visitTypeInsn(CHECKCAST, LIST_VALUE_TYPE)
                    val listSlot = nextSlot++
                    mv.visitVarInsn(ASTORE, listSlot)
                    // Destructure using the pattern
                    when (val pattern = param.pattern) {
                        is ListPattern -> compileListPatternBindings(pattern, listSlot)
                        else -> TODO("Pattern ${pattern::class.simpleName} not yet implemented in function params")
                    }
                    argIndex++
                }
                is RestParam -> {
                    // Capture remaining arguments as a list
                    mv.visitVarInsn(ALOAD, 1) // args
                    pushInt(mv, nonRestParams.size)
                    mv.visitVarInsn(ALOAD, 1) // args
                    mv.visitMethodInsn(
                        INVOKEINTERFACE,
                        "java/util/List",
                        "size",
                        "()I",
                        true
                    )
                    mv.visitMethodInsn(
                        INVOKEINTERFACE,
                        "java/util/List",
                        "subList",
                        "(II)Ljava/util/List;",
                        true
                    )
                    // Convert java.util.List to ListValue
                    mv.visitMethodInsn(
                        INVOKESTATIC,
                        "santa/runtime/Operators",
                        "listFromJavaList",
                        "(Ljava/util/List;)L${LIST_VALUE_TYPE};",
                        false
                    )
                    val slot = nextSlot++
                    mv.visitVarInsn(ASTORE, slot)
                    declareBinding(param.name, slot, isMutable = false)
                }
                PlaceholderParam -> {
                    // Skip placeholder params
                    argIndex++
                }
            }
        }

        // Store captured values into local slots so nested lambdas can capture them.
        // Without this, nested lambdas can't find captured variables via lookupBinding.
        for (capture in captures) {
            if (capture.isSelfRef) {
                // Self-references are loaded from base class selfRef field
                mv.visitVarInsn(ALOAD, 0) // this
                mv.visitFieldInsn(
                    GETFIELD,
                    FUNCTION_VALUE_TYPE,
                    "selfRef",
                    "L${VALUE_TYPE};"
                )
            } else {
                // Load from this.fieldName
                mv.visitVarInsn(ALOAD, 0) // this
                mv.visitFieldInsn(
                    GETFIELD,
                    lambdaClassName,
                    capture.name,
                    "L${VALUE_TYPE};"
                )
            }
            // Store in local slot
            val slot = nextSlot++
            mv.visitVarInsn(ASTORE, slot)
            declareBinding(capture.name, slot, isMutable = false)
        }
    }

    override fun compileExpr(expr: Expr) {
        when (expr) {
            is IdentifierExpr -> {
                // Check local bindings first (params, pattern bindings) - they shadow captures
                val localBinding = lookupBinding(expr.name)
                if (localBinding != null) {
                    mv.visitVarInsn(ALOAD, localBinding.slot)
                    return
                }

                // Then check captured variables
                val capture = captures.find { it.name == expr.name }
                if (capture != null) {
                    if (capture.isSelfRef) {
                        // Load from this.selfRef (base class field for recursive self-reference)
                        mv.visitVarInsn(ALOAD, 0) // this
                        mv.visitFieldInsn(
                            GETFIELD,
                            FUNCTION_VALUE_TYPE,
                            "selfRef",
                            "L${VALUE_TYPE};"
                        )
                    } else {
                        // Load from this.fieldName
                        mv.visitVarInsn(ALOAD, 0) // this
                        mv.visitFieldInsn(
                            GETFIELD,
                            lambdaClassName,
                            capture.name,
                            "L${VALUE_TYPE};"
                        )
                    }
                } else {
                    // Fall back to parent lookup (builtins)
                    super.compileExpr(expr)
                }
            }
            else -> super.compileExpr(expr)
        }
    }
}

/**
 * Generates bytecode for tail-recursive lambda bodies.
 *
 * Handles tail-recursive calls by:
 * 1. Evaluating new argument values
 * 2. Storing them in the parameter slots (updating in place)
 * 3. Jumping back to the loop start label
 *
 * This avoids stack growth for tail-recursive functions, enabling deep recursion
 * without StackOverflowError.
 */
private class TailRecursiveLambdaExpressionGenerator(
    mv: MethodVisitor,
    lambdaClassName: String,
    params: List<Param>,
    captures: List<CaptureInfo>,
    lambdaGenerator: LambdaGenerator,
    private val tailRecursionInfo: TailRecursionInfo,
    private val loopStartLabel: Label,
) : LambdaExpressionGenerator(mv, lambdaClassName, params, captures, lambdaGenerator) {

    // Store the parameter slots for updating during tail calls
    private val paramSlots: List<Int>

    init {
        // Capture the parameter slots that were allocated by the parent class
        val namedParams = params.filterIsInstance<NamedParam>()
        paramSlots = namedParams.map { param ->
            lookupBinding(param.name)?.slot
                ?: throw CodegenException("Parameter ${param.name} not found in scope")
        }
    }

    override fun compileExpr(expr: Expr) {
        when (expr) {
            is CallExpr -> {
                // Check if this is a tail-recursive call
                val callee = expr.callee
                if (callee is IdentifierExpr &&
                    callee.name == tailRecursionInfo.funcName &&
                    expr in tailRecursionInfo.tailCalls
                ) {
                    // This is a tail call - compile as loop iteration
                    compileTailCall(expr)
                } else {
                    // Normal call
                    super.compileExpr(expr)
                }
            }
            else -> super.compileExpr(expr)
        }
    }

    /**
     * Compiles a tail-recursive call as a loop iteration.
     *
     * Instead of actually calling the function, we:
     * 1. Evaluate all new argument values (storing in temp slots to handle dependencies)
     * 2. Copy from temp slots to parameter slots
     * 3. Jump back to the loop start
     */
    private fun compileTailCall(call: CallExpr) {
        val args = call.arguments.filterIsInstance<ExprArgument>()

        if (args.size != paramSlots.size) {
            throw CodegenException(
                "Tail call argument count mismatch: expected ${paramSlots.size}, got ${args.size}"
            )
        }

        // Evaluate all arguments first and store in temporary slots
        // This is necessary because arg expressions may reference current param values
        val tempSlots = args.map { arg ->
            compileExpr(arg.expr)
            val tempSlot = allocateSlot()
            mv.visitVarInsn(ASTORE, tempSlot)
            tempSlot
        }

        // Copy from temp slots to parameter slots
        for ((paramSlot, tempSlot) in paramSlots.zip(tempSlots)) {
            mv.visitVarInsn(ALOAD, tempSlot)
            mv.visitVarInsn(ASTORE, paramSlot)
        }

        // Jump back to loop start
        mv.visitJumpInsn(GOTO, loopStartLabel)
    }
}

/**
 * Exception thrown during code generation.
 */
class CodegenException(message: String) : RuntimeException(message)
