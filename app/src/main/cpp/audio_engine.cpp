#include <jni.h>
#include <algorithm>
#include <atomic>
#include <cmath>

namespace {
constexpr double kPi = 3.14159265358979323846;
std::atomic<bool> g_running{false};
std::atomic<float> g_frequency{110.0f};
std::atomic<float> g_gain{0.0f};
std::atomic<int> g_waveform{0};
std::atomic<double> g_phase{0.0};
std::atomic<int> g_sample_rate{44100};

float oscillator(double phase, int waveform) {
    const double wrapped = phase - std::floor(phase);
    switch (waveform) {
        case 1: return static_cast<float>(2.0 * wrapped - 1.0);
        case 2: return wrapped < 0.5 ? 1.0f : -1.0f;
        case 3: return static_cast<float>(1.0 - 4.0 * std::abs(wrapped - 0.5));
        default: return static_cast<float>(std::sin(2.0 * kPi * wrapped));
    }
}
}

extern "C" JNIEXPORT void JNICALL
Java_com_studiowave_app_audio_NativeAudioEngine_nativeConfigure(JNIEnv*, jobject, jint sampleRate) {
    if (sampleRate >= 8000 && sampleRate <= 192000) g_sample_rate.store(sampleRate);
}

extern "C" JNIEXPORT void JNICALL
Java_com_studiowave_app_audio_NativeAudioEngine_nativeStart(JNIEnv*, jobject) { g_running.store(true); }

extern "C" JNIEXPORT void JNICALL
Java_com_studiowave_app_audio_NativeAudioEngine_nativeStop(JNIEnv*, jobject) { g_running.store(false); }

extern "C" JNIEXPORT void JNICALL
Java_com_studiowave_app_audio_NativeAudioEngine_nativeSetTone(JNIEnv*, jobject, jfloat frequency, jfloat gain, jint waveform) {
    if (std::isfinite(frequency)) g_frequency.store(std::clamp(frequency, 20.0f, 18000.0f));
    if (std::isfinite(gain)) g_gain.store(std::clamp(gain, 0.0f, 0.25f));
    g_waveform.store(std::clamp(waveform, 0, 3));
}

extern "C" JNIEXPORT void JNICALL
Java_com_studiowave_app_audio_NativeAudioEngine_nativeRender(JNIEnv* env, jobject, jshortArray target, jint frames) {
    if (target == nullptr || frames <= 0 || frames > 4096) return;
    const jsize length = env->GetArrayLength(target);
    if (length < frames * 2) return;
    jshort* output = env->GetShortArrayElements(target, nullptr);
    if (output == nullptr) return;

    const double increment = static_cast<double>(g_frequency.load()) / static_cast<double>(g_sample_rate.load());
    double phase = g_phase.load();
    const float gain = g_running.load() ? g_gain.load() : 0.0f;
    const int waveform = g_waveform.load();
    for (jint frame = 0; frame < frames; ++frame) {
        const auto sample = static_cast<jshort>(std::clamp(
            oscillator(phase, waveform) * gain * 32767.0f, -32767.0f, 32767.0f));
        output[frame * 2] = sample;
        output[frame * 2 + 1] = sample;
        phase += increment;
        if (phase >= 1.0) phase -= 1.0;
    }
    g_phase.store(phase);
    env->ReleaseShortArrayElements(target, output, 0);
}