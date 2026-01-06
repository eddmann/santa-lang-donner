package santa.cli

import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldNotContain
import java.io.File
import java.util.concurrent.TimeUnit

/**
 * Helper for running CLI integration tests.
 * Spawns the actual CLI JAR and captures output.
 */
class CliTestHelper {

    private val projectRoot: File by lazy {
        // Find project root by looking for settings.gradle.kts
        var dir = File(System.getProperty("user.dir"))
        while (dir.parentFile != null) {
            if (File(dir, "settings.gradle.kts").exists()) {
                return@lazy dir
            }
            dir = dir.parentFile
        }
        // Fallback: try current directory
        File(System.getProperty("user.dir"))
    }

    private val jarPath: String by lazy {
        // Find the shadow JAR in build/libs
        val buildLibs = File(projectRoot, "cli/build/libs")
        val shadowJar = buildLibs.listFiles()
            ?.find { it.name.endsWith("-all.jar") }
            ?: error("Shadow JAR not found at ${buildLibs.absolutePath}. Run ./gradlew :cli:shadowJar first.")
        shadowJar.absolutePath
    }

    private val fixturesPath: String by lazy {
        // Try classpath resource first, then file system
        val resource = this::class.java.classLoader.getResource("fixtures")
        if (resource != null) {
            return@lazy File(resource.toURI()).absolutePath
        }
        // Fallback to file system
        val resources = File(projectRoot, "cli/src/test/resources/fixtures")
        if (!resources.exists()) {
            error("Fixtures directory not found: ${resources.absolutePath}")
        }
        resources.absolutePath
    }

    /**
     * Run the CLI with the given arguments.
     */
    fun run(vararg args: String): CliResult {
        val command = mutableListOf("java", "-jar", jarPath)
        command.addAll(args)

        val process = ProcessBuilder(command)
            .redirectErrorStream(false)
            .start()

        val stdout = process.inputStream.bufferedReader().readText()
        val stderr = process.errorStream.bufferedReader().readText()
        val exitCode = process.waitFor(30, TimeUnit.SECONDS)
            .let { completed ->
                if (completed) process.exitValue()
                else {
                    process.destroyForcibly()
                    error("CLI process timed out")
                }
            }

        return CliResult(exitCode, stdout, stderr)
    }

    /**
     * Run the CLI with a fixture file.
     */
    fun runWithFixture(fixture: String, vararg args: String): CliResult {
        val fixturePath = "$fixturesPath/$fixture"
        if (!File(fixturePath).exists()) {
            error("Fixture not found: $fixturePath")
        }
        val allArgs = args.toList() + fixturePath
        return run(*allArgs.toTypedArray())
    }
}

/**
 * Result from running the CLI.
 */
data class CliResult(
    val exitCode: Int,
    val stdout: String,
    val stderr: String
) {
    /**
     * Assert the command succeeded (exit code 0).
     */
    fun assertSuccess(): CliResult {
        exitCode shouldBe 0
        return this
    }

    /**
     * Assert a specific exit code.
     */
    fun assertExitCode(code: Int): CliResult {
        exitCode shouldBe code
        return this
    }

    /**
     * Assert stdout contains the given text.
     */
    fun assertStdoutContains(text: String): CliResult {
        stdout shouldContain text
        return this
    }

    /**
     * Assert stdout does not contain the given text.
     */
    fun assertStdoutNotContains(text: String): CliResult {
        stdout shouldNotContain text
        return this
    }

    /**
     * Assert stderr contains the given text.
     */
    fun assertStderrContains(text: String): CliResult {
        stderr shouldContain text
        return this
    }
}
