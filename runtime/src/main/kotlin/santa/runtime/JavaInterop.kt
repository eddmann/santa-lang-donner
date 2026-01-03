package santa.runtime

import kotlinx.collections.immutable.toPersistentList
import kotlinx.collections.immutable.toPersistentMap
import kotlinx.collections.immutable.toPersistentSet
import santa.runtime.value.*
import java.lang.reflect.Method
import java.lang.reflect.Constructor
import java.lang.reflect.Field
import java.lang.reflect.Modifier
import java.util.concurrent.ConcurrentHashMap

/**
 * Java interoperability utilities for santa-lang.
 *
 * Provides reflection-based method invocation, field access, and type
 * conversion between santa-lang Values and Java objects.
 */
object JavaInterop {

    // Cache for loaded classes
    private val classCache = ConcurrentHashMap<String, Class<*>>()

    // Cache for resolved methods (class + method name + arg count -> Method)
    private val methodCache = ConcurrentHashMap<MethodKey, Method>()

    // Cache for resolved constructors
    private val constructorCache = ConcurrentHashMap<ConstructorKey, Constructor<*>>()

    // Cache for resolved fields
    private val fieldCache = ConcurrentHashMap<FieldKey, Field>()

    private data class MethodKey(val clazz: Class<*>, val name: String, val argCount: Int)
    private data class ConstructorKey(val clazz: Class<*>, val argCount: Int)
    private data class FieldKey(val clazz: Class<*>, val name: String)

    // =========================================================================
    // Class Loading
    // =========================================================================

    /**
     * Load a class by its fully qualified name.
     *
     * @param className Fully qualified class name (e.g., "java.util.ArrayList")
     * @return The loaded Class object
     * @throws SantaRuntimeException if the class cannot be found
     */
    @JvmStatic
    fun loadClass(className: String): Class<*> {
        return classCache.getOrPut(className) {
            try {
                Class.forName(className)
            } catch (e: ClassNotFoundException) {
                throw SantaRuntimeException("Java class not found: $className")
            }
        }
    }

    // =========================================================================
    // Object Construction
    // =========================================================================

    /**
     * Create a new instance of a Java class.
     *
     * @param clazz The class to instantiate
     * @param args Constructor arguments as santa-lang Values
     * @return The created object wrapped in JavaObjectValue
     */
    @JvmStatic
    fun construct(clazz: Class<*>, args: List<Value>): Value {
        val javaArgs = args.map { toJava(it) }.toTypedArray()
        val argTypes: Array<Class<*>?> = javaArgs.map { it?.javaClass as Class<*>? }.toTypedArray()

        val constructor = findConstructor(clazz, argTypes, javaArgs.size)
            ?: throw SantaRuntimeException(
                "No matching constructor found for ${clazz.simpleName} with ${args.size} arguments"
            )

        return try {
            constructor.isAccessible = true
            val result = constructor.newInstance(*coerceArgs(constructor.parameterTypes, javaArgs))
            fromJava(result)
        } catch (e: Exception) {
            throw SantaRuntimeException("Failed to construct ${clazz.simpleName}: ${e.message}")
        }
    }

    private fun findConstructor(clazz: Class<*>, argTypes: Array<Class<*>?>, argCount: Int): Constructor<*>? {
        val key = ConstructorKey(clazz, argCount)
        return constructorCache.getOrPut(key) {
            clazz.constructors
                .filter { it.parameterCount == argCount }
                .firstOrNull { matchesParameters(it.parameterTypes, argTypes) }
                ?: clazz.constructors.firstOrNull { it.parameterCount == argCount }
        }
    }

    // =========================================================================
    // Method Invocation
    // =========================================================================

    /**
     * Invoke an instance method on a Java object.
     *
     * @param target The target object (will be unwrapped if JavaObjectValue)
     * @param methodName The method name to invoke
     * @param args Method arguments as santa-lang Values
     * @return The method result wrapped as a Value
     */
    @JvmStatic
    fun invokeMethod(target: Value, methodName: String, args: List<Value>): Value {
        val javaTarget = toJava(target)
            ?: throw SantaRuntimeException("Cannot invoke method on nil")

        val javaArgs = args.map { toJava(it) }.toTypedArray()
        val targetClass = javaTarget.javaClass

        val method = findMethod(targetClass, methodName, javaArgs.size, false)
            ?: throw SantaRuntimeException(
                "No method '$methodName' with ${args.size} arguments found on ${targetClass.simpleName}"
            )

        return try {
            method.isAccessible = true
            val result = method.invoke(javaTarget, *coerceArgs(method.parameterTypes, javaArgs))
            fromJava(result)
        } catch (e: Exception) {
            val cause = e.cause ?: e
            throw SantaRuntimeException("Method $methodName failed: ${cause.message}")
        }
    }

