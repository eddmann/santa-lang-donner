package santa.compiler.codegen

import santa.compiler.lexer.Lexer
import santa.compiler.parser.Parser
import santa.compiler.resolver.Resolver

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
        val tokens = Lexer(source).lex()
        val program = Parser(tokens).parseProgram()
        Resolver().resolve(program)
        return CodeGenerator.generate(program)
    }
}
