#include <jni.h>
#include <aaudio/AAudio.h>
#include <algorithm>
#include <array>
#include <atomic>
#include <cmath>
#include <cstdint>
#include <mutex>

namespace {
constexpr int32_t kChannels = 2;
constexpr uint64_t kCapacityFrames = 8192; // power of two for inexpensive wrap
constexpr uint64_t kRingMask = kCapacityFrames - 1;
constexpr double kPi = 3.14159265358979323846;

struct AudioState {
    std::array<int16_t, kCapacityFrames * kChannels> ring{};
    std::atomic<uint64_t> writeFrame{0};
    std::atomic<uint64_t> readFrame{0};
    // PCM frames consumed drain the final queued block; output frames count elapsed
    // device callbacks, including silence during underruns, so the playhead cannot freeze.
    std::atomic<uint64_t> playedFrames{0};
    std::atomic<uint64_t> outputFrames{0};
    std::atomic<uint64_t> underruns{0};
    std::atomic<bool> hasReceivedPcm{false};
    std::atomic<bool> running{false};
    std::atomic<float> toneFrequency{440.0f};
    std::atomic<float> toneGain{0.0f};
    std::atomic<int> toneWaveform{0};
    std::atomic<uint32_t> tonePhase{0};
    std::atomic<int32_t> sampleRate{44100};
};

AudioState g_state;
AAudioStream* g_stream = nullptr;
std::mutex g_streamMutex;

float oscillator(float phase, int waveform) {
    const float wrapped = phase - std::floor(phase);
    switch (waveform) {
        case 1: return 2.0f * wrapped - 1.0f;
        case 2: return wrapped < 0.5f ? 1.0f : -1.0f;
        case 3: return 1.0f - 4.0f * std::abs(wrapped - 0.5f);
        default: return std::sin(2.0f * static_cast<float>(kPi) * wrapped);
    }
}

aaudio_data_callback_result_t dataCallback(
    AAudioStream*, void* userData, void* audioData, int32_t numFrames) {
    auto* state = static_cast<AudioState*>(userData);
    auto* output = static_cast<int16_t*>(audioData);
    if (state == nullptr || output == nullptr || numFrames <= 0) {
        return AAUDIO_CALLBACK_RESULT_CONTINUE;
    }

    const uint64_t read = state->readFrame.load(std::memory_order_relaxed);
    const uint64_t write = state->writeFrame.load(std::memory_order_acquire);
    const uint64_t available = std::min<uint64_t>(write - read, kCapacityFrames);
    const uint64_t fromQueue = std::min<uint64_t>(available, static_cast<uint64_t>(numFrames));

    for (uint64_t frame = 0; frame < fromQueue; ++frame) {
        const uint64_t slot = ((read + frame) & kRingMask) * kChannels;
        output[frame * kChannels] = state->ring[slot];
        output[frame * kChannels + 1] = state->ring[slot + 1];
    }

    const float gain = state->running.load(std::memory_order_relaxed)
        ? std::clamp(state->toneGain.load(std::memory_order_relaxed), 0.0f, 0.25f)
        : 0.0f;
    if (fromQueue < static_cast<uint64_t>(numFrames) && gain > 0.0f) {
        const float increment = std::clamp(state->toneFrequency.load(std::memory_order_relaxed), 20.0f, 18000.0f)
            / static_cast<float>(std::max(1, state->sampleRate.load(std::memory_order_relaxed)));
        float phase = static_cast<float>(state->tonePhase.load(std::memory_order_relaxed)) / 4294967296.0f;
        const int waveform = state->toneWaveform.load(std::memory_order_relaxed);
        for (uint64_t frame = fromQueue; frame < static_cast<uint64_t>(numFrames); ++frame) {
            const auto sample = static_cast<int16_t>(std::clamp(
                oscillator(phase, waveform) * gain * 32767.0f, -32767.0f, 32767.0f));
            output[frame * kChannels] = sample;
            output[frame * kChannels + 1] = sample;
            phase += increment;
            if (phase >= 1.0f) phase -= 1.0f;
        }
        state->tonePhase.store(static_cast<uint32_t>(phase * 4294967296.0), std::memory_order_relaxed);
    } else {
        std::fill(output + fromQueue * kChannels, output + static_cast<uint64_t>(numFrames) * kChannels, 0);
    }

    state->readFrame.store(read + fromQueue, std::memory_order_release);
    state->playedFrames.fetch_add(fromQueue, std::memory_order_relaxed);
    state->outputFrames.fetch_add(static_cast<uint64_t>(numFrames), std::memory_order_relaxed);
    if (fromQueue < static_cast<uint64_t>(numFrames) &&
        state->hasReceivedPcm.load(std::memory_order_acquire)) {
        state->underruns.fetch_add(1, std::memory_order_relaxed);
    }
    return AAUDIO_CALLBACK_RESULT_CONTINUE;
}

void errorCallback(AAudioStream*, void* userData, aaudio_result_t) {
    auto* state = static_cast<AudioState*>(userData);
    if (state != nullptr) state->running.store(false, std::memory_order_release);
}

} // namespace

