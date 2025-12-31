package santa.compiler.codegen

import santa.compiler.lexer.Lexer
import santa.compiler.parser.Parser
import santa.compiler.parser.Program
import santa.compiler.resolver.Resolver

/**
 * Result of parsing and compiling source code.
 */
data class CompileResult(
    val program: Program,
    val script: CompiledScript,
)

/**
 * Compiler facade that orchestrates lexing, parsing, resolution, and bytecode generation.
 *
 * This is the main entry point for compiling santa-lang source code to JVM bytecode.
 */
object Compiler {
    /**
     * Compile source code and return a CompiledScript ready for execution.
     */
    fun compile(source: String): CompiledScript {
        return compileWithAst(source).script
    }

    /**
     * Compile source code and return both the AST and the compiled script.
     * Used by the CLI for test validation.
     */
    fun compileWithAst(source: String): CompileResult {
        val tokens = Lexer(source).lex()
        val program = Parser(tokens).parseProgram()
        Resolver().resolve(program)
        val script = CodeGenerator.generate(program)
        return CompileResult(program, script)
    }

    /**
     * Compile a program AST directly without re-parsing.
     * Used for test execution with modified programs.
     */
    fun compileProgram(program: Program): CompiledScript {
        Resolver().resolve(program)
        return CodeGenerator.generate(program)
    }
}
