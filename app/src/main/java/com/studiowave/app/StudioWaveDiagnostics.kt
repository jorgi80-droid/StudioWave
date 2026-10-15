package com.studiowave.app

import android.os.Build
import android.os.Debug
import android.os.Process
import android.os.SystemClock
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.ConcurrentLinkedDeque
import java.util.concurrent.atomic.AtomicLong

/**
 * Local diagnostics for StudioWave. Records stay on-device and are exported only after
 * an explicit user action. This intentionally captures app-owned events, not privileged
 * system Logcat, which Android does not expose to ordinary apps.
 */
internal object StudioWaveDiagnostics {
    private const val MAX_ENTRIES = 3000
    private val entries = ConcurrentLinkedDeque<String>()
    private val lastCpuMs = AtomicLong(-1L)
    private val lastWallMs = AtomicLong(-1L)
    private val previousHandler = AtomicLong(0L)
    @Volatile private var frameIntervalNanos = 0L
    @Volatile private var frameSamples = 0L
    @Volatile private var underrunLast = 0L
    @Volatile private var underrunTotal = 0L
    @Volatile private var underrunEvents = 0L
    @Volatile private var crashHandlerInstalled = false

    data class RuntimeSnapshot(
        val cpuPercent: Float?,
        val javaHeapUsedBytes: Long,
        val javaHeapMaxBytes: Long,
        val nativeHeapUsedBytes: Long,
        val availableProcessors: Int,
        val uiFps: Float?,
        val uiFrameIntervalMs: Float?,
        val uptimeSeconds: Long
    )

    @Synchronized
    fun log(level: String = "INFO", tag: String = "APP", message: String) {
        val timestamp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US).format(Date())
        val line = "$timestamp [${level.uppercase(Locale.US)}] [$tag] $message"
        entries.addLast(line)
        while (entries.size > MAX_ENTRIES) entries.pollFirst()
    }

    fun snapshot(limit: Int = 3000): List<String> =
        entries.toList().takeLast(limit.coerceIn(1, MAX_ENTRIES))

    fun formattedReport(extra: List<String> = emptyList()): String = buildString {
        appendLine("StudioWave diagnostic report")
        appendLine("Generated: ${Date()}")
        appendLine("App-owned event log; system Logcat is not included.")
        appendLine("Android: ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})")
        appendLine("Device: ${Build.MANUFACTURER} ${Build.MODEL}")
        appendLine("Processors: ${Runtime.getRuntime().availableProcessors()}")
        appendLine()
        if (extra.isNotEmpty()) {
            appendLine("=== LIVE SNAPSHOT ===")
            extra.forEach(::appendLine)
            appendLine()
        }
        appendLine("=== APP EVENT LOG (oldest to newest) ===")
        snapshot().forEach(::appendLine)
    }

    fun installCrashHandler() {
        if (crashHandlerInstalled) return
        synchronized(this) {
            if (crashHandlerInstalled) return
            val previous = Thread.getDefaultUncaughtExceptionHandler()
            Thread.setDefaultUncaughtExceptionHandler { thread, error ->
                log("FATAL", "CRASH", "Uncaught exception on thread ${thread.name}: ${error.javaClass.name}: ${error.message}")
                error.stackTraceToString().lineSequence().forEach { log("FATAL", "CRASH", it) }
                previous?.uncaughtException(thread, error)
            }
            crashHandlerInstalled = true
            log("INFO", "APP", "Crash diagnostic handler installed")
        }
    }

    /** Call from the UI monitor; this is an app-process estimate, not a per-core profiler. */
    @Synchronized
    fun runtimeSnapshot(): RuntimeSnapshot {
        val nowWall = SystemClock.elapsedRealtime()
        val nowCpu = Process.getElapsedCpuTime()
        val priorWall = lastWallMs.getAndSet(nowWall)
        val priorCpu = lastCpuMs.getAndSet(nowCpu)
        val cpu = if (priorWall >= 0L && nowWall > priorWall && nowCpu >= priorCpu) {
            ((nowCpu - priorCpu).toDouble() / (nowWall - priorWall).toDouble() * 100.0)
                .toFloat().coerceIn(0f, Runtime.getRuntime().availableProcessors() * 100f)
        } else null
        val runtime = Runtime.getRuntime()
        val heapMax = runtime.maxMemory().coerceAtLeast(1L)
        val heapUsed = (runtime.totalMemory() - runtime.freeMemory()).coerceAtLeast(0L)
        val frames = frameSamples
        val meanInterval = if (frames > 0) frameIntervalNanos.toDouble() / frames / 1_000_000.0 else Double.NaN
        return RuntimeSnapshot(
            cpuPercent = cpu,
            javaHeapUsedBytes = heapUsed,
            javaHeapMaxBytes = heapMax,
            nativeHeapUsedBytes = Debug.getNativeHeapAllocatedSize().coerceAtLeast(0L),
            availableProcessors = runtime.availableProcessors().coerceAtLeast(1),
            uiFps = if (meanInterval.isFinite() && meanInterval > 0) (1000.0 / meanInterval).toFloat() else null,
            uiFrameIntervalMs = if (meanInterval.isFinite()) meanInterval.toFloat() else null,
            uptimeSeconds = nowWall / 1000L
        )
    }

    @Synchronized
    fun recordUiFrameInterval(intervalNanos: Long) {
        if (intervalNanos <= 0L || intervalNanos > 1_000_000_000L) return
        // Exponential mean avoids allocating a history buffer and reacts to stutters.
        frameIntervalNanos = if (frameSamples == 0L) intervalNanos
            else (frameIntervalNanos * 0.90 + intervalNanos * 0.10).toLong()
        frameSamples = (frameSamples + 1L).coerceAtMost(1_000_000L)
    }

    @Synchronized
    fun recordUnderruns(total: Long) {
        val safeTotal = total.coerceAtLeast(0L)
        val before = underrunLast
        underrunLast = safeTotal
        underrunTotal = safeTotal
        if (safeTotal > before) {
            val delta = safeTotal - before
            underrunEvents += delta
            log("WARN", "AUDIO", "Output underrun counter increased by $delta (total=$safeTotal)")
        }
    }

    fun currentUnderruns(): Long = underrunTotal
    fun underrunEvents(): Long = underrunEvents
}
