package com.forgequest.app.data.crash

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CrashReportTest {
    @Test fun formatsHeaderThenTrace() {
        val trace = CrashReport.stackTrace(IllegalStateException("boom"))
        val report = CrashReport.format(0L, "2.17.0 (30)", "15 (API 35)", "Google Pixel 8", "main", trace)
        val lines = report.lines()
        assertEquals("ForgeQuest crash report", lines[0])
        assertEquals("Time: 1970-01-01 00:00:00 UTC", lines[1])
        assertEquals("App: 2.17.0 (30)", lines[2])
        assertEquals("Android: 15 (API 35)", lines[3])
        assertEquals("Device: Google Pixel 8", lines[4])
        assertEquals("Thread: main", lines[5])
        assertEquals("", lines[6])
        assertTrue(lines[7].startsWith("java.lang.IllegalStateException: boom"))
        assertTrue(report.contains("CrashReportTest"))
    }

    @Test fun cutsVeryLongTraces() {
        val report = CrashReport.format(0L, "v", "a", "d", "t", "x".repeat(50_000))
        assertTrue(report.length < 17_000)
        assertTrue(report.endsWith("... (cut)"))
    }
}
