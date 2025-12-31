package santa.compiler.codegen

import santa.runtime.value.Value

/**
 * A compiled santa-lang script ready for execution.
 *
 * The script is compiled to a JVM class with a static entry point.
 * Calling execute() loads and runs the compiled bytecode.
 */
class CompiledScript(
    private val className: String,
    private val bytecode: ByteArray,
) {
    /**
     * Execute the compiled script and return the result value.
     */
    fun execute(): Value {
        val loader = ScriptClassLoader(className, bytecode)
        val scriptClass = loader.loadClass(className)
        val executeMethod = scriptClass.getMethod("execute")
        return executeMethod.invoke(null) as Value
    }

    /**
     * Get the raw bytecode for debugging or caching.
     */
    fun getBytecode(): ByteArray = bytecode.copyOf()

    /**
     * Get the class name for debugging.
     */
    fun getClassName(): String = className
}

/**
 * Custom class loader for loading compiled script bytecode.
 */
private class ScriptClassLoader(
    private val className: String,
    private val bytecode: ByteArray,
) : ClassLoader(ScriptClassLoader::class.java.classLoader) {

    override fun findClass(name: String): Class<*> {
        return if (name == className) {
            defineClass(name, bytecode, 0, bytecode.size)
        } else {
            super.findClass(name)
        }
    }
}
