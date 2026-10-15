package com.studiowave.app.audio
import java.util.concurrent.atomic.AtomicBoolean
/** Native stereo PCM output through Oboe/AAudio. Realtime callback never enters Kotlin or waits on locks. */
class NativeAudioEngine:AutoCloseable {
 private val running=AtomicBoolean(false);private val closed=AtomicBoolean(false)
 @Volatile private var producerBackpressureFrames=512
 companion object {
  // Keep enough ready audio for scheduler jitter without adding unnecessary monitoring latency.
  private const val MAX_QUEUED_FRAMES=1024
  private const val PRODUCER_BACKPRESSURE_FRAMES=MAX_QUEUED_FRAMES/2
  init { System.loadLibrary("studiowave_audio") }
 }
 private external fun nativeOpenOutput(sampleRate:Int):Int
 private external fun nativeCloseOutput()
 private external fun nativeSetTone(frequency:Float,gain:Float,waveform:Int)
 private external fun nativeWritePcm(source:ShortArray,sampleCount:Int):Int
 private external fun nativeQueuedFrames():Int
 private external fun nativePlayedFrames():Long
 private external fun nativeUnderrunCount():Long
 fun configureQueueTarget(targetFrames:Int){
  producerBackpressureFrames=targetFrames.coerceIn(256,4096)
 }
 @Synchronized fun start(sampleRate:Int=44100,queueTargetFrames:Int=1024){
  configureQueueTarget(queueTargetFrames)
  check(!closed.get()){"Audio engine is closed"};require(sampleRate in 8000..192000){"Unsupported sample rate: $sampleRate"}
  if(running.get())return;stop();nativeSetTone(440f,0f,0)
  val result=nativeOpenOutput(sampleRate);check(result==0){"Oboe could not open output stream (code $result)"};running.set(true)
 }
 fun setTone(frequencyHz:Float,gain:Float=.08f,waveform:Int=0){
  check(!closed.get()){"Audio engine is closed"};require(frequencyHz.isFinite()&&gain.isFinite()){"Tone parameters must be finite"}
  nativeSetTone(frequencyHz,gain,waveform.coerceIn(0,3))
 }
 /** Bounded producer queue: back-pressure is applied here, never in the audio callback. */
 fun queuePcm(samples:ShortArray,sampleCount:Int,keepRunning:()->Boolean):Boolean{
  require(sampleCount in 2..samples.size&&sampleCount%2==0){"PCM count must contain complete stereo frames"}
  check(!closed.get()){"Audio engine is closed"}
  while(running.get()&&keepRunning()){
   if(nativeQueuedFrames()>=producerBackpressureFrames){
    try { Thread.sleep(1L) } catch (_:InterruptedException) { Thread.currentThread().interrupt(); return false }
    continue
   }
   if(nativeWritePcm(samples,sampleCount)==sampleCount)return true
   try{Thread.sleep(1L)}catch(_:InterruptedException){Thread.currentThread().interrupt();return false}
  };return false
 }
 fun queuedFrames():Int=if(running.get())nativeQueuedFrames().coerceAtLeast(0) else 0
 fun playedFrames():Long=nativePlayedFrames().coerceAtLeast(0L)
 fun underrunCount():Long=nativeUnderrunCount().coerceAtLeast(0L)
 data class Diagnostics(val queuedFrames:Int,val playedFrames:Long,val underruns:Long)
 fun diagnostics():Diagnostics = Diagnostics(queuedFrames(),playedFrames(),underrunCount())
 fun isRunning():Boolean=running.get()
 @Synchronized fun stop(){running.set(false);nativeCloseOutput()}
 override fun close(){if(closed.compareAndSet(false,true))stop()}
}
