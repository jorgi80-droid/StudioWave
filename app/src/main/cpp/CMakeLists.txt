cmake_minimum_required(VERSION 3.22.1)
project(studiowave_native LANGUAGES CXX)

add_library(studiowave_audio SHARED audio_engine.cpp)
find_library(log-lib log)
find_library(android-lib android)
target_link_libraries(studiowave_audio PRIVATE ${log-lib} ${android-lib})

target_compile_features(studiowave_audio PRIVATE cxx_std_20)
target_compile_options(studiowave_audio PRIVATE -Wall -Wextra -Werror=return-type)