package com.studiowave.app.audio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.ByteBuffer
import java.nio.ByteOrder

class PlaybackWavRecorderTest {
    @Test fun writesValidStereoPcmWaveHeader() {
        val header = PlaybackWavRecorder.wavHeader(4_000L, 44_100)
        assertEquals(44, header.size)
        val bytes = ByteBuffer.wrap(header).order(ByteOrder.LITTLE_ENDIAN)
        assertEquals("RIFF", String(header, 0, 4, Charsets.US_ASCII))
        assertEquals(4_036, bytes.getInt(4))
        assertEquals("WAVE", String(header, 8, 4, Charsets.US_ASCII))
        assertEquals(1, bytes.getShort(20).toInt())
        assertEquals(2, bytes.getShort(22).toInt())
        assertEquals(44_100, bytes.getInt(24))
        assertEquals(176_400, bytes.getInt(28))
        assertEquals(4, bytes.getShort(32).toInt())
        assertEquals(16, bytes.getShort(34).toInt())
        assertEquals(4_000, bytes.getInt(40))
    }

    @Test fun rejectsInvalidSampleRateAndOversizedRiffData() {
        assertTrue(runCatching { PlaybackWavRecorder.wavHeader(0, 7_999) }.isFailure)
        assertTrue(runCatching { PlaybackWavRecorder.wavHeader(0xFFFFFFFFL, 44_100) }.isFailure)
    }
}
