package santa.compiler.codegen

import santa.compiler.desugar.PatternParamDesugarer
import santa.compiler.desugar.PipelineDesugarer
import santa.compiler.desugar.PlaceholderDesugarer
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
        val parsedProgram = Parser(tokens).parseProgram()
        val desugared1 = PlaceholderDesugarer.desugar(parsedProgram)
        val desugared2 = PipelineDesugarer.desugar(desugared1)
        val program = PatternParamDesugarer.desugar(desugared2)
        Resolver().resolve(program)
        val script = CodeGenerator.generate(program)
        return CompileResult(program, script)
    }

    /**
     * Compile a program AST directly without re-parsing.
     * Used for test execution with modified programs.
     */
    fun compileProgram(program: Program): CompiledScript {
        val desugared1 = PlaceholderDesugarer.desugar(program)
        val desugared2 = PipelineDesugarer.desugar(desugared1)
        val desugared = PatternParamDesugarer.desugar(desugared2)
        Resolver().resolve(desugared)
        return CodeGenerator.generate(desugared)
    }
}
