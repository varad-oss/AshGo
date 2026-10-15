package com.varad.unstoppableash

import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File

/**
 * Guards the v2 layering rules (ARCHITECTURE_PROPOSAL §3) and keeps personal
 * names out of every production source.
 */
class LayeringTest {

    private val moduleDir: File = File(System.getProperty("user.dir")!!).let {
        if (File(it, "src/main").isDirectory) it else File(it, "app")
    }

    /** Package roots of every production source set (main, debug, release, flavours; java and kotlin dirs). */
    private val sourceRoots: List<File> = File(moduleDir, "src").listFiles().orEmpty()
        .filter { it.isDirectory && it.name != "test" && !it.name.startsWith("androidTest") }
        .flatMap { set -> listOf("java", "kotlin").map { File(set, "$it/com/varad/unstoppableash") } }
        .filter { it.isDirectory }

    /** v1 sources were removed at the Stage 11 cutover; nothing is exempt any more. */
    private val legacyFiles = emptySet<String>()

    private fun kotlinFilesUnder(vararg packages: String): List<Pair<File, String>> =
        sourceRoots.flatMap { root ->
            packages.flatMap { pkg ->
                File(root, pkg).walkTopDown()
                    .filter { it.isFile && it.extension == "kt" }
                    .map { it to it.relativeTo(root).invariantSeparatorsPath }
                    .filterNot { (_, rel) -> rel in legacyFiles }
                    .toList()
            }
        }

    /** Drops comments so KDoc prose can't trip (or hide from) the guards. */
    private fun code(file: File): String = file.readText()
        .replace(Regex("/\\*.*?\\*/", RegexOption.DOT_MATCHES_ALL), "")
        .replace(Regex("(?<!:)//.*"), "")

    private fun offenders(files: List<Pair<File, String>>, forbidden: Regex): List<String> =
        files.mapNotNull { (file, rel) -> forbidden.find(code(file))?.let { "$rel: ${it.value}" } }

    @Test
    fun domainHasNoPlatformImports() {
        val files = kotlinFilesUnder("domain")
        assertTrue("domain/ must exist and contain sources", files.isNotEmpty())
        val found = offenders(files, Regex("""\b(android|androidx|com\.google\.firebase)\.\w"""))
        assertTrue("Platform references in domain/: $found", found.isEmpty())
    }

    @Test
    fun uiAndFeatureHaveNoFirebaseImports() {
        val files = kotlinFilesUnder("ui", "feature")
        assertTrue("ui/ must contain v2 sources", files.isNotEmpty())
        val found = offenders(files, Regex("""\bcom\.google\.firebase\b"""))
        assertTrue("Firebase references in ui/ or feature/: $found", found.isEmpty())
    }

    @Test
    fun noPersonalNamesInV2() {
        val list = File(moduleDir, "src/test/resources/forbidden_names.txt")
        assumeTrue("forbidden_names.txt not present; skipping", list.isFile)
        val names = list.readLines().map { it.trim() }.filter { it.isNotEmpty() && !it.startsWith("#") }
        val v2Strings = File(moduleDir, "src/main/res").walkTopDown()
            .filter { it.isFile && it.name == "strings_v2.xml" }.toList()
        val files = kotlinFilesUnder("core", "domain", "data", "service", "ui", "feature").map { it.first } + v2Strings
        val found = files.flatMap { file ->
            val text = file.readText().lowercase()
            names.filter { text.contains(it.lowercase()) }
                .map { "${file.relativeTo(moduleDir).invariantSeparatorsPath}: <name #${names.indexOf(it) + 1}>" }
        }
        assertTrue("Personal names found in v2 sources: $found", found.isEmpty())
    }
}