extern "C" JNIEXPORT jint JNICALL
Java_com_studiowave_app_audio_NativeAudioEngine_nativeOpenOutput(JNIEnv*, jobject, jint sampleRate) {
    if (sampleRate < 8000 || sampleRate > 192000) return AAUDIO_ERROR_ILLEGAL_ARGUMENT;
    std::lock_guard<std::mutex> lock(g_streamMutex);
    if (g_stream != nullptr) return AAUDIO_OK;

    g_state.writeFrame.store(0, std::memory_order_relaxed);
    g_state.readFrame.store(0, std::memory_order_relaxed);
    g_state.playedFrames.store(0, std::memory_order_relaxed);
    g_state.outputFrames.store(0, std::memory_order_relaxed);
    g_state.underruns.store(0, std::memory_order_relaxed);
    g_state.hasReceivedPcm.store(false, std::memory_order_relaxed);
    g_state.sampleRate.store(sampleRate, std::memory_order_relaxed);
    AAudioStreamBuilder* builder = nullptr;
    aaudio_result_t result = AAudio_createStreamBuilder(&builder);
    if (result != AAUDIO_OK || builder == nullptr) return result == AAUDIO_OK ? AAUDIO_ERROR_INTERNAL : result;

    AAudioStreamBuilder_setDirection(builder, AAUDIO_DIRECTION_OUTPUT);
    AAudioStreamBuilder_setFormat(builder, AAUDIO_FORMAT_PCM_I16);
    AAudioStreamBuilder_setChannelCount(builder, kChannels);
    AAudioStreamBuilder_setSampleRate(builder, sampleRate);
    AAudioStreamBuilder_setPerformanceMode(builder, AAUDIO_PERFORMANCE_MODE_LOW_LATENCY);
    // A DAW benefits from the direct MMAP path when Android grants exclusive access.
    // Some devices or competing audio apps reject it, so shared mode is a real fallback.
    AAudioStreamBuilder_setSharingMode(builder, AAUDIO_SHARING_MODE_EXCLUSIVE);
    AAudioStreamBuilder_setDataCallback(builder, dataCallback, &g_state);
    AAudioStreamBuilder_setErrorCallback(builder, errorCallback, &g_state);
    result = AAudioStreamBuilder_openStream(builder, &g_stream);
    if (result != AAUDIO_OK || g_stream == nullptr) {
        if (g_stream != nullptr) {
            AAudioStream_close(g_stream);
            g_stream = nullptr;
        }
        AAudioStreamBuilder_setSharingMode(builder, AAUDIO_SHARING_MODE_SHARED);
        result = AAudioStreamBuilder_openStream(builder, &g_stream);
    }
    AAudioStreamBuilder_delete(builder);
    if (result != AAUDIO_OK || g_stream == nullptr) {
        g_stream = nullptr;
        return result == AAUDIO_OK ? AAUDIO_ERROR_INTERNAL : result;
    }

    const int32_t actualRate = AAudioStream_getSampleRate(g_stream);
    if (actualRate > 0) g_state.sampleRate.store(actualRate, std::memory_order_relaxed);
    // AAudio defaults can be oversized on some routes. Request two device bursts for
    // responsive transport while preserving a safe fallback if the device rejects it.
    const int32_t burstFrames = AAudioStream_getFramesPerBurst(g_stream);
    if (burstFrames > 0) {
        const int32_t desiredBufferFrames = burstFrames * 2;
        (void)AAudioStream_setBufferSizeInFrames(g_stream, desiredBufferFrames);
    }
    result = AAudioStream_requestStart(g_stream);
    if (result != AAUDIO_OK) {
        AAudioStream_close(g_stream);
        g_stream = nullptr;
        return result;
    }
    g_state.running.store(true, std::memory_order_release);
    return AAUDIO_OK;
}

