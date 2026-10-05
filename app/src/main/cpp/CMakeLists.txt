cmake_minimum_required(VERSION 3.22.1)
project(checkmind_stockfish CXX)

set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)

set(SF ${CMAKE_CURRENT_SOURCE_DIR}/stockfish/src)
file(GLOB SF_SOURCES ${SF}/*.cpp ${SF}/nnue/*.cpp ${SF}/nnue/features/*.cpp ${SF}/syzygy/*.cpp)

# Stockfish's own main() becomes stockfish_main(), called from the JNI bridge on its own thread.
set_source_files_properties(${SF}/main.cpp PROPERTIES COMPILE_DEFINITIONS main=stockfish_main)

add_library(checkmind_stockfish SHARED ${SF_SOURCES} engine_jni.cpp)

# The nets are loaded from files (UCI EvalFile) instead of being embedded in the library.
target_compile_definitions(checkmind_stockfish PRIVATE NDEBUG NNUE_EMBEDDING_OFF IS_64BIT USE_POPCNT)
target_compile_options(checkmind_stockfish PRIVATE -O3)

if(ANDROID_ABI STREQUAL "arm64-v8a")
    target_compile_definitions(checkmind_stockfish PRIVATE USE_NEON=8)
elseif(ANDROID_ABI STREQUAL "x86_64")
    target_compile_definitions(checkmind_stockfish PRIVATE USE_SSE41 USE_SSSE3 USE_SSE2)
    target_compile_options(checkmind_stockfish PRIVATE -msse4.1 -mssse3 -mpopcnt)
endif()

target_link_libraries(checkmind_stockfish PRIVATE log)
