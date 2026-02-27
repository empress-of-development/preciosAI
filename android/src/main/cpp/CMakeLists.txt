cmake_minimum_required(VERSION 3.10.2)
project(preciosAI)

set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)

add_library(postprocess_cpp SHARED
        ${CMAKE_CURRENT_SOURCE_DIR}/native-lib.cpp
)

find_library(log_lib log)

target_link_libraries(postprocess_cpp
        ${log_lib}
)
