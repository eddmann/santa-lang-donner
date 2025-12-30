package santa.compiler.testutil

import java.nio.charset.StandardCharsets

object SnapshotAssertions {
    fun assertMatches(snapshotName: String, actual: String) {
        val resourcePath = "/snapshots/$snapshotName.snap"
        val resource = SnapshotAssertions::class.java.getResource(resourcePath)
            ?: throw AssertionError("Missing snapshot: $resourcePath")

        val expected = resource.readText(StandardCharsets.UTF_8)
        if (expected == actual) return

        val message = buildString {
            appendLine("Snapshot mismatch for $snapshotName")
            appendLine("--- Expected ---")
            appendLine(expected)
            appendLine("--- Actual ---")
            appendLine(actual)
        }
        throw AssertionError(message)
    }
}
