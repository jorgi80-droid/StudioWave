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

    @Test fun disabledEffectIsBypassed() {
        val engine=TrackDeviceDsp(44100);val input=impulse(64);val original=input.copyOf();val master=FloatArray(input.size)
        engine.process(input,master,input.size,1f,listOf(TrackDevice(type="SATURATOR",name="Off",enabled=false,parameters=defaultDeviceParameters("SATURATOR"))))
        assertTrue(original.contentEquals(input));assertTrue(master.all{it==0f})
    }
}
