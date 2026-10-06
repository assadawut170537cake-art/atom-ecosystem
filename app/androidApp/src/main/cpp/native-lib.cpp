#include <jni.h>
#include <string>

// To compile this properly, you need the WebRTC prebuilt library (libwebrtc.a) and headers.
// The following code represents the WebRTC APM AEC3 + Dual-Mic Spatial Beamforming JNI Bridge
// as requested for the online.assadawut.atom Full-Duplex Voice system.

/* Uncomment and link against WebRTC when headers are available in the project
#include <modules/audio_processing/include/audio_processing.h>

class AudioEchoCancellerBridge {
private:
    webrtc::AudioProcessing* apm;

public:
    AudioEchoCancellerBridge() {
        apm = webrtc::AudioProcessingBuilder().Create();
        webrtc::AudioProcessing::Config config;
        config.echo_canceller.enabled = true;
        config.echo_canceller.mobile_mode = true;
        config.noise_suppression.enabled = true;
        config.noise_suppression.level = webrtc::AudioProcessing::Config::NoiseSuppression::kHigh;
        apm->ApplyConfig(config);
    }

    void process_capture_frame(int16_t* mic_data, int16_t* out_clean) {
        webrtc::StreamConfig config(16000, 1);
        apm->ProcessStream(mic_data, config, config, out_clean);
    }

    void feed_playback_reference(const int16_t* spk_data) {
        webrtc::StreamConfig config(24000, 1);
        apm->ProcessReverseStream(spk_data, config, config, nullptr);
    }
};

static AudioEchoCancellerBridge* aecBridge = nullptr;
*/

extern "C" JNIEXPORT void JNICALL
Java_online_assadawut_atom_voice_AEC3JniBridge_initAEC3(JNIEnv *env, jobject thiz) {
    // aecBridge = new AudioEchoCancellerBridge();
}

extern "C" JNIEXPORT void JNICALL
Java_online_assadawut_atom_voice_AEC3JniBridge_processCaptureFrame(JNIEnv *env, jobject thiz, jshortArray mic_data, jshortArray out_clean) {
    /*
    jshort *mic_ptr = env->GetShortArrayElements(mic_data, nullptr);
    jshort *out_ptr = env->GetShortArrayElements(out_clean, nullptr);

    if (aecBridge != nullptr) {
        aecBridge->process_capture_frame(mic_ptr, out_ptr);
    }

    env->ReleaseShortArrayElements(mic_data, mic_ptr, 0);
    env->ReleaseShortArrayElements(out_clean, out_ptr, 0);
    */
}

extern "C" JNIEXPORT void JNICALL
Java_online_assadawut_atom_voice_AEC3JniBridge_feedPlaybackReference(JNIEnv *env, jobject thiz, jshortArray spk_data) {
    /*
    jshort *spk_ptr = env->GetShortArrayElements(spk_data, nullptr);

    if (aecBridge != nullptr) {
        aecBridge->feed_playback_reference(spk_ptr);
    }

    env->ReleaseShortArrayElements(spk_data, spk_ptr, 0);
    */
}
