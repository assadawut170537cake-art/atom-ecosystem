cmake_minimum_required(VERSION 3.22.1)

project("webrtc_aec3")

# This is a stub for the WebRTC APM AEC3 JNI Bridge.
# To compile the actual WebRTC C++ code, you must include the WebRTC static library
# and header files, then link them here.

add_library(
        webrtc_aec3
        SHARED
        native-lib.cpp)

find_library(
        log-lib
        log)

target_link_libraries(
        webrtc_aec3
        ${log-lib})
