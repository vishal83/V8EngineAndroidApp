# For more information about using CMake with Android Studio, read the
# documentation: https://d.android.com/studio/projects/add-native-code.html.

# Sets the minimum CMake version required for this project.
cmake_minimum_required(VERSION 3.22.1)

# Declares the project name.
project("v8integration")

# Set C++ standard
set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)

# V8 configuration
set(V8_DIR ${CMAKE_CURRENT_SOURCE_DIR}/v8)
set(V8_INCLUDE_DIR ${V8_DIR}/include)
set(V8_LIB_DIR ${V8_DIR}/lib/${ANDROID_ABI})

# Include V8 headers
include_directories(${V8_INCLUDE_DIR})

# Creates and names a library, sets it as either STATIC
# or SHARED, and provides the relative paths to its source code.
add_library(${CMAKE_PROJECT_NAME} SHARED
    # List C/C++ source files with relative paths to this CMakeLists.txt.
    v8integration.cpp
    bytetransfer.cpp)

# Specifies libraries CMake should link to your target library.
target_link_libraries(${CMAKE_PROJECT_NAME}
    # List libraries link to the target library
    android
    log)