package santa.compiler.codegen

import santa.runtime.value.Value

/**
 * A compiled santa-lang script ready for execution.
 *
 * The script is compiled to a JVM class with a static entry point.
 * Lambda functions are compiled to separate classes.
 * Calling execute() loads and runs the compiled bytecode.
 */
class CompiledScript(
    private val mainClassName: String,
    private val mainBytecode: ByteArray,
    private val lambdaClasses: Map<String, ByteArray> = emptyMap(),
) {
    /**
     * Execute the compiled script and return the result value.
     */
    fun execute(): Value {
        val loader = ScriptClassLoader(mainClassName, mainBytecode, lambdaClasses)
        val scriptClass = loader.loadClass(mainClassName)
        val executeMethod = scriptClass.getMethod("execute")
        return executeMethod.invoke(null) as Value
    }

    /**
     * Get the raw bytecode for debugging or caching.
     */
    fun getBytecode(): ByteArray = mainBytecode.copyOf()

    /**
     * Get the class name for debugging.
     */
    fun getClassName(): String = mainClassName
}

/**
 * Custom class loader for loading compiled script bytecode.
 */
private class ScriptClassLoader(
    private val mainClassName: String,
    private val mainBytecode: ByteArray,
    private val lambdaClasses: Map<String, ByteArray>,
) : ClassLoader(ScriptClassLoader::class.java.classLoader) {

    override fun findClass(name: String): Class<*> {
        return when {
            name == mainClassName -> defineClass(name, mainBytecode, 0, mainBytecode.size)
            name in lambdaClasses -> {
                val bytecode = lambdaClasses[name]!!
                defineClass(name, bytecode, 0, bytecode.size)
            }
            else -> super.findClass(name)
        }
    }
}
