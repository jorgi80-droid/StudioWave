package com.studiowave.app

import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.studiowave.app.audio.DirectWaveSampler
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private data class DirectWavePreset(
    val category: String,
    val title: String,
    val description: String,
    val resourceId: Int
)

/**
 * Touch-first Live-inspired device surface. Controls update the actual SoundPool sampler.
 * The six CC0 drum assets are supplied by the CI workflow into assets/samples and copied to
 * app-private storage on first use, so a fresh APK works offline after build-time download.
 */
@Composable
internal fun DirectWaveDevicePanel(
    trackId: String,
    trackName: String,
    onClose: () -> Unit,
    fullScreen: Boolean = false
) {
    val context = LocalContext.current
    val prefs = remember(context) { context.getSharedPreferences("studiowave_directwave", android.content.Context.MODE_PRIVATE) }
    val sampler = remember(trackId) { DirectWaveSampler(context) }
    var sampleUri by remember(trackId) { mutableStateOf<String?>(null) }
    var sampleName by remember(trackId) { mutableStateOf("hard-kick-01.wav") }
    var sampleLoaded by remember(trackId) { mutableStateOf(false) }
    var sampleLoading by remember(trackId) { mutableStateOf(false) }
    var browserOpen by remember(trackId) { mutableStateOf(false) }
    var category by remember(trackId) { mutableStateOf("ALL") }
    var loop by remember(trackId) { mutableStateOf(false) }
    var pitch by remember(trackId) { mutableFloatStateOf(0f) }
    var fine by remember(trackId) { mutableFloatStateOf(0f) }
    var level by remember(trackId) { mutableFloatStateOf(0.8f) }
    var attack by remember(trackId) { mutableFloatStateOf(5f) }
    var decay by remember(trackId) { mutableFloatStateOf(120f) }
    var sustain by remember(trackId) { mutableFloatStateOf(0.8f) }
    var release by remember(trackId) { mutableFloatStateOf(180f) }
    var waveform by remember(sampleUri) { mutableStateOf<List<Float>>(emptyList()) }

    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            runCatching {
                context.contentResolver.takePersistableUriPermission(uri, android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            sampleUri = uri.toString()
            sampleName = uri.lastPathSegment?.substringAfterLast('/') ?: "Imported WAV"
            sampleLoaded = false
            sampleLoading = true
            prefs.edit().putString("source.$trackId", sampleUri).putString("name.$trackId", sampleName).apply()
            sampler.load(uri) { ready ->
                sampleLoaded = ready
                sampleLoading = false
            }
        }
    }

    fun loadUri(uri: Uri, label: String) {
        sampleUri = uri.toString()
        sampleName = label
        sampleLoaded = false
        sampleLoading = true
        prefs.edit().putString("source.$trackId", sampleUri).putString("name.$trackId", label).apply()
        sampler.load(uri) { ready ->
            sampleLoaded = ready
            sampleLoading = false
        }
    }

    LaunchedEffect(trackId, sampler) {
        pitch = prefs.getInt("pitch.$trackId", 0).toFloat().coerceIn(-48f, 48f)
        fine = prefs.getInt("fine.$trackId", 0).toFloat().coerceIn(-100f, 100f)
        level = prefs.getFloat("level.$trackId", 0.8f).coerceIn(0f, 1f)
        attack = prefs.getInt("attack.$trackId", 5).toFloat().coerceIn(0f, 5000f)
        decay = prefs.getInt("decay.$trackId", 120).toFloat().coerceIn(0f, 5000f)
        sustain = prefs.getFloat("sustain.$trackId", 0.8f).coerceIn(0f, 1f)
        release = prefs.getInt("release.$trackId", 180).toFloat().coerceIn(0f, 5000f)
        loop = prefs.getBoolean("loop.$trackId", false)
        sampler.pitchSemitones = pitch.toInt()
        sampler.fineTuneCents = fine.toInt()
        sampler.outputGain = level
        sampler.attackMs = attack.toInt()
        sampler.decayMs = decay.toInt()
        sampler.sustain = sustain
        sampler.releaseMs = release.toInt()
        sampler.looping = loop
        val saved = prefs.getString("source.$trackId", null)
        val savedName = prefs.getString("name.$trackId", null)
        if (!saved.isNullOrBlank()) {
            sampleUri = saved
            sampleName = savedName ?: "Saved sample"
            sampleLoading = true
            sampler.load(Uri.parse(saved)) { ready ->
                sampleLoaded = ready
                sampleLoading = false
            }
        } else {
            withContext(Dispatchers.IO) {
                val file = copyBundledSampleToCache(context, "hard-kick-01.wav")
                file
            }?.let { target -> loadUri(Uri.fromFile(target), target.name) }
        }
    }
    DisposableEffect(sampler) { onDispose { sampler.close() } }

    LaunchedEffect(sampleUri) {
        waveform = emptyList()
        val uri = sampleUri?.let(Uri::parse) ?: return@LaunchedEffect
        waveform = withContext(Dispatchers.IO) {
            runCatching { decodeDirectWavePreview(context, uri, 128) }.getOrDefault(emptyList())
        }
    }

    Column(
        (if (fullScreen) Modifier.fillMaxSize() else Modifier.widthIn(min = 300.dp, max = 900.dp).fillMaxWidth())
            .background(LivePalette.canvas, RoundedCornerShape(4.dp))
            .border(1.dp, LivePalette.border, RoundedCornerShape(4.dp))
            .padding(4.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        LiveDeviceFrame(
            title = "DIRECTWAVE",
            subtitle = "SAMPLER  /  ${trackName.uppercase()}",
            modifier = Modifier.fillMaxWidth(),
            headerActions = {
                Text(
                    when { sampleLoaded -> "READY"; sampleLoading -> "LOADING"; else -> "NO SAMPLE" },
                    color = if (sampleLoaded) LivePalette.aqua else LivePalette.orange,
                    fontSize = 7.sp, fontWeight = FontWeight.Black
                )
                LiveStateButton("BROWSE", browserOpen, accent = LivePalette.yellow) { browserOpen = !browserOpen }
                LiveStateButton(if (loop) "LOOP ON" else "LOOP OFF", loop, accent = LivePalette.orange) {
                    loop = !loop
                    sampler.looping = loop
                    prefs.edit().putBoolean("loop.$trackId", loop).apply()
                }
                LiveStateButton("LOAD WAV", false) { launcher.launch(arrayOf("audio/wav", "audio/x-wav", "audio/*")) }
                LiveStateButton("×", false, accent = LivePalette.orange, onClick = onClose)
            }
        ) {
            Row(
                Modifier.fillMaxWidth().heightIn(min = if (fullScreen) 150.dp else 90.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Column(
                    Modifier.weight(1.15f).fillMaxHeight().background(LivePalette.panel, RoundedCornerShape(3.dp)).padding(5.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    LiveSectionLabel("SAMPLE SOURCE", color = LivePalette.yellow)
                    Text(sampleName.uppercase(), color = LivePalette.text, fontSize = 9.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                    Text(
                        if (sampleLoaded) "SOURCE READY • OFFLINE AVAILABLE" else if (sampleLoading) "DECODING SAMPLE…" else "CHOOSE A WAV SOURCE",
                        color = if (sampleLoaded) LivePalette.aqua else LivePalette.muted, fontSize = 7.sp, maxLines = 1
                    )
                    DirectWaveWaveform(waveform, Modifier.fillMaxWidth().weight(1f).heightIn(min = 35.dp))
                }
                Column(
                    Modifier.weight(1.7f).fillMaxHeight().background(LivePalette.panel, RoundedCornerShape(3.dp)).padding(4.dp),
                    verticalArrangement = Arrangement.spacedBy(1.dp)
                ) {
                    LiveSectionLabel("TUNE / OUTPUT")
                    Row(Modifier.fillMaxWidth().weight(1f), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
                        LiveKnob("PITCH", pitch, -48f..48f, "${pitch.toInt()} st", modifier = Modifier.weight(1f), diameter = if (fullScreen) 64.dp else 46.dp) {
                            pitch = it; sampler.pitchSemitones = it.toInt()
                            prefs.edit().putInt("pitch.$trackId", it.toInt()).apply()
                        }
                        LiveKnob("FINE", fine, -100f..100f, "${fine.toInt()} ct", modifier = Modifier.weight(1f), diameter = if (fullScreen) 64.dp else 46.dp) {
                            fine = it; sampler.fineTuneCents = it.toInt()
                            prefs.edit().putInt("fine.$trackId", it.toInt()).apply()
                        }
                        LiveKnob("LEVEL", level, 0f..1f, "${(level * 100).roundToInt()}%", modifier = Modifier.weight(1f), diameter = if (fullScreen) 64.dp else 46.dp) {
                            level = it; sampler.outputGain = it
                            prefs.edit().putFloat("level.$trackId", it).apply()
                        }
                    }
                }
            }

            Column(
                Modifier.fillMaxWidth().background(LivePalette.panel, RoundedCornerShape(3.dp)).padding(horizontal = 5.dp, vertical = 4.dp)
            ) {
                LiveSectionLabel("AMP ENVELOPE  /  ADSR")
                Row(Modifier.fillMaxWidth().height(if (fullScreen) 110.dp else 83.dp), horizontalArrangement = Arrangement.spacedBy(2.dp), verticalAlignment = Alignment.CenterVertically) {
                    LiveKnob("ATTACK", attack, 0f..2000f, "${attack.roundToInt()} ms", modifier = Modifier.weight(1f), diameter = if (fullScreen) 60.dp else 43.dp) {
                        attack = it; sampler.attackMs = it.toInt()
                        prefs.edit().putInt("attack.$trackId", it.toInt()).apply()
                    }
                    LiveKnob("DECAY", decay, 0f..2000f, "${decay.roundToInt()} ms", modifier = Modifier.weight(1f), diameter = if (fullScreen) 60.dp else 43.dp) {
                        decay = it; sampler.decayMs = it.toInt()
                        prefs.edit().putInt("decay.$trackId", it.toInt()).apply()
                    }
                    LiveKnob("SUSTAIN", sustain, 0f..1f, "${(sustain * 100).roundToInt()}%", modifier = Modifier.weight(1f), diameter = if (fullScreen) 60.dp else 43.dp) {
                        sustain = it; sampler.sustain = it
                        prefs.edit().putFloat("sustain.$trackId", it).apply()
                    }
                    LiveKnob("RELEASE", release, 0f..2000f, "${release.roundToInt()} ms", modifier = Modifier.weight(1f), diameter = if (fullScreen) 60.dp else 43.dp) {
                        release = it; sampler.releaseMs = it.toInt()
                        prefs.edit().putInt("release.$trackId", it.toInt()).apply()
                    }
                }
            }

            Row(Modifier.fillMaxWidth().height(38.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                Text("TOUCH KEYS", color = LivePalette.muted, fontSize = 7.sp, fontWeight = FontWeight.Black)
                val notes = listOf(48,49,50,51,52,53,54,55,56,57,58,59,60,61,62,63,64,65,66,67,68,69,70,71)
                notes.forEach { note ->
                    val black = note % 12 in setOf(1,3,6,8,10)
                    Surface(
                        modifier = Modifier.weight(1f).fillMaxHeight().pointerInput(note, sampleLoaded) {
                            detectTapGestures(onPress = {
                                if (sampleLoaded) sampler.noteOn(note)
                                tryAwaitRelease()
                                sampler.noteOff(note)
                            })
                        },
                        color = if (black) LivePalette.control else Color(0xFFE5E7E8),
                        contentColor = if (black) LivePalette.text else Color(0xFF232528),
                        shape = RoundedCornerShape(2.dp),
                        border = androidx.compose.foundation.BorderStroke(1.dp, LivePalette.border)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            if (!black) Text(listOf("C","D","E","F","G","A","B")[(note % 12).let { when(it){0->0;2->1;4->2;5->3;7->4;9->5;11->6;else->0} }], fontSize = 6.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                        }
                    }
                }
            }
        }

        if (browserOpen) {
            Column(
                Modifier.fillMaxWidth().heightIn(max = if (fullScreen) 220.dp else 130.dp)
                    .background(LivePalette.room, RoundedCornerShape(3.dp)).padding(4.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                    listOf("ALL","KICK","SNARE","HI-HAT","BASS").forEach { cat ->
                        LiveStateButton(cat, category == cat, accent = LivePalette.yellow) { category = cat }
                    }
                }
                val assets = remember { listOf(
                    Triple("KICK","hard-kick-01.wav","Hard kick one-shot"),
                    Triple("SNARE","hard-snare-01.wav","Hard snare one-shot"),
                    Triple("HI-HAT","hi-hat-closed-01.wav","Closed hi-hat one-shot"),
                    Triple("BASS","808-bass-sub.wav","808 sub-bass one-shot"),
                    Triple("KICK","bounce-kick-01.wav","Bounce kick one-shot"),
                    Triple("KICK","vintage-kick-01.wav","Vintage kick one-shot")
                ) }
                assets.filter { category == "ALL" || it.first == category }.forEach { sample ->
                    Row(
                        Modifier.fillMaxWidth().background(if (sampleUri?.endsWith(sample.second) == true) LivePalette.raised else LivePalette.panel, RoundedCornerShape(2.dp))
                            .clickable {
                                val target = copyBundledSampleToCache(context, sample.second)
                                if (target == null) {
                                    Toast.makeText(context, "Sample belum tersedia. Build ulang agar library bawaan terunduh.", Toast.LENGTH_LONG).show()
                                } else {
                                    loadUri(Uri.fromFile(target), sample.second)
                                    browserOpen = false
                                }
                            }.padding(horizontal = 7.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(Modifier.size(5.dp).background(LivePalette.orange, RoundedCornerShape(1.dp)))
                        Spacer(Modifier.width(7.dp))
                        Column(Modifier.weight(1f)) {
                            Text(sample.second.uppercase(), color = LivePalette.text, fontSize = 8.sp, fontWeight = FontWeight.Black)
                            Text(sample.third, color = LivePalette.muted, fontSize = 7.sp)
                        }
                        Text(if (sampleLoaded && sampleUri?.endsWith(sample.second) == true) "LOADED" else "LOAD", color = LivePalette.aqua, fontSize = 7.sp, fontWeight = FontWeight.Black)
                    }
                }
            }
        }
    }
}

@Composable
private fun DirectWaveWaveform(peaks: List<Float>, modifier: Modifier = Modifier) {
    Canvas(modifier.background(LivePalette.room, RoundedCornerShape(2.dp)).padding(3.dp)) {
        val centerY = size.height / 2f
        drawLine(LivePalette.border, Offset(0f, centerY), Offset(size.width, centerY), 1f)
        if (peaks.isNotEmpty()) {
            val dx = size.width / peaks.size
            peaks.forEachIndexed { i, value ->
                val h = value.coerceIn(0f, 1f) * size.height * 0.45f
                drawLine(LivePalette.aqua, Offset((i + 0.5f) * dx, centerY - h), Offset((i + 0.5f) * dx, centerY + h), dx.coerceIn(1f, 2f))
            }
        }
    }
}

private fun copyBundledSampleToCache(context: android.content.Context, fileName: String): java.io.File? {
    return try {
        val target = java.io.File(context.filesDir, "factory-samples/$fileName")
        if (!target.exists() || target.length() < 44L) {
            val dir = target.parentFile ?: return null
            if (!dir.exists() && !dir.mkdirs()) return null
            context.assets.open("samples/$fileName").use { input -> target.outputStream().use { output -> input.copyTo(output) } }
        }
        if (target.length() >= 44L) target else null
    } catch (_: Exception) { null }
}

/** Decode waveform from the exact selected source; no synthetic preview for unknown content. */
private fun decodeDirectWavePreview(context: android.content.Context, uri: Uri, bins: Int): List<Float> {
    val extractor = android.media.MediaExtractor()
    var decoder: android.media.MediaCodec? = null
    try {
        extractor.setDataSource(context, uri, null)
        var audioTrack = -1
        var format: android.media.MediaFormat? = null
        for (i in 0 until extractor.trackCount) {
            val candidate = extractor.getTrackFormat(i)
            if (candidate.getString(android.media.MediaFormat.KEY_MIME)?.startsWith("audio/") == true) {
                audioTrack = i; format = candidate; break
            }
        }
        val input = format ?: return emptyList()
        extractor.selectTrack(audioTrack)
        decoder = android.media.MediaCodec.createDecoderByType(input.getString(android.media.MediaFormat.KEY_MIME) ?: return emptyList())
        decoder.configure(input, null, null, 0); decoder.start()
        val peaks = FloatArray(bins)
        val info = android.media.MediaCodec.BufferInfo()
        var inputDone = false
        var outputDone = false
        var count = 0
        val durationUs = if (input.containsKey(android.media.MediaFormat.KEY_DURATION)) input.getLong(android.media.MediaFormat.KEY_DURATION).coerceAtLeast(1L) else 1L
        while (!outputDone && count++ < 1200) {
            if (!inputDone) {
                val ix = decoder.dequeueInputBuffer(10000)
                if (ix >= 0) {
                    val buffer = decoder.getInputBuffer(ix) ?: continue
                    buffer.clear()
                    val bytes = extractor.readSampleData(buffer, 0)
                    if (bytes < 0) { decoder.queueInputBuffer(ix,0,0,0,android.media.MediaCodec.BUFFER_FLAG_END_OF_STREAM); inputDone = true }
                    else { decoder.queueInputBuffer(ix,0,bytes,extractor.sampleTime.coerceAtLeast(0L),0); extractor.advance() }
                }
            }
            when (val ix = decoder.dequeueOutputBuffer(info,10000)) {
                android.media.MediaCodec.INFO_TRY_AGAIN_LATER -> Unit
                android.media.MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> Unit
                else -> if (ix >= 0) {
                    try {
                        val buffer = decoder.getOutputBuffer(ix)
                        if (buffer != null && info.size > 0) {
                            val sr = decoder.outputFormat.getInteger(android.media.MediaFormat.KEY_SAMPLE_RATE).coerceAtLeast(1)
                            val channels = decoder.outputFormat.getInteger(android.media.MediaFormat.KEY_CHANNEL_COUNT).coerceIn(1,8)
                            val slice = buffer.duplicate().order(java.nio.ByteOrder.nativeOrder())
                            slice.position(info.offset); slice.limit(info.offset + info.size)
                            val pcm = slice.slice().order(java.nio.ByteOrder.nativeOrder())
                            val frames = info.size / 2 / channels
                            for (frame in 0 until frames) {
                                val magnitude = (pcm.getShort(frame * channels * 2).toInt() / 32768f).let(kotlin.math::abs)
                                val timeUs = info.presentationTimeUs + frame.toLong() * 1000000L / sr
                                val bin = ((timeUs.toDouble() / durationUs) * bins).toInt().coerceIn(0,bins-1)
                                peaks[bin] = maxOf(peaks[bin], magnitude)
                            }
                        }
                        if (info.flags and android.media.MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) outputDone = true
                    } finally { decoder.releaseOutputBuffer(ix, false) }
                }
            }
        }
        return peaks.toList()
    } finally {
        runCatching { decoder?.stop() }; runCatching { decoder?.release() }; runCatching { extractor.release() }
    }
}
