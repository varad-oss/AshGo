package com.varad.unstoppableash

import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File

/**
 * Guards the v2 layering rules (ARCHITECTURE_PROPOSAL §3) and keeps personal
 * names out of v2 sources. v1 files are exempt until the Stage 11 cutover
 * deletes them.
 */
class LayeringTest {

    private val moduleDir: File = File(System.getProperty("user.dir")!!).let {
        if (File(it, "src/main").isDirectory) it else File(it, "app")
    }
    private val sourceRoot = File(moduleDir, "src/main/java/com/varad/unstoppableash")

    /** v1 sources that predate the layering rules; removed at cutover. */
    private val legacyFiles = setOf(
        "ui/AboutScreen.kt",
        "ui/AppDrawer.kt",
        "ui/HelpScreen.kt",
        "ui/ReceiverScreen.kt",
        "ui/TravelerChatScreen.kt",
        "ui/theme/Color.kt",
        "ui/theme/Theme.kt",
        "ui/theme/Type.kt",
    )

    private fun kotlinFilesUnder(vararg packages: String): List<File> =
        packages.flatMap { pkg ->
            File(sourceRoot, pkg).walkTopDown()
                .filter { it.isFile && it.extension == "kt" }
                .filterNot { it.relativeTo(sourceRoot).invariantSeparatorsPath in legacyFiles }
                .toList()
        }

    private fun offenders(files: List<File>, forbidden: List<String>): List<String> =
        files.flatMap { file ->
            val text = file.readText()
            forbidden.filter { text.contains(it) }
                .map { "${file.relativeTo(sourceRoot).invariantSeparatorsPath}: $it" }
        }

    @Test
    fun domainHasNoPlatformImports() {
        assertTrue("domain/ must exist", File(sourceRoot, "domain").isDirectory)
        val found = offenders(
            kotlinFilesUnder("domain"),
            listOf("import android.", "import androidx.", "import com.google.firebase"),
        )
        assertTrue("Platform imports in domain/: $found", found.isEmpty())
    }

    @Test
    fun uiAndFeatureHaveNoFirebaseImports() {
        val found = offenders(kotlinFilesUnder("ui", "feature"), listOf("import com.google.firebase"))
        assertTrue("Firebase imports in ui/ or feature/: $found", found.isEmpty())
    }

    @Test
    fun noPersonalNamesInV2() {
        val list = File(moduleDir, "src/test/resources/forbidden_names.txt")
        assumeTrue("forbidden_names.txt not present; skipping", list.isFile)
        val names = list.readLines().map { it.trim() }.filter { it.isNotEmpty() && !it.startsWith("#") }
        val v2Strings = File(moduleDir, "src/main/res").walkTopDown()
            .filter { it.isFile && it.name == "strings_v2.xml" }.toList()
        val files = kotlinFilesUnder("core", "domain", "data", "service", "ui", "feature") + v2Strings
        val found = files.flatMap { file ->
            val text = file.readText().lowercase()
            names.filter { text.contains(it.lowercase()) }
                .map { "${file.relativeTo(moduleDir).invariantSeparatorsPath}: <name #${names.indexOf(it) + 1}>" }
        }
        assertTrue("Personal names found in v2 sources: $found", found.isEmpty())
    }
}
