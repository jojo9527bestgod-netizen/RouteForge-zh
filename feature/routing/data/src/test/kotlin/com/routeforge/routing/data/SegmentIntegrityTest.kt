package com.routeforge.routing.data

import java.io.DataOutputStream
import java.io.File
import java.io.RandomAccessFile
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir

internal fun File.writeCompleteTileHeader() {
    DataOutputStream(outputStream()).use { output -> repeat(25) { output.writeLong(200L) } }
}

class SegmentIntegrityTest {
    @Test
    fun `a header whose final indexed block is within the file is complete`(@TempDir directory: File) {
        val file = File(directory, "tile.rd5").apply { writeCompleteTileHeader() }
        assertTrue(hasCompleteSegmentHeader(file))
    }

    @Test
    fun `an interrupted segment is incomplete even when its rd5 file already exists`(@TempDir directory: File) {
        val file = File(directory, "tile.rd5").apply { writeCompleteTileHeader() }
        RandomAccessFile(file, "rw").use { it.seek(192L); it.writeLong(11_559_321L) }
        assertFalse(hasCompleteSegmentHeader(file))
    }

    @Test
    fun `missing and truncated headers are incomplete`(@TempDir directory: File) {
        assertFalse(hasCompleteSegmentHeader(File(directory, "missing.rd5")))
        val file = File(directory, "short.rd5").apply { writeBytes(byteArrayOf(1, 2, 3)) }
        assertFalse(hasCompleteSegmentHeader(file))
    }
}