extern "C" JNIEXPORT void JNICALL
Java_com_studiowave_app_audio_NativeAudioEngine_nativeCloseOutput(JNIEnv*, jobject) {
    std::lock_guard<std::mutex> lock(g_streamMutex);
    g_state.running.store(false, std::memory_order_release);
    if (g_stream != nullptr) {
        AAudioStream_requestStop(g_stream);
        AAudioStream_close(g_stream);
        g_stream = nullptr;
    }
}

extern "C" JNIEXPORT void JNICALL
Java_com_studiowave_app_audio_NativeAudioEngine_nativeSetTone(
    JNIEnv*, jobject, jfloat frequency, jfloat gain, jint waveform) {
    if (std::isfinite(frequency)) g_state.toneFrequency.store(std::clamp(frequency, 20.0f, 18000.0f));
    if (std::isfinite(gain)) g_state.toneGain.store(std::clamp(gain, 0.0f, 0.25f));
    g_state.toneWaveform.store(std::clamp(waveform, 0, 3));
}

extern "C" JNIEXPORT jint JNICALL
Java_com_studiowave_app_audio_NativeAudioEngine_nativeWritePcm(
    JNIEnv* env, jobject, jshortArray source, jint sampleCount) {
    if (!g_state.running.load(std::memory_order_acquire)) return AAUDIO_ERROR_DISCONNECTED;
    if (source == nullptr || sampleCount <= 0 || (sampleCount % kChannels) != 0) return 0;
    const jsize length = env->GetArrayLength(source);
    if (sampleCount > length) return 0;
    const uint64_t frames = static_cast<uint64_t>(sampleCount / kChannels);
    const uint64_t write = g_state.writeFrame.load(std::memory_order_relaxed);
    const uint64_t read = g_state.readFrame.load(std::memory_order_acquire);
    const uint64_t queued = write - read;
    if (queued > kCapacityFrames || frames > kCapacityFrames - queued) return 0;

    jboolean isCopy = JNI_FALSE;
    jshort* pcm = env->GetShortArrayElements(source, &isCopy);
    if (pcm == nullptr) return 0;
    for (uint64_t frame = 0; frame < frames; ++frame) {
        const uint64_t slot = ((write + frame) & kRingMask) * kChannels;
        g_state.ring[slot] = pcm[frame * kChannels];
        g_state.ring[slot + 1] = pcm[frame * kChannels + 1];
    }
    env->ReleaseShortArrayElements(source, pcm, JNI_ABORT);
    g_state.writeFrame.store(write + frames, std::memory_order_release);
    g_state.hasReceivedPcm.store(true, std::memory_order_release);
    return sampleCount;
}

extern "C" JNIEXPORT jint JNICALL
Java_com_studiowave_app_audio_NativeAudioEngine_nativeQueuedFrames(JNIEnv*, jobject) {
    if (!g_state.running.load(std::memory_order_acquire)) return -1;
    const uint64_t write = g_state.writeFrame.load(std::memory_order_acquire);
    const uint64_t read = g_state.readFrame.load(std::memory_order_acquire);
    return static_cast<jint>(std::min<uint64_t>(write - read, static_cast<uint64_t>(INT32_MAX)));
}

extern "C" JNIEXPORT jlong JNICALL
Java_com_studiowave_app_audio_NativeAudioEngine_nativePlayedFrames(JNIEnv*, jobject) {
    return static_cast<jlong>(g_state.playedFrames.load(std::memory_order_relaxed));
}

extern "C" JNIEXPORT jlong JNICALL
Java_com_studiowave_app_audio_NativeAudioEngine_nativeOutputFrames(JNIEnv*, jobject) {
    return static_cast<jlong>(g_state.outputFrames.load(std::memory_order_relaxed));
}

extern "C" JNIEXPORT jlong JNICALL
Java_com_studiowave_app_audio_NativeAudioEngine_nativeUnderrunCount(JNIEnv*, jobject) {
    return static_cast<jlong>(g_state.underruns.load(std::memory_order_relaxed));
}
