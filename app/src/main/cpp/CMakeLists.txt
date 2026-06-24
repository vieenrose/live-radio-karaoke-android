cmake_minimum_required(VERSION 3.22.1)
project(llamabridge)

# llama.cpp is a git submodule (or fetched by scripts/fetch-native-libs.sh).
# It is intentionally NOT committed to this repo (see .gitignore); F-Droid pulls it as a srclib.
set(LLAMA_DIR ${CMAKE_CURRENT_SOURCE_DIR}/llama.cpp)

if(NOT EXISTS ${LLAMA_DIR}/CMakeLists.txt)
    message(FATAL_ERROR
        "llama.cpp source not found at ${LLAMA_DIR}.\n"
        "Run scripts/fetch-native-libs.sh (or `git submodule update --init`) before building with -PwithNative.")
endif()

# Lean mobile build of llama.cpp.
set(LLAMA_CURL OFF CACHE BOOL "" FORCE)
set(LLAMA_BUILD_TESTS OFF CACHE BOOL "" FORCE)
set(LLAMA_BUILD_EXAMPLES OFF CACHE BOOL "" FORCE)
set(LLAMA_BUILD_SERVER OFF CACHE BOOL "" FORCE)
set(GGML_OPENMP OFF CACHE BOOL "" FORCE)
set(BUILD_SHARED_LIBS OFF CACHE BOOL "" FORCE)

# Performance: target ARMv8.2 + dotprod + fp16. The NDK arm64-v8a default is plain armv8-a, which
# leaves llama.cpp on generic NEON and ignores the SDOT/UDOT int8 dot-product + fp16 instructions
# that the Pixel 6 (Tensor: Cortex-X1/A76/A55) and essentially all 2018+ arm64 phones support.
# Enabling dotprod markedly speeds up Q4_0 matmul (the Gemma decode hot path). (i8mm/SVE omitted —
# the Tensor cores don't have them.)
if(ANDROID_ABI STREQUAL "arm64-v8a")
    set(GGML_NATIVE OFF CACHE BOOL "" FORCE)
    set(GGML_CPU_ARM_ARCH "armv8.2-a+dotprod+fp16" CACHE STRING "" FORCE)
endif()

add_subdirectory(${LLAMA_DIR} ${CMAKE_CURRENT_BINARY_DIR}/llama.cpp EXCLUDE_FROM_ALL)

add_library(llama-android SHARED llama_jni.cpp)
target_link_libraries(llama-android llama ggml android log)