    /**
     * Invoke a static method on a Java class.
     *
     * @param clazz The class containing the static method
     * @param methodName The static method name
     * @param args Method arguments as santa-lang Values
     * @return The method result wrapped as a Value
     */
    @JvmStatic
    fun invokeStatic(clazz: Class<*>, methodName: String, args: List<Value>): Value {
        val javaArgs = args.map { toJava(it) }.toTypedArray()

        val method = findMethod(clazz, methodName, javaArgs.size, true)
            ?: throw SantaRuntimeException(
                "No static method '$methodName' with ${args.size} arguments found on ${clazz.simpleName}"
            )

        return try {
            method.isAccessible = true
            val result = method.invoke(null, *coerceArgs(method.parameterTypes, javaArgs))
            fromJava(result)
        } catch (e: Exception) {
            val cause = e.cause ?: e
            throw SantaRuntimeException("Static method ${clazz.simpleName}.$methodName failed: ${cause.message}")
        }
    }

    private fun findMethod(clazz: Class<*>, name: String, argCount: Int, isStatic: Boolean): Method? {
        val key = MethodKey(clazz, name, argCount)
        return methodCache.getOrPut(key) {
            // Search in class and all superclasses
            var currentClass: Class<*>? = clazz
            while (currentClass != null) {
                val method = currentClass.declaredMethods
                    .filter { it.name == name && it.parameterCount == argCount }
                    .filter { Modifier.isStatic(it.modifiers) == isStatic }
                    .firstOrNull()
                if (method != null) return@getOrPut method
                currentClass = currentClass.superclass
            }
            // Also check interfaces for default methods
            clazz.interfaces.forEach { iface ->
                val method = iface.methods
                    .filter { it.name == name && it.parameterCount == argCount }
                    .firstOrNull()
                if (method != null) return@getOrPut method
            }
            throw SantaRuntimeException("Method not found: $name")
        }
    }

    // =========================================================================
    // Field Access
    // =========================================================================

    /**
     * Get an instance field value from a Java object.
     *
     * @param target The target object
     * @param fieldName The field name
     * @return The field value wrapped as a Value
     */
    @JvmStatic
    fun getField(target: Value, fieldName: String): Value {
        val javaTarget = toJava(target)
            ?: throw SantaRuntimeException("Cannot access field on nil")

        val targetClass = javaTarget.javaClass
        val field = findField(targetClass, fieldName)
            ?: throw SantaRuntimeException("No field '$fieldName' found on ${targetClass.simpleName}")

        return try {
            field.isAccessible = true
            fromJava(field.get(javaTarget))
        } catch (e: Exception) {
            throw SantaRuntimeException("Failed to access field $fieldName: ${e.message}")
        }
    }

    /**
     * Get a static field value from a Java class.
     *
     * @param clazz The class containing the static field
     * @param fieldName The static field name
     * @return The field value wrapped as a Value
     */
    @JvmStatic
    fun getStaticField(clazz: Class<*>, fieldName: String): Value {
        val field = findField(clazz, fieldName)
            ?: throw SantaRuntimeException("No static field '$fieldName' found on ${clazz.simpleName}")

        if (!Modifier.isStatic(field.modifiers)) {
            throw SantaRuntimeException("Field '$fieldName' on ${clazz.simpleName} is not static")
        }

        return try {
            field.isAccessible = true
            fromJava(field.get(null))
        } catch (e: Exception) {
            throw SantaRuntimeException("Failed to access static field $fieldName: ${e.message}")
        }
    }

    private fun findField(clazz: Class<*>, name: String): Field? {
        val key = FieldKey(clazz, name)
        return fieldCache.getOrPut(key) {
            var currentClass: Class<*>? = clazz
            while (currentClass != null) {
                try {
                    return@getOrPut currentClass.getDeclaredField(name)
                } catch (e: NoSuchFieldException) {
                    currentClass = currentClass.superclass
                }
            }
            throw SantaRuntimeException("Field not found: $name")
        }
    }

    // =========================================================================
    // Type Conversion: Santa-Lang -> Java
    // =========================================================================

