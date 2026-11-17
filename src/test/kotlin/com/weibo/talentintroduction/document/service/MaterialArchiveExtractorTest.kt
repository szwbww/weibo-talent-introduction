package com.weibo.talentintroduction.document.service

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class MaterialArchiveExtractorTest {
    @TempDir lateinit var root: Path
    private val extractor = MaterialArchiveExtractor()
    private fun zip(vararg entries: Pair<String, String>): Path {
        val file = root.resolve("source.zip")
        ZipOutputStream(Files.newOutputStream(file)).use { zip ->
            entries.forEach { (name, text) -> zip.putNextEntry(ZipEntry(name)); zip.write(text.toByteArray()); zip.closeEntry() }
        }
        return file
    }

    @Test fun `nested folders same basenames and inner archive preserved as separate files`() {
        val source = zip("学历/博士/证明.txt" to "doctor", "工作/证明.txt" to "employment", "bundle/inner.zip" to "inner bytes")
        val files = extractor.extract(source, root.resolve("out"))
        assertEquals(listOf("学历/博士/证明.txt", "工作/证明.txt", "bundle/inner.zip"), files.map { it.entryPath })
        assertEquals(listOf("doctor", "employment", "inner bytes"), files.map { Files.readString(it.path) })
        assertEquals(3, files.map { it.path }.distinct().size)
        assertTrue(Files.exists(source))
    }

    @Test fun `reject traversal absolute windows and overdeep paths`() {
        listOf("../escape", "/tmp/escape", "C:\\x", "a/../../escape", "a/./b", "a//b", "a\u0000b", (1..33).joinToString("/") { "a" }).forEach {
            assertThrows(IllegalArgumentException::class.java) { extractor.validatePath(it) }
        }
        val out = root.resolve("bad")
        assertThrows(IllegalArgumentException::class.java) { extractor.extract(zip("ok.txt" to "a", "../escape" to "b"), out) }
        assertFalse(Files.exists(out)); assertFalse(Files.exists(root.resolve("escape")))
    }

    @Test fun `limits and corrupt input leave no partial output`() {
        val archive = zip("a.txt" to "123456", "b.txt" to "123456")
        assertThrows(IllegalArgumentException::class.java) { extractor.extract(archive, root.resolve("size"), maxBytes=10) }
        assertFalse(Files.exists(root.resolve("size")))
        assertThrows(IllegalArgumentException::class.java) { extractor.extract(archive, root.resolve("count"), maxEntries=1) }
        assertFalse(Files.exists(root.resolve("count")))
        val corrupt = Files.writeString(root.resolve("corrupt.rar"), "not an archive")
        assertThrows(IllegalArgumentException::class.java) { extractor.extract(corrupt, root.resolve("corrupt")) }
        assertFalse(Files.exists(root.resolve("corrupt")))
    }

    @Test fun `real RAR4 and RAR5 fixtures extract with native implementation`() {
        listOf("sample-rar4.rar", "sample-rar5.rar").forEach { name ->
            val source = Path.of("src/test/resources/material-archives", name)
            val files = extractor.extract(source, root.resolve(name))
            assertTrue(files.isNotEmpty(), name)
            assertTrue(files.all { Files.isRegularFile(it.path) && Files.size(it.path) == it.size })
            assertTrue(files.any { it.size > 0 })
        }
    }
}
