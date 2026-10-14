package com.varad.unstoppableash

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** Personal media and on-phone-only data never reach cloud backup or a new phone (review I6). */
class BackupRulesTest {
    private val res = File(System.getProperty("user.dir")!!).let { if (File(it, "src/main").isDirectory) it else File(it, "app") }.resolve("src/main/res/xml")

    private fun excludesAllFiles(xml: String) = Regex("""<exclude\s+domain="file"\s+path="\."\s*/>""").findAll(xml).count()

    @Test fun everyFileUnderFilesDirIsExcludedEverywhere() {
        assertTrue("cloud backup and device transfer", excludesAllFiles(res.resolve("data_extraction_rules.xml").readText()) == 2)
        assertTrue("Android 11 and older", excludesAllFiles(res.resolve("backup_rules.xml").readText()) == 1)
    }
}
