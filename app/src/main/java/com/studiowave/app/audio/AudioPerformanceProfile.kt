package com.studiowave.app.audio

import android.app.ActivityManager
import android.content.Context
import android.media.AudioManager
import android.os.Build
import kotlin.math.max
import kotlin.math.min

/**
 * Device-aware settings for the mobile audio workload.
 *
 * These values are recommendations, not promises about the device's actual audio route:
 * Android audio policy and OEM drivers can override the requested stream configuration.
 */
internal object AudioPerformanceProfile {
    data class DeviceCapabilities(
        val cpuCores: Int,
        val memoryClassMb: Int,
        val lowRamDevice: Boolean,
        val outputSampleRateHz: Int?,
        val outputBurstFrames: Int?
    )

    fun capabilities(context: Context): DeviceCapabilities {
        val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
        val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
        val rate = audioManager?.getProperty(AudioManager.PROPERTY_OUTPUT_SAMPLE_RATE)
            ?.toIntOrNull()?.takeIf { it in 8000..192000 }
        val burst = audioManager?.getProperty(AudioManager.PROPERTY_OUTPUT_FRAMES_PER_BUFFER)
            ?.toIntOrNull()?.takeIf { it in 16..8192 }
        return DeviceCapabilities(
            cpuCores = Runtime.getRuntime().availableProcessors().coerceIn(1, 64),
            memoryClassMb = (activityManager?.memoryClass ?: 192).coerceIn(32, 8192),
            lowRamDevice = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
                activityManager?.isLowRamDevice == true
            } else false,
            outputSampleRateHz = rate,
            outputBurstFrames = burst
        )
    }

    fun recommendedProfile(device: DeviceCapabilities): String = when {
        device.lowRamDevice || device.memoryClassMb <= 192 || device.cpuCores <= 4 -> "STABLE"
        device.cpuCores >= 8 && device.memoryClassMb >= 384 -> "LOW_LATENCY"
        else -> "BALANCED"
    }

    fun effectiveProfile(context: Context, requested: String): String {
        val safe = requested.takeIf { it in setOf("AUTO", "LOW_LATENCY", "BALANCED", "STABLE") } ?: "AUTO"
        return if (safe == "AUTO") recommendedProfile(capabilities(context)) else safe
    }

    fun queueTargetFrames(context: Context, requested: String): Int {
        val device = capabilities(context)
        return queueTargetFrames(effectiveProfile(context, requested), device.outputBurstFrames)
    }

    internal fun queueTargetFrames(profile: String, outputBurstFrames: Int?): Int {
        val profileTarget = when (profile) {
            "LOW_LATENCY" -> 512
            "STABLE" -> 2048
            else -> 1024
        }
        // Respect the device's reported output quantum; request at least two bursts when
        // the hardware requires a larger quantum, but keep a hard practical upper bound.
        val hardwareMinimum = outputBurstFrames?.let { it.coerceIn(16, 8192) * 2 } ?: 0
        return max(profileTarget, hardwareMinimum).coerceIn(256, 4096)
    }

    fun decoderParallelism(context: Context, requested: String, parallelPreparation: Boolean): Int {
        if (!parallelPreparation) return 1
        val device = capabilities(context)
        return when (effectiveProfile(context, requested)) {
            "LOW_LATENCY" -> min(4, max(2, device.cpuCores / 2))
            "STABLE" -> if (device.cpuCores <= 2 || device.memoryClassMb < 128) 1 else 2
            else -> min(3, max(2, device.cpuCores / 3))
        }
    }

    fun describeDevice(context: Context, requested: String, parallelPreparation: Boolean = true): String {
        val device = capabilities(context)
        val effective = effectiveProfile(context, requested)
        val ram = if (device.lowRamDevice) "low-RAM device" else "standard memory class"
        val rate = device.outputSampleRateHz?.let { "${it} Hz" } ?: "unknown"
        val burst = device.outputBurstFrames?.toString() ?: "unknown"
        val queue = queueTargetFrames(effective, device.outputBurstFrames)
        val decoders = decoderParallelism(context, requested, parallelPreparation)
        return "Profile $requested → $effective · CPU ${device.cpuCores} cores · memory class ${device.memoryClassMb} MiB ($ram) · Android output ${rate} · burst ${burst} frames · producer queue ${queue} frames · decoder workers ${decoders}"
    }
}
