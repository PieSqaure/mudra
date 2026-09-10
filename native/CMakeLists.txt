# Scaffold for the llama.cpp JNI bridge backing LlamaCppQwenRuntime.kt.
#
# This is intentionally NOT wired into app/build.gradle.kts yet: it depends on vendoring
# llama.cpp (a large C/C++ project) which this repo does not include. See README.md in this
# directory for the steps to enable it.

cmake_minimum_required(VERSION 3.22.1)
project(mudra_llama_bridge)

set(CMAKE_CXX_STANDARD 17)

# Expect llama.cpp to be vendored at native/llama.cpp (git submodule) once enabled.
# add_subdirectory(llama.cpp)

add_library(mudra_llama_bridge SHARED
    llama_bridge.cpp
)

target_include_directories(mudra_llama_bridge PRIVATE
    llama.cpp
    llama.cpp/include
)

# target_link_libraries(mudra_llama_bridge PRIVATE llama log android)
target_link_libraries(mudra_llama_bridge PRIVATE log android)
