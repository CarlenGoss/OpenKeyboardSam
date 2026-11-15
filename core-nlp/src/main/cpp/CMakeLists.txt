cmake_minimum_required(VERSION 3.22.1)
project(openkeyboardsam_nlp LANGUAGES CXX)

add_library(openkeyboardsam_nlp SHARED
    nlp_engine.cpp
    jni_bridge.cpp
)

target_compile_features(openkeyboardsam_nlp PRIVATE cxx_std_17)

find_library(log-lib log)
target_link_libraries(openkeyboardsam_nlp ${log-lib})
