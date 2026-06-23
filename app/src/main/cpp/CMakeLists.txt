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

add_subdirectory(${LLAMA_DIR} ${CMAKE_CURRENT_BINARY_DIR}/llama.cpp EXCLUDE_FROM_ALL)

add_library(llama-android SHARED llama_jni.cpp)
target_link_libraries(llama-android llama ggml android log)
