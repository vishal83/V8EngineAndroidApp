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

# QuickJS configuration
set(QUICKJS_DIR ${CMAKE_CURRENT_SOURCE_DIR}/quickjs)

# Include directories
include_directories(${V8_INCLUDE_DIR})
include_directories(${CMAKE_CURRENT_SOURCE_DIR})
include_directories(${QUICKJS_DIR})

# QuickJS compile definitions for Android
add_definitions(-DCONFIG_VERSION="2025-04-26")
add_definitions(-DCONFIG_ANDROID=1)
add_definitions(-D_GNU_SOURCE)
add_definitions(-DCONFIG_BIGNUM)
# add_definitions(-DCONFIG_ATOMICS)  # Already defined in quickjs.c

# QuickJS optimization flags
if(CMAKE_BUILD_TYPE STREQUAL "Release")
    add_definitions(-DNDEBUG)
    add_definitions(-DJS_STRICT_NAN_BOXING)
    set(CMAKE_C_FLAGS_RELEASE "${CMAKE_C_FLAGS_RELEASE} -O3 -flto")
    set(CMAKE_CXX_FLAGS_RELEASE "${CMAKE_CXX_FLAGS_RELEASE} -O3 -flto")
endif()

# Creates and names a library, sets it as either STATIC
# or SHARED, and provides the relative paths to its source code.
add_library(${CMAKE_PROJECT_NAME} SHARED
    # List C/C++ source files with relative paths to this CMakeLists.txt.
    v8integration.cpp
        bytetransfer.cpp
        quickjs_integration.cpp
        # Real QuickJS source files
        quickjs/quickjs.c
        quickjs/cutils.c
        quickjs/libregexp.c
        quickjs/libunicode.c
        quickjs/quickjs-libc.c
        quickjs/dtoa.c)

# Optional: Link against pre-built static QuickJS library if available
# Uncomment these lines if you've built static libraries using build_quickjs.sh
#set(QUICKJS_LIB_DIR ${QUICKJS_DIR}/lib/${ANDROID_ABI})
#if(EXISTS ${QUICKJS_LIB_DIR}/libquickjs.a)
#    message(STATUS "Using pre-built QuickJS library: ${QUICKJS_LIB_DIR}/libquickjs.a")
#    target_link_libraries(${CMAKE_PROJECT_NAME} ${QUICKJS_LIB_DIR}/libquickjs.a)
#else()
#    message(STATUS "Using QuickJS source files (no pre-built library found)")
#endif()

# Specifies libraries CMake should link to your target library.
target_link_libraries(${CMAKE_PROJECT_NAME}
    # List libraries link to the target library
    android
    log
    m  # Math library for QuickJS
)