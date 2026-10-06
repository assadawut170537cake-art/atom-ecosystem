package online.assadawut.atom.voice

class AEC3JniBridge {
    companion object {
        init {
            try {
                System.loadLibrary("webrtc_aec3")
            } catch (e: UnsatisfiedLinkError) {
                e.printStackTrace()
            }
        }
    }

    external fun initAEC3()
    external fun processCaptureFrame(micData: ShortArray, outClean: ShortArray)
    external fun feedPlaybackReference(spkData: ShortArray)
}
