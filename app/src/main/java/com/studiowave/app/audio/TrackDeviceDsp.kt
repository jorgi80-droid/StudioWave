package com.studiowave.app.audio

import com.studiowave.app.data.TrackDevice
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.log10
import kotlin.math.max
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.tanh

/** Original StudioWave track insert processors. These are basic native algorithms, not proprietary Ableton effects. */
internal class TrackDeviceDsp(private val sampleRate: Int) {
    private data class Biquad(
        var b0: Double = 1.0, var b1: Double = 0.0, var b2: Double = 0.0, var a1: Double = 0.0, var a2: Double = 0.0,
        var x1L: Double = 0.0, var x2L: Double = 0.0, var y1L: Double = 0.0, var y2L: Double = 0.0,
        var x1R: Double = 0.0, var x2R: Double = 0.0, var y1R: Double = 0.0, var y2R: Double = 0.0
    ) {
        fun processL(x: Double): Double { val y=b0*x+b1*x1L+b2*x2L-a1*y1L-a2*y2L; x2L=x1L; x1L=x; y2L=y1L; y1L=y; return y }
        fun processR(x: Double): Double { val y=b0*x+b1*x1R+b2*x2R-a1*y1R-a2*y2R; x2R=x1R; x1R=x; y2R=y1R; y1R=y; return y }
        fun tune(freq: Double,q: Double,gainDb: Double,sr:Int) {
            val a=10.0.pow(gainDb.coerceIn(-18.0,18.0)/40.0); val w=2*PI*freq.coerceIn(20.0,sr*0.45)/sr
            val alpha=sin(w)/(2*q.coerceIn(0.25,8.0)); val c=kotlin.math.cos(w); val a0=1+alpha/a
            b0=(1+alpha*a)/a0; b1=(-2*c)/a0; b2=(1-alpha*a)/a0; a1=(-2*c)/a0; a2=(1-alpha/a)/a0
        }
    }
    private class State(sampleRate:Int) {
        var lowL=0.0; var lowR=0.0; var envL=0.0; var envR=0.0; var write=0
        val delayL=FloatArray((sampleRate*2.1f).toInt().coerceAtLeast(4096)); val delayR=FloatArray(delayL.size)
        val eq=Array(8){Biquad()}; var eqSignature=Long.MIN_VALUE
    }
    private val states=HashMap<String,State>()
    private var chainSignature=""
    fun process(buffer:FloatArray,master:FloatArray,sampleCount:Int,masterGain:Float,devices:List<TrackDevice>) {
        val active=devices.filter{it.enabled && it.type in EFFECTS}
        val signature=active.joinToString("|"){"${it.id}:${it.type}"}
        if(signature!=chainSignature) {
            val ids=active.map{it.id}.toSet(); states.keys.retainAll(ids)
            active.forEach{states.getOrPut(it.id){State(sampleRate)}}
            chainSignature=signature
        }
        if(active.isEmpty()) return
        val gain=masterGain.coerceIn(0f,1f)

        // Tune EQ filters once per block, not once per stereo frame. Keep temporary
        // list/fold work out of the sample loop to reduce allocations on the audio path.
        active.forEach { device ->
            if(device.type == "PARAM_EQ_8") {
                val st=states.getValue(device.id)
                val p=device.parameters
                var signatureValue=1L
                for(band in 1..8) {
                    val gain=(p["band$band"]?:0f).coerceIn(-18f,18f)
                    val defaultNorm=EQ_DEFAULT_FREQ_NORMS[band - 1]
                    val frequencyNorm=(p["band${band}FreqNorm"]?:defaultNorm).coerceIn(0f,1f)
                    val q=(p["band${band}Q"]?:1.1f).coerceIn(0.25f,8f)
                    signatureValue=signatureValue*31L+gain.toBits()
                    signatureValue=signatureValue*31L+frequencyNorm.toBits()
                    signatureValue=signatureValue*31L+q.toBits()
                }
                if(signatureValue!=st.eqSignature) {
                    for(band in 1..8) {
                        val gainDb=(p["band$band"]?:0f).coerceIn(-18f,18f)
                        val frequencyNorm=(p["band${band}FreqNorm"]?:EQ_DEFAULT_FREQ_NORMS[band - 1]).coerceIn(0f,1f)
                        val frequencyHz=20.0 * 1000.0.pow(frequencyNorm.toDouble())
                        val q=(p["band${band}Q"]?:1.1f).coerceIn(0.25f,8f)
                        st.eq[band - 1].tune(frequencyHz,q.toDouble(),gainDb.toDouble(),sampleRate)
                    }
                    st.eqSignature=signatureValue
                }
            }
        }

        for(i in 0 until sampleCount step 2) {
            val dryL=buffer[i].toDouble(); val dryR=buffer[i+1].toDouble()
            var left=dryL; var right=dryR
            active.forEach { device ->
                val st=states.getValue(device.id); val p=device.parameters
                when(device.type) {
                    "EQ" -> {
                        val lowGain=db(p["lowDb"]?:0f); val midGain=db(p["midDb"]?:0f); val highGain=db(p["highDb"]?:0f)
                        val al=1-exp(-2*PI*180/sampleRate); val ah=1-exp(-2*PI*5200/sampleRate)
                        st.lowL+=al*(left-st.lowL); st.lowR+=al*(right-st.lowR)
                        st.envL+=ah*(left-st.envL); st.envR+=ah*(right-st.envR)
                        val hiL=left-st.envL; val hiR=right-st.envR
                        val midL=left-st.lowL-hiL; val midR=right-st.lowR-hiR
                        left=st.lowL*lowGain+midL*midGain+hiL*highGain
                        right=st.lowR*lowGain+midR*midGain+hiR*highGain
                    }
                    "PARAM_EQ_8" -> {
                        st.eq.forEach { filter -> left=filter.processL(left); right=filter.processR(right) }
                    }
                    "FILTER" -> {
                        val cutoff=(p["cutoffHz"]?:16000f).coerceIn(20f,sampleRate*0.45f)
                        val mode=(p["mode"]?:0f).toInt().coerceIn(0,2)
                        val alpha=1-exp(-2*PI*cutoff/sampleRate)
                        st.lowL+=alpha*(left-st.lowL);st.lowR+=alpha*(right-st.lowR)
                        when(mode){1->{left-=st.lowL;right-=st.lowR};2->{left=(left-st.lowL)*0.7+st.lowL*0.3;right=(right-st.lowR)*0.7+st.lowR*0.3};else->{left=st.lowL;right=st.lowR}}
                    }
                    "SATURATOR" -> {
                        val drive=(p["drive"]?:2.5f).coerceIn(0.2f,12f).toDouble()
                        val wet=(p["wet"]?:0.35f).coerceIn(0f,1f).toDouble()
                        val out=db(p["outputDb"]?:0f); val norm=tanh(drive).coerceAtLeast(0.0001)
                        left=(left*(1-wet)+tanh(left*drive)/norm*wet)*out
                        right=(right*(1-wet)+tanh(right*drive)/norm*wet)*out
                    }
                    "COMPRESSOR" -> {
                        val threshold=(p["thresholdDb"]?:-18f).coerceIn(-60f,0f); val ratio=(p["ratio"]?:3f).coerceIn(1f,20f)
                        val aa=exp(-1.0/((p["attackMs"]?:10f).coerceIn(0.1f,250f)*sampleRate/1000.0))
                        val ra=exp(-1.0/((p["releaseMs"]?:120f).coerceIn(1f,1500f)*sampleRate/1000.0))
                        val absL=abs(left);val absR=abs(right);val la=if(absL>st.envL)aa else ra;val lr=if(absR>st.envR)aa else ra
                        st.envL=la*st.envL+(1-la)*absL;st.envR=lr*st.envR+(1-lr)*absR
                        val detector=max(st.envL,st.envR).coerceAtLeast(1e-7);val inDb=20*log10(detector)
                        val outDb=if(inDb>threshold)threshold+(inDb-threshold)/ratio else inDb
                        val reduction=db((outDb-inDb).toFloat())*db(p["makeupDb"]?:0f)
                        left*=reduction;right*=reduction
                    }
                    "DELAY","REVERB" -> {
                        val ms=if(device.type=="REVERB")((p["decay"]?:1.6f)*1000/7).coerceIn(24f,180f) else (p["timeMs"]?:320f).coerceIn(10f,2000f)
                        val frames=(ms*sampleRate/1000).toInt().coerceIn(1,st.delayL.size-1)
                        if(device.type=="DELAY") {
                            val read=Math.floorMod(st.write-frames,st.delayL.size);val dl=st.delayL[read].toDouble();val dr=st.delayR[read].toDouble()
                            val feedback=(p["feedback"]?:0.32f).coerceIn(0f,0.88f).toDouble();val wet=(p["wet"]?:0.2f).coerceIn(0f,1f).toDouble()
                            st.delayL[st.write]=(left+dl*feedback).toFloat().coerceIn(-4f,4f);st.delayR[st.write]=(right+dr*feedback).toFloat().coerceIn(-4f,4f)
                            left=left*(1-wet)+dl*wet;right=right*(1-wet)+dr*wet
                        } else {
                            val room=(p["room"]?:0.45f).coerceIn(0.05f,0.95f).toDouble();val wet=(p["wet"]?:0.22f).coerceIn(0f,1f).toDouble()
                            val tap1=frames
                            val tap2=(frames*1.31f).toInt().coerceAtMost(st.delayL.size-1)
                            val tap3=(frames*1.73f).toInt().coerceAtMost(st.delayL.size-1)
                            val a=st.delayL[Math.floorMod(st.write-tap1,st.delayL.size)].toDouble()
                            val b=st.delayL[Math.floorMod(st.write-tap2,st.delayL.size)].toDouble()
                            val c=st.delayL[Math.floorMod(st.write-tap3,st.delayL.size)].toDouble()
                            val ar=st.delayR[Math.floorMod(st.write-tap1,st.delayR.size)].toDouble()
                            val br=st.delayR[Math.floorMod(st.write-tap2,st.delayR.size)].toDouble()
                            val cr=st.delayR[Math.floorMod(st.write-tap3,st.delayR.size)].toDouble()
                            st.delayL[st.write]=(left+(a+b+c)/3*room).toFloat().coerceIn(-4f,4f)
                            st.delayR[st.write]=(right+(ar+br+cr)/3*room).toFloat().coerceIn(-4f,4f)
                            left=left*(1-wet)+(a+b+c)/3*wet;right=right*(1-wet)+(ar+br+cr)/3*wet
                        }
                        st.write=(st.write+1)%st.delayL.size
                    }
                    "LIMITER" -> {
                        val ceiling=db((p["ceilingDb"]?:-1f).coerceIn(-12f,0f)).coerceIn(0.05,1.0)
                        left=(left/(1+abs(left)/ceiling)).coerceIn(-ceiling,ceiling)
                        right=(right/(1+abs(right)/ceiling)).coerceIn(-ceiling,ceiling)
                    }
                }
            }
            buffer[i]=left.toFloat().coerceIn(-4f,4f);buffer[i+1]=right.toFloat().coerceIn(-4f,4f)
            master[i]+=(buffer[i]-dryL.toFloat())*gain
            master[i+1]+=(buffer[i+1]-dryR.toFloat())*gain
        }
    }
    companion object {
        // Log-normalized 20 Hz–20 kHz starting points for the eight EQ bands.
        private val EQ_DEFAULT_FREQ_NORMS=floatArrayOf(0.159f,0.259f,0.366f,0.466f,0.566f,0.681f,0.799f,0.900f)
        private val EFFECTS=setOf("EQ","PARAM_EQ_8","REVERB","DELAY","COMPRESSOR","SATURATOR","FILTER","LIMITER")
        private fun db(v:Float)=10.0.pow(v.coerceIn(-48f,18f).toDouble()/20.0)
    }
}
