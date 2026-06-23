// JNI bridge over llama.cpp for on-device Gemma 3 1B (summaries + translation).
// Exposes a small stepping API so Kotlin can stream tokens:
//   load -> startCompletion -> nextToken* -> (EOS) -> reset/startCompletion -> ... -> free
// Mirrors the Python SummarizerService's streamed create_chat_completion loop.
#include <jni.h>
#include <android/log.h>
#include <string>
#include <vector>
#include "llama.h"

#define LOG_TAG "llama-jni"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

struct LlamaSession {
    llama_model *model = nullptr;
    llama_context *ctx = nullptr;
    const llama_vocab *vocab = nullptr;
    llama_sampler *smpl = nullptr;
    int n_past = 0;
    int n_ctx = 2048;
};

static bool g_backend_inited = false;

extern "C" JNIEXPORT jlong JNICALL
Java_io_github_vieenrose_liveradiokaraoke_llm_LlamaBridge_nativeLoad(
        JNIEnv *env, jobject, jstring jPath, jint nCtx, jint nThreads) {
    if (!g_backend_inited) { llama_backend_init(); g_backend_inited = true; }

    const char *path = env->GetStringUTFChars(jPath, nullptr);
    auto *s = new LlamaSession();
    s->n_ctx = nCtx;

    llama_model_params mparams = llama_model_default_params();
    mparams.n_gpu_layers = 0; // CPU only, on-device
    s->model = llama_model_load_from_file(path, mparams);
    env->ReleaseStringUTFChars(jPath, path);
    if (!s->model) { LOGE("model load failed"); delete s; return 0; }

    s->vocab = llama_model_get_vocab(s->model);

    llama_context_params cparams = llama_context_default_params();
    cparams.n_ctx = (uint32_t) nCtx;
    cparams.n_threads = nThreads;
    cparams.n_threads_batch = nThreads;
    s->ctx = llama_init_from_model(s->model, cparams);
    if (!s->ctx) { LOGE("ctx init failed"); llama_model_free(s->model); delete s; return 0; }

    return reinterpret_cast<jlong>(s);
}

// Low-temperature factual sampling — matches summarizer_service.py (temp 0.1 / top_k 50 / top_p 0.1 / rep 1.05).
extern "C" JNIEXPORT void JNICALL
Java_io_github_vieenrose_liveradiokaraoke_llm_LlamaBridge_nativeStart(
        JNIEnv *env, jobject, jlong handle, jstring jPrompt, jfloat temp, jint topK,
        jfloat topP, jfloat repeatPenalty) {
    auto *s = reinterpret_cast<LlamaSession *>(handle);
    if (!s) return;

    // Fresh KV-cache per generation.
    llama_memory_clear(llama_get_memory(s->ctx), true);
    s->n_past = 0;
    if (s->smpl) { llama_sampler_free(s->smpl); s->smpl = nullptr; }

    s->smpl = llama_sampler_chain_init(llama_sampler_chain_default_params());
    llama_sampler_chain_add(s->smpl, llama_sampler_init_penalties(64, repeatPenalty, 0.0f, 0.0f));
    llama_sampler_chain_add(s->smpl, llama_sampler_init_top_k(topK));
    llama_sampler_chain_add(s->smpl, llama_sampler_init_top_p(topP, 1));
    llama_sampler_chain_add(s->smpl, llama_sampler_init_temp(temp));
    llama_sampler_chain_add(s->smpl, llama_sampler_init_dist(LLAMA_DEFAULT_SEED));

    const char *prompt = env->GetStringUTFChars(jPrompt, nullptr);
    std::string text(prompt);
    env->ReleaseStringUTFChars(jPrompt, prompt);

    int n_max = (int) text.size() + 64;
    std::vector<llama_token> tokens(n_max);
    int n = llama_tokenize(s->vocab, text.c_str(), (int) text.size(),
                           tokens.data(), n_max, /*add_special*/ true, /*parse_special*/ true);
    if (n < 0) { tokens.resize(-n); n = -n; }
    tokens.resize(n);

    llama_batch batch = llama_batch_get_one(tokens.data(), (int) tokens.size());
    if (llama_decode(s->ctx, batch) != 0) { LOGE("prompt decode failed"); return; }
    s->n_past += (int) tokens.size();
}

// Returns the next token's text, or "" at end-of-generation / context-full.
extern "C" JNIEXPORT jstring JNICALL
Java_io_github_vieenrose_liveradiokaraoke_llm_LlamaBridge_nativeNext(
        JNIEnv *env, jobject, jlong handle) {
    auto *s = reinterpret_cast<LlamaSession *>(handle);
    if (!s || !s->smpl) return env->NewStringUTF("");

    if (s->n_past >= s->n_ctx) return env->NewStringUTF("");

    llama_token tok = llama_sampler_sample(s->smpl, s->ctx, -1);
    if (llama_vocab_is_eog(s->vocab, tok)) return env->NewStringUTF("");

    char buf[256];
    int len = llama_token_to_piece(s->vocab, tok, buf, sizeof(buf), 0, true);
    std::string piece = (len > 0) ? std::string(buf, len) : std::string();

    llama_batch batch = llama_batch_get_one(&tok, 1);
    if (llama_decode(s->ctx, batch) != 0) return env->NewStringUTF("");
    s->n_past += 1;

    return env->NewStringUTF(piece.c_str());
}

extern "C" JNIEXPORT void JNICALL
Java_io_github_vieenrose_liveradiokaraoke_llm_LlamaBridge_nativeFree(
        JNIEnv *, jobject, jlong handle) {
    auto *s = reinterpret_cast<LlamaSession *>(handle);
    if (!s) return;
    if (s->smpl) llama_sampler_free(s->smpl);
    if (s->ctx) llama_free(s->ctx);
    if (s->model) llama_model_free(s->model);
    delete s;
}
