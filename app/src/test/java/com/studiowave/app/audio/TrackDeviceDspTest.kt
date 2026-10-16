package com.studiowave.app.audio

import com.studiowave.app.data.TrackDevice
import com.studiowave.app.data.defaultDeviceParameters
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs
import kotlin.math.sin

class TrackDeviceDspTest {
    private fun impulse(frames: Int = 1024): FloatArray = FloatArray(frames * 2).also { it[0] = 0.8f; it[1] = -0.6f }

    @Test fun bypassDoesNotChangeSignalOrMaster() {
        val engine = TrackDeviceDsp(44100)
        val input = impulse(64); val original = input.copyOf(); val master = FloatArray(input.size)
        engine.process(input, master, input.size, 1f, emptyList())
        assertTrue(original.contentEquals(input)); assertTrue(master.all { it == 0f })
    }

    @Test fun filterProcessesStereoAndKeepsSamplesFinite() {
        val engine = TrackDeviceDsp(44100)
        val input = FloatArray(4096) { index -> val frame=index/2; if(index%2==0) sin(frame*0.47).toFloat() else sin(frame*0.47+0.15).toFloat() }
        val master=FloatArray(input.size)
        engine.process(input,master,input.size,1f,listOf(TrackDevice(type="FILTER",name="Auto Filter",parameters=defaultDeviceParameters("FILTER")+("cutoffHz" to 250f))))
        assertTrue(input.all{it.isFinite()}); assertTrue(master.any{abs(it)>0.0001f})
    }

    @Test fun saturatorChangesSignalAndKeepsOutputBounded() {
        val engine=TrackDeviceDsp(44100);val input=FloatArray(512){if(it%2==0)0.8f else -0.8f};val master=FloatArray(input.size)
        engine.process(input,master,input.size,1f,listOf(TrackDevice(type="SATURATOR",name="Saturator",parameters=defaultDeviceParameters("SATURATOR")+mapOf("drive" to 6f,"wet" to 1f))))
        assertTrue(input.all{it.isFinite()&&abs(it)<=1f});assertTrue(master.any{abs(it)>0.001f})
    }

    @Test fun delayStateContinuesAcrossBlocks() {
        val engine=TrackDeviceDsp(8000)
        val delay=TrackDevice(type="DELAY",name="Delay",parameters=mapOf("timeMs" to 100f,"feedback" to 0f,"wet" to 1f))
        val first=impulse(256);engine.process(first,FloatArray(first.size),first.size,1f,listOf(delay))
        val second=FloatArray(2048);engine.process(second,FloatArray(second.size),second.size,1f,listOf(delay))
        assertTrue(second.any{abs(it)>0.01f});assertTrue(second.all{it.isFinite()})
    }

    @Test fun preparedFilterRefreshesParametersWithoutChangingItsChain() {
        val engine = TrackDeviceDsp(44100)
        val firstDevice = TrackDevice(
            type = "FILTER",
            name = "Live Filter",
            parameters = defaultDeviceParameters("FILTER") + ("cutoffHz" to 12000f)
        )
        engine.prepare(listOf(firstDevice))

        val inputA = FloatArray(8192) { index -> sin(index * 0.19).toFloat() }
        val masterA = FloatArray(inputA.size)
        engine.process(inputA, masterA, inputA.size, 1f, listOf(firstDevice))

        val updatedDevice = firstDevice.copy(
            parameters = firstDevice.parameters + ("cutoffHz" to 120f)
        )
        val inputB = FloatArray(8192) { index -> sin(index * 0.19).toFloat() }
        val masterB = FloatArray(inputB.size)
        engine.process(inputB, masterB, inputB.size, 1f, listOf(updatedDevice))

        assertTrue(masterA.all { it.isFinite() } && masterB.all { it.isFinite() })
        assertTrue(masterA.indices.any { abs(masterA[it] - masterB[it]) > 0.00001f })
    }

    @Test fun compressorPublishesActualBlockGainReduction() {
        val engine = TrackDeviceDsp(44100)
        val compressor = TrackDevice(
            type = "COMPRESSOR",
            name = "Compressor",
            parameters = defaultDeviceParameters("COMPRESSOR") + mapOf(
                "thresholdDb" to -18f, "ratio" to 4f, "attackMs" to 0.1f, "releaseMs" to 120f
            )
        )
        val input = FloatArray(16_384) { 0.95f }
        val master = FloatArray(input.size)
        engine.process(input, master, input.size, 1f, listOf(compressor))
        assertTrue("gain reduction should follow actual compressed signal", engine.gainReductionDbSnapshot() > 3f)
        assertTrue(engine.gainReductionDbSnapshot() <= 60f)

        engine.process(FloatArray(input.size), FloatArray(input.size), input.size, 1f,
            listOf(compressor.copy(enabled = false)))
        assertTrue("bypassing the compressor clears its GR display", engine.gainReductionDbSnapshot() == 0f)
    }

    @Test fun disabledEffectIsBypassed() {
        val engine=TrackDeviceDsp(44100);val input=impulse(64);val original=input.copyOf();val master=FloatArray(input.size)
        engine.process(input,master,input.size,1f,listOf(TrackDevice(type="SATURATOR",name="Off",enabled=false,parameters=defaultDeviceParameters("SATURATOR"))))
        assertTrue(original.contentEquals(input));assertTrue(master.all{it==0f})
    }

    @Test fun parametricEqEightUsesBandFrequencyAndQSettings() {
        val input = FloatArray(8192) { index ->
            val frame = index / 2
            sin(frame * 2.0 * Math.PI * 1000.0 / 44100.0).toFloat() * 0.35f
        }
        fun render(frequencyNorm: Float, q: Float): FloatArray {
            val buffer = input.copyOf()
            val master = FloatArray(input.size)
            val parameters = defaultDeviceParameters("PARAM_EQ_8") + mapOf(
                "band5" to 10f,
                "band5FreqNorm" to frequencyNorm,
                "band5Q" to q
            )
            TrackDeviceDsp(44100).process(
                buffer, master, buffer.size, 1f,
                listOf(TrackDevice(type = "PARAM_EQ_8", name = "Parametric EQ Eight", parameters = parameters))
            )
            assertTrue(master.all { it.isFinite() })
            return master
        }
        val lowBand = render(0.35f, 1.1f)
        val nearTone = render(0.566f, 2.4f)
        assertTrue(nearTone.any { abs(it) > 0.0001f })
        assertTrue(lowBand.indices.any { abs(lowBand[it] - nearTone[it]) > 0.00001f })
    }
}
