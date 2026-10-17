package com.tagforge.app.infrastructure.android

import com.tagforge.domain.common.Bytes
import java.io.File
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** TT-06: exports go to a private cache folder under safe names, never to a world-readable or traversable path. */
class ShareExporterTest {

    private val uid = Bytes.of(0x04, 0xA2, 0x3B, 0x1C, 0x5D, 0x6E, 0x80)

    @Test
    fun theFileNameCarriesTheChipTheLastUidBytesAndTheTimestamp() {
        val name = ExportFiles.fileName(chip = "NTAG213", uid = uid, extension = ExportFiles.TEXT, epochMillis = 1_792_240_800_000)

        assertTrue(name.matches(Regex("tagforge-dump-NTAG213-6E80-\\d{8}-\\d{6}\\.txt")), name)
    }

    @Test
    fun theFullUidNeverAppearsInTheFileName() {
        val name = ExportFiles.fileName("NTAG215", uid, ExportFiles.BINARY, 0)

        assertFalse(name.contains("A23B1C"), name)
        assertTrue(name.endsWith(".bin"))
    }

    @Test
    fun anUnknownChipFallsBackToATagLabelAndOddCharactersAreReplaced() {
        assertTrue(ExportFiles.fileName(null, uid, ExportFiles.TEXT, 0).contains("-tag-"))
        assertEquals("a_b_c.txt", ExportFiles.sanitize("a/b\\c.txt"))
        assertEquals("NTAG_213", ExportFiles.sanitize("NTAG 213"))
    }

    @Test
    fun onlyTheTwoKnownExtensionsAreAccepted() {
        assertFailsWith<IllegalArgumentException> { ExportFiles.fileName("NTAG213", uid, "apk", 0) }
        assertFailsWith<IllegalArgumentException> { ExportFiles.fileName("NTAG213", uid, "../txt", 0) }
    }

    @Test
    fun aResolvedFileStaysInsideTheExportFolder() {
        val dir = Files.createTempDirectory("exports").toFile()
        try {
            val file = ExportFiles.resolveInside(dir, "tagforge-dump-NTAG213-6E80-20261017-120000.txt")
            assertEquals(dir.canonicalFile, file.parentFile.canonicalFile)
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test
    fun traversalAttemptsAreRejected() {
        val dir = Files.createTempDirectory("exports").toFile()
        try {
            assertFailsWith<IllegalArgumentException> { ExportFiles.resolveInside(dir, "../secret.txt") }
            assertFailsWith<IllegalArgumentException> { ExportFiles.resolveInside(dir, "sub/x.txt") }
            assertFailsWith<IllegalArgumentException> { ExportFiles.resolveInside(dir, File(dir, "a.txt").absolutePath) }
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test
    fun staleExportsAreSweptAndFreshOnesKept() {
        val dir = Files.createTempDirectory("exports").toFile()
        try {
            val old = File(dir, "old.txt").apply { writeText("x"); setLastModified(1_000) }
            val fresh = File(dir, "fresh.txt").apply { writeText("y"); setLastModified(9_000_000) }

            val removed = ExportFiles.sweep(dir, nowMillis = 9_000_000 + 1000, maxAgeMillis = 60_000)

            assertEquals(1, removed)
            assertFalse(old.exists())
            assertTrue(fresh.exists())
        } finally {
            dir.deleteRecursively()
        }
    }
}
