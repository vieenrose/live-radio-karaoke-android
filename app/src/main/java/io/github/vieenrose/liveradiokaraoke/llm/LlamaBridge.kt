package io.github.vieenrose.liveradiokaraoke.llm

/**
 * Thin Kotlin wrapper over the llama.cpp JNI bridge (app/src/main/cpp/llama_jni.cpp).
 * Stepping API: [load] → [start] → [next]* (until it returns "") → repeat → [free].
 */
class LlamaBridge {

    private var handle: Long = 0L

    val isLoaded: Boolean get() = handle != 0L

    fun load(modelPath: String, nCtx: Int, nThreads: Int): Boolean {
        if (handle != 0L) return true
        handle = nativeLoad(modelPath, nCtx, nThreads)
        return handle != 0L
    }

    fun start(system: String, user: String, temp: Float = 0.1f, topK: Int = 50, topP: Float = 0.1f, repeatPenalty: Float = 1.05f) {
        if (handle != 0L) nativeStart(handle, system, user, temp, topK, topP, repeatPenalty)
    }

    /** Next decoded piece, or "" at end-of-generation. */
    fun next(): String = if (handle != 0L) nativeNext(handle) else ""

    fun free() {
        if (handle != 0L) { nativeFree(handle); handle = 0L }
    }

    private external fun nativeLoad(modelPath: String, nCtx: Int, nThreads: Int): Long
    private external fun nativeStart(handle: Long, system: String, user: String, temp: Float, topK: Int, topP: Float, repeatPenalty: Float)
    private external fun nativeNext(handle: Long): String
    private external fun nativeFree(handle: Long)

    companion object {
        fun nativeAvailable(): Boolean = runCatching { System.loadLibrary("llama-android"); true }.getOrDefault(false)

        init {
            runCatching { System.loadLibrary("llama-android") }
        }
    }
}
