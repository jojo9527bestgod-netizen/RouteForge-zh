package com.routeforge.routing.data

import java.io.File
import java.io.IOException
import java.io.RandomAccessFile

private const val SEGMENT_HEADER_SIZE = 25L * 8L
private const val SEGMENT_OFFSET_MASK = 0xFFFFFFFFFFFFL

/** A cheap check for legacy downloads interrupted before the last indexed block. Full BRouter
 *  integrity verification runs on the download worker before a new file becomes available. */
internal fun hasCompleteSegmentHeader(file: File): Boolean =
    try {
        if (!file.isFile || file.length() < SEGMENT_HEADER_SIZE) {
            false
        } else {
            RandomAccessFile(file, "r").use { input ->
                input.seek(SEGMENT_HEADER_SIZE - 8L)
                val dataEnd = input.readLong() and SEGMENT_OFFSET_MASK
                dataEnd in SEGMENT_HEADER_SIZE..input.length()
            }
        }
    } catch (_: IOException) {
        false
    }