    /**
     * Convert a santa-lang Value to a Java object.
     *
     * @param value The Value to convert
     * @return The corresponding Java object, or null for NilValue
     */
    @JvmStatic
    fun toJava(value: Value): Any? = when (value) {
        is IntValue -> value.value
        is DecimalValue -> value.value
        is StringValue -> value.value
        is BoolValue -> value.value
        is NilValue -> null
        is JavaObjectValue -> value.obj
        is JavaClassValue -> value.clazz
        is ListValue -> value.elements.map { toJava(it) }
        is SetValue -> value.elements.map { toJava(it) }.toSet()
        is DictValue -> value.entries.map { (k, v) -> toJava(k) to toJava(v) }.toMap()
        is FunctionValue -> throw SantaRuntimeException("Cannot convert Function to Java object")
        is RangeValue -> value.toList()
        is LazySequenceValue -> value.toList().map { toJava(it) }
    }

    // =========================================================================
    // Type Conversion: Java -> Santa-Lang
    // =========================================================================

    /**
     * Convert a Java object to a santa-lang Value.
     *
     * @param obj The Java object to convert
     * @return The corresponding Value
     */
    @JvmStatic
    fun fromJava(obj: Any?): Value = when (obj) {
        null -> NilValue
        is Value -> obj  // Already a Value
        is Long -> IntValue(obj)
        is Int -> IntValue(obj.toLong())
        is Short -> IntValue(obj.toLong())
        is Byte -> IntValue(obj.toLong())
        is Double -> DecimalValue(obj)
        is Float -> DecimalValue(obj.toDouble())
        is String -> StringValue(obj)
        is Boolean -> BoolValue.box(obj)
        is Char -> StringValue(obj.toString())
        // Note: We keep mutable Java collections as JavaObjectValue to preserve
        // their identity for mutation. Use toList() builtin to convert if needed.
        // Only convert immutable Kotlin/Java collections or arrays.
        is Array<*> -> ListValue(obj.map { fromJava(it) }.toPersistentList())
        is IntArray -> ListValue(obj.map { IntValue(it.toLong()) }.toPersistentList())
        is LongArray -> ListValue(obj.map { IntValue(it) }.toPersistentList())
        is DoubleArray -> ListValue(obj.map { DecimalValue(it) }.toPersistentList())
        is FloatArray -> ListValue(obj.map { DecimalValue(it.toDouble()) }.toPersistentList())
        is BooleanArray -> ListValue(obj.map { BoolValue.box(it) }.toPersistentList())
        is CharArray -> StringValue(obj.concatToString())
        is ByteArray -> ListValue(obj.map { IntValue(it.toLong()) }.toPersistentList())
        is ShortArray -> ListValue(obj.map { IntValue(it.toLong()) }.toPersistentList())
        is Class<*> -> JavaClassValue(obj)
        else -> JavaObjectValue(obj)
    }

    // =========================================================================
    // Argument Coercion
    // =========================================================================

    /**
     * Coerce arguments to match expected parameter types.
     */
    private fun coerceArgs(paramTypes: Array<Class<*>>, args: Array<Any?>): Array<Any?> {
        return args.mapIndexed { i, arg ->
            if (i < paramTypes.size) {
                coerceArg(arg, paramTypes[i])
            } else {
                arg
            }
        }.toTypedArray()
    }

    private fun coerceArg(arg: Any?, targetType: Class<*>): Any? {
        if (arg == null) return null

        return when {
            targetType.isAssignableFrom(arg.javaClass) -> arg
            // Primitive number conversions
            targetType == Int::class.java || targetType == Integer::class.java -> when (arg) {
                is Long -> arg.toInt()
                is Number -> arg.toInt()
                else -> arg
            }
            targetType == Long::class.java || targetType == java.lang.Long::class.java -> when (arg) {
                is Number -> arg.toLong()
                else -> arg
            }
            targetType == Double::class.java || targetType == java.lang.Double::class.java -> when (arg) {
                is Number -> arg.toDouble()
                else -> arg
            }
            targetType == Float::class.java || targetType == java.lang.Float::class.java -> when (arg) {
                is Number -> arg.toFloat()
                else -> arg
            }
            targetType == Short::class.java || targetType == java.lang.Short::class.java -> when (arg) {
                is Number -> arg.toShort()
                else -> arg
            }
            targetType == Byte::class.java || targetType == java.lang.Byte::class.java -> when (arg) {
                is Number -> arg.toByte()
                else -> arg
            }
            targetType == Boolean::class.java || targetType == java.lang.Boolean::class.java -> arg
            targetType == Char::class.java || targetType == Character::class.java -> when (arg) {
                is String -> if (arg.isNotEmpty()) arg[0] else ' '
                else -> arg
            }
            // Collection conversions
            targetType.isAssignableFrom(List::class.java) -> when (arg) {
                is List<*> -> arg
                is Set<*> -> arg.toList()
                else -> arg
            }
            targetType.isAssignableFrom(Set::class.java) -> when (arg) {
                is Set<*> -> arg
                is List<*> -> arg.toSet()
                else -> arg
            }
            else -> arg
        }
    }

