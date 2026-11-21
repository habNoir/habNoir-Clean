package xyz.habnoir.clean.model

import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.Locale

class ByteFormatTest {

    private val idLocale = Locale.forLanguageTag("id-ID")

    @Test
    fun `formats bytes cleanly across units`() {
        assertEquals("0 B", ByteFormat.format(-10L, idLocale))
        assertEquals("512 B", ByteFormat.format(512L, idLocale))
        assertEquals("45 KB", ByteFormat.format(45_000L, idLocale))
        assertEquals("12,5 MB", ByteFormat.format(12_500_000L, idLocale))
        assertEquals("615 MB", ByteFormat.format(615_000_000L, idLocale))
        assertEquals("3,42 GB", ByteFormat.format(3_420_000_000L, idLocale))
    }
}
