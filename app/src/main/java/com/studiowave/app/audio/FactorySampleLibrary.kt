package com.studiowave.app.audio

import java.io.ByteArrayOutputStream
import java.io.DataOutputStream
import kotlin.math.PI
import kotlin.math.sin
import kotlin.math.tanh

/** Creates short, original, synthesized WAV starter voices for the factory browser. */
object FactorySampleLibrary {
    data class Preset(val name: String, val category: String, val fileName: String, val description: String)

    val presets = listOf(
        Preset("Arp Pluck", "Arp", "arp_pluck.wav", "Bright plucked arpeggio"),
        Preset("Sub Bass", "Bass", "sub_bass.wav", "Clean low sine bass"),
        Preset("Saw Lead", "Lead", "saw_lead.wav", "Bright saw lead"),
        Preset("Soft Keys", "Keyboard", "soft_keys.wav", "Soft electric-key tone"),
        Preset("Orchestral Pad", "Orchestra", "orchestra_pad.wav", "Original synthetic orchestral-like sustain"),
        Preset("Analog Synth", "Synth", "analog_synth.wav", "Detuned saw synth"),
        Preset("Warm Pad", "Pad", "warm_pad.wav", "Slow warm sustained texture")
    )

    /** 16-bit mono PCM, 22.05 kHz. Each tone is generated deterministically and needs no network access. */
    fun wavBytes(index: Int, seconds: Double = 2.0, sampleRate: Int = 22050): ByteArray {
        val count = (seconds.coerceIn(0.2, 4.0) * sampleRate).toInt()
        val pcm = ByteArray(count * 2)
        val frequencies = doubleArrayOf(880.0, 55.0, 440.0, 261.63, 220.0, 329.63, 196.0)
        val base = frequencies[index.coerceIn(0, frequencies.lastIndex)]
        for (i in 0 until count) {
            val t = i.toDouble() / sampleRate
            val env = when (index) {
                0 -> kotlin.math.exp(-t * 2.2) * (1.0 - kotlin.math.exp(-t * 90.0))
                1 -> (1.0 - kotlin.math.exp(-t * 20.0)) * kotlin.math.exp(-t * 0.35)
                2, 5 -> (1.0 - kotlin.math.exp(-t * 18.0)) * kotlin.math.exp(-t * 0.7)
                3 -> kotlin.math.exp(-t * 1.4) * (1.0 - kotlin.math.exp(-t * 30.0))
                4, 6 -> (1.0 - kotlin.math.exp(-t * 2.8)) * (0.88 + 0.12 * sin(2.0 * PI * 0.7 * t))
                else -> 1.0
            }.coerceIn(0.0, 1.0)
            val phase = 2.0 * PI * base * t
            val value = when (index) {
                0 -> 0.58 * sin(phase) + 0.23 * sin(phase * 2.0) + 0.10 * sin(phase * 3.0)
                1 -> sin(phase) * 0.86
                2 -> tanh(2.2 * (2.0 * ((base * t) % 1.0) - 1.0)) * 0.7
                3 -> 0.65 * sin(phase) + 0.25 * sin(phase * 2.01) + 0.1 * sin(phase * 3.0)
                4 -> 0.45 * sin(phase) + 0.28 * sin(phase * 1.498) + 0.18 * sin(phase * 2.01)
                5 -> 0.45 * sin(phase) + 0.35 * sin(phase * 1.006) + 0.15 * sin(phase * 2.0)
                else -> 0.5 * sin(phase) + 0.28 * sin(phase * 1.005) + 0.14 * sin(phase * 0.5)
            }
            val fade = minOf(1.0, (count - i).toDouble() / (sampleRate * 0.04))
            val sample = (value * env * fade * 0.75 * 32767.0).toInt().coerceIn(-32768, 32767)
            pcm[i * 2] = (sample and 0xff).toByte()
            pcm[i * 2 + 1] = ((sample shr 8) and 0xff).toByte()
        }
        val out = ByteArrayOutputStream(44 + pcm.size)
        val data = DataOutputStream(out)
        fun ascii(value: String) = data.writeBytes(value)
        fun le16(value: Int) { data.writeByte(value and 0xff); data.writeByte((value shr 8) and 0xff) }
        fun le32(value: Int) { le16(value); le16(value ushr 16) }
        ascii("RIFF"); le32(36 + pcm.size); ascii("WAVEfmt "); le32(16); le16(1); le16(1)
        le32(sampleRate); le32(sampleRate * 2); le16(2); le16(16); ascii("data"); le32(pcm.size); data.write(pcm)
        return out.toByteArray()
    }
}
