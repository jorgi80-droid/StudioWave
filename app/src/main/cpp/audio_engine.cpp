#include <jni.h>
#include <oboe/Oboe.h>
#include <algorithm>
#include <array>
#include <atomic>
#include <cmath>
#include <cstdint>
#include <memory>
#include <mutex>
namespace {
constexpr double kPi=3.14159265358979323846;
constexpr uint32_t kRingSamples=8192, kRingMask=kRingSamples-1;
static_assert((kRingSamples & kRingMask)==0);
std::array<int16_t,kRingSamples> ring{};
std::atomic<uint32_t> readIndex{0}, writeIndex{0}, playedFrames{0}, underruns{0};
std::atomic<bool> ready{false};
std::atomic<float> frequency{440.f}, gain{0.f};
std::atomic<int> waveform{0}, sampleRate{44100};
std::atomic<float> phase{0.0f};
std::mutex streamMutex;
std::shared_ptr<oboe::AudioStream> stream;
float oscillator(double p,int w) {
 const double x=p-std::floor(p);
 switch(w){case 1:return float(2*x-1);case 2:return x<.5?1.f:-1.f;case 3:return float(1-4*std::abs(x-.5));default:return float(std::sin(2*kPi*x));}
}
class Callback final:public oboe::AudioStreamDataCallback {
 public: oboe::DataCallbackResult onAudioReady(oboe::AudioStream*,void* data,int32_t frames) override {
  if(!data||frames<=0)return oboe::DataCallbackResult::Continue;
  auto*out=static_cast<int16_t*>(data); const uint32_t wanted=uint32_t(frames)*2U;
  const uint32_t r=readIndex.load(std::memory_order_relaxed), w=writeIndex.load(std::memory_order_acquire);
  const uint32_t count=std::min(w-r,wanted);
  for(uint32_t i=0;i<count;++i)out[i]=ring[(r+i)&kRingMask];
  if(count<wanted){
   const float g=gain.load(std::memory_order_relaxed);
   if(g>0.f&&count==0U){
    const double inc=double(frequency.load(std::memory_order_relaxed))/double(std::max(1,sampleRate.load(std::memory_order_relaxed)));
    double p=phase.load(std::memory_order_relaxed); const int wf=waveform.load(std::memory_order_relaxed);
    for(uint32_t i=0;i+1U<wanted;i+=2U){const auto s=int16_t(std::clamp(oscillator(p,wf)*g*32767.f,-32767.f,32767.f));out[i]=s;out[i+1U]=s;p+=inc;if(p>=1)p-=1;}
    phase.store(p,std::memory_order_relaxed);
   } else std::fill(out+count,out+wanted,int16_t(0));
   underruns.fetch_add(1U,std::memory_order_relaxed);
  }
  readIndex.store(r+count,std::memory_order_release); // Advance the playback clock for every hardware output frame, including silence inserted on underrun.
  playedFrames.fetch_add(uint32_t(frames),std::memory_order_relaxed);
  return oboe::DataCallbackResult::Continue;
 }
};
Callback callback;
void closeLocked(){ready.store(false,std::memory_order_release);if(stream){(void)stream->requestStop();(void)stream->close();stream.reset();}}
oboe::Result openLocked(int32_t rate){
 closeLocked();readIndex.store(0);writeIndex.store(0);playedFrames.store(0);underruns.store(0);sampleRate.store(rate);phase.store(0);
 oboe::AudioStreamBuilder b;
 b.setDirection(oboe::Direction::Output)->setPerformanceMode(oboe::PerformanceMode::LowLatency)
  ->setSharingMode(oboe::SharingMode::Exclusive)->setFormat(oboe::AudioFormat::I16)
  ->setChannelCount(oboe::ChannelCount::Stereo)->setSampleRate(rate)
  ->setSampleRateConversionQuality(oboe::SampleRateConversionQuality::Medium)
  ->setUsage(oboe::Usage::Game)->setContentType(oboe::ContentType::Music)->setDataCallback(&callback);
 auto result=b.openStream(stream);
 if(result!=oboe::Result::OK){b.setSharingMode(oboe::SharingMode::Shared);result=b.openStream(stream);}
 if(result!=oboe::Result::OK||!stream)return result;
 (void)stream->setBufferSizeInFrames(std::max(1,stream->getFramesPerBurst())*2);
 ready.store(true,std::memory_order_release);result=stream->requestStart();if(result!=oboe::Result::OK)closeLocked();return result;
}
}
extern "C" JNIEXPORT jint JNICALL Java_com_studiowave_app_audio_NativeAudioEngine_nativeOpenOutput(JNIEnv*,jobject,jint rate){
 if(rate<8000||rate>192000)return static_cast<jint>(oboe::Result::ErrorIllegalArgument);
 std::lock_guard<std::mutex> lock(streamMutex);return static_cast<jint>(openLocked(rate));
}
extern "C" JNIEXPORT void JNICALL Java_com_studiowave_app_audio_NativeAudioEngine_nativeCloseOutput(JNIEnv*,jobject){
 std::lock_guard<std::mutex> lock(streamMutex);closeLocked();
}
extern "C" JNIEXPORT void JNICALL Java_com_studiowave_app_audio_NativeAudioEngine_nativeSetTone(JNIEnv*,jobject,jfloat f,jfloat g,jint w){
 if(std::isfinite(f))frequency.store(std::clamp(f,20.f,18000.f));
 if(std::isfinite(g))gain.store(std::clamp(g,0.f,.25f));waveform.store(std::clamp(w,0,3));
}
extern "C" JNIEXPORT jint JNICALL Java_com_studiowave_app_audio_NativeAudioEngine_nativeWritePcm(JNIEnv*env,jobject,jshortArray src,jint n){
 if(!src||n<=0||(n&1)||uint32_t(n)>kRingSamples||!ready.load(std::memory_order_acquire))return 0;
 const uint32_t count=uint32_t(n),r=readIndex.load(std::memory_order_acquire),w=writeIndex.load(std::memory_order_relaxed),used=w-r;
 if(count>kRingSamples-std::min(used,kRingSamples))return 0;
 jshort*in=env->GetShortArrayElements(src,nullptr);if(!in)return 0;
 for(uint32_t i=0;i<count;++i)ring[(w+i)&kRingMask]=in[i];
 env->ReleaseShortArrayElements(src,in,JNI_ABORT);writeIndex.store(w+count,std::memory_order_release);return n;
}
extern "C" JNIEXPORT jint JNICALL Java_com_studiowave_app_audio_NativeAudioEngine_nativeQueuedFrames(JNIEnv*,jobject){
 const auto w=writeIndex.load(std::memory_order_acquire),r=readIndex.load(std::memory_order_acquire);return jint(std::min(w-r,kRingSamples)/2U);
}
extern "C" JNIEXPORT jlong JNICALL Java_com_studiowave_app_audio_NativeAudioEngine_nativePlayedFrames(JNIEnv*,jobject){return jlong(playedFrames.load());}
extern "C" JNIEXPORT jlong JNICALL Java_com_studiowave_app_audio_NativeAudioEngine_nativeUnderrunCount(JNIEnv*,jobject){return jlong(underruns.load());}