    private fun matchesParameters(paramTypes: Array<Class<*>>, argTypes: Array<Class<*>?>): Boolean {
        if (paramTypes.size != argTypes.size) return false
        return paramTypes.zip(argTypes).all { (param, arg) ->
            arg == null || param.isAssignableFrom(arg) || isAssignableWithBoxing(param, arg)
        }
    }

    private fun isAssignableWithBoxing(param: Class<*>, arg: Class<*>): Boolean {
        return when {
            param == Int::class.java && (arg == Integer::class.java || arg == Long::class.java) -> true
            param == Long::class.java && (arg == java.lang.Long::class.java || arg == Integer::class.java) -> true
            param == Double::class.java && (arg == java.lang.Double::class.java || Number::class.java.isAssignableFrom(arg)) -> true
            param == Boolean::class.java && arg == java.lang.Boolean::class.java -> true
            param == Integer::class.java && arg == Long::class.java -> true
            param == java.lang.Long::class.java && (arg == Long::class.java || arg == Integer::class.java) -> true
            param == java.lang.Double::class.java && Number::class.java.isAssignableFrom(arg) -> true
            param.isAssignableFrom(arg) -> true
            else -> false
        }
    }
}

// =============================================================================
// Java Interop Function Values (Combinators)
// =============================================================================

/**
 * A function value that invokes a Java instance method.
 *
 * Created by the `method` builtin: `method("toUpperCase")`
 * First argument is the target object, remaining are method arguments.
 */
class JavaMethodValue(
    private val methodName: String,
    private val partialArgs: List<Value> = emptyList()
) : FunctionValue(-1) {  // Variadic

    override fun invoke(args: List<Value>): Value {
        if (args.isEmpty()) {
            throw SantaRuntimeException("method($methodName) requires at least one argument (the target object)")
        }
        val target = args[0]
        val methodArgs = partialArgs + args.drop(1)
        return JavaInterop.invokeMethod(target, methodName, methodArgs)
    }

    override fun typeName(): String = "JavaMethod<$methodName>"

    /**
     * Create a partially applied version with additional arguments.
     */
    fun withArgs(additionalArgs: List<Value>): JavaMethodValue {
        return JavaMethodValue(methodName, partialArgs + additionalArgs)
    }
}

/**
 * A function value that invokes a Java static method.
 *
 * Created by the `static` builtin: `static(Math, "max")`
 */
class JavaStaticMethodValue(
    private val clazz: Class<*>,
    private val methodName: String,
    private val partialArgs: List<Value> = emptyList()
) : FunctionValue(-1) {  // Variadic

    override fun invoke(args: List<Value>): Value {
        val allArgs = partialArgs + args
        return JavaInterop.invokeStatic(clazz, methodName, allArgs)
    }

    override fun typeName(): String = "JavaStaticMethod<${clazz.simpleName}.$methodName>"

    /**
     * Create a partially applied version with additional arguments.
     */
    fun withArgs(additionalArgs: List<Value>): JavaStaticMethodValue {
        return JavaStaticMethodValue(clazz, methodName, partialArgs + additionalArgs)
    }
}

/**
 * A function value that constructs a Java object.
 *
 * Created by the `new` builtin: `new(ArrayList)`
 */
class JavaConstructorValue(
    private val clazz: Class<*>,
    private val partialArgs: List<Value> = emptyList()
) : FunctionValue(-1) {  // Variadic

    override fun invoke(args: List<Value>): Value {
        val allArgs = partialArgs + args
        return JavaInterop.construct(clazz, allArgs)
    }

    override fun typeName(): String = "JavaConstructor<${clazz.simpleName}>"

    /**
     * Create a partially applied version with additional arguments.
     */
    fun withArgs(additionalArgs: List<Value>): JavaConstructorValue {
        return JavaConstructorValue(clazz, partialArgs + additionalArgs)
    }
}

/**
 * A function value that accesses a Java field.
 *
 * Created by the `field` builtin: `field("x")`
 */
class JavaFieldAccessorValue(
    private val fieldName: String
) : FunctionValue(1) {

    override fun invoke(args: List<Value>): Value {
        if (args.isEmpty()) {
            throw SantaRuntimeException("field($fieldName) requires one argument (the target object)")
        }
        return JavaInterop.getField(args[0], fieldName)
    }

    override fun typeName(): String = "JavaField<$fieldName>"
}
