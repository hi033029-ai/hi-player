# app/src/main/cpp/CMakeLists.txt

cmake_minimum_required(VERSION 3.22.1)
project("chromaprint_jni")

add_library(chromaprint_jni SHARED chromaprint_jni.cpp)

# The .so you built in BUILDING_CHROMAPRINT.md, one per ABI, already placed under
# app/src/main/jniLibs/<abi>/libchromaprint.so — Gradle packages those automatically;
# this just tells the linker chromaprint's symbols exist for this JNI wrapper to call.
add_library(chromaprint SHARED IMPORTED)
set_target_properties(chromaprint PROPERTIES
    IMPORTED_LOCATION ${CMAKE_SOURCE_DIR}/../jniLibs/${ANDROID_ABI}/libchromaprint.so)

target_include_directories(chromaprint_jni PRIVATE ${CMAKE_SOURCE_DIR})
target_link_libraries(chromaprint_jni chromaprint log)
