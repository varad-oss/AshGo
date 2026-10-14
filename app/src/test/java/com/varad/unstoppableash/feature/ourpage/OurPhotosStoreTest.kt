package com.varad.unstoppableash.feature.ourpage

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class OurPhotosStoreTest {
    @get:Rule val tmp = TemporaryFolder()
    private var clock = 1_000L

    private fun store() = OurPhotosStore(tmp.root.resolve("our"), encode = { uri -> if (uri == "bad") null else uri.toByteArray() }, now = { clock++ })

    @Test fun addsPhotosNewestLastAndKeepsThemOnDisk() = runTest {
        val s = store()
        assertEquals(2, s.add(listOf("a", "b")))
        val list = s.list()
        assertEquals(2, list.size)
        assertEquals("b", list.last().readText())
        assertEquals("survives a new store instance", 2, store().list().size)
    }

    @Test fun unreadablePhotosAreSkipped() = runTest {
        val s = store()
        assertEquals(1, s.add(listOf("bad", "ok")))
        assertEquals(listOf("ok"), s.list().map { it.readText() })
    }

    @Test fun removeDeletesTheFile() = runTest {
        val s = store()
        s.add(listOf("a", "b"))
        val first = s.list().first()
        s.remove(first.name)
        assertEquals(listOf("b"), s.list().map { it.readText() })
        assertTrue(!first.exists())
    }

    @Test fun removeNeverLeavesTheFolder() = runTest {
        val outside = tmp.newFile("keep.jpg")
        val s = store()
        s.add(listOf("a")) // the folder exists, so "../" really resolves
        s.remove("../keep.jpg")
        assertTrue(outside.exists())
    }

    @Test fun capsTheCount() = runTest {
        val s = store()
        val added = s.add((1..OurPhotosStore.MAX + 5).map { "p$it" })
        assertEquals(OurPhotosStore.MAX, added)
        assertEquals(OurPhotosStore.MAX, s.list().size)
        assertEquals(0, s.add(listOf("more")))
    }
}
