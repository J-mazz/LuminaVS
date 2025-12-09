# Lumina Virtual Studio CMake Configuration
# Configures native build for Vulkan/OpenGL rendering engine

cmake_minimum_required(VERSION 3.22.1)

project("lumina_engine" 
    VERSION 1.0.0 
    LANGUAGES CXX
)

# C++ Standard
set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)
set(CMAKE_CXX_EXTENSIONS OFF)

# Build type specific flags
set(CMAKE_CXX_FLAGS_DEBUG "-g -O0 -DDEBUG")
set(CMAKE_CXX_FLAGS_RELEASE "-O3 -DNDEBUG")

# Common flags
set(CMAKE_CXX_FLAGS "${CMAKE_CXX_FLAGS} -Wall -Wextra -fexceptions -frtti")

# ============================================================================
# Source Files
# ============================================================================

set(LUMINA_SOURCES
    native-lib.cpp
    lumina_engine.cpp
    renderer_gles.cpp
    renderer_vulkan.cpp
    json_parser.cpp
)

set(LUMINA_HEADERS
    engine_structs.h
    lumina_engine.h
    renderer_gles.h
    renderer_vulkan.h
    json_parser.h
)

# ============================================================================
# Main Library
# ============================================================================

add_library(lumina_engine SHARED
    ${LUMINA_SOURCES}
    ${LUMINA_HEADERS}
)

# ============================================================================
# Find Required Packages
# ============================================================================

# Android NDK Libraries
find_library(log-lib log)
find_library(android-lib android)
find_library(jnigraphics-lib jnigraphics)
find_library(egl-lib EGL)
find_library(glesv3-lib GLESv3)

# Vulkan (Android NDK)
find_library(vulkan-lib vulkan)

# ============================================================================
# Oboe Audio Library (Prefab)
# ============================================================================

find_package(oboe REQUIRED CONFIG)

# ============================================================================
# OpenCV (if available via prefab or local build)
# ============================================================================

# Option 1: Using OpenCV from a local build
set(OpenCV_DIR "${CMAKE_SOURCE_DIR}/../opencv/sdk/native/jni" CACHE PATH "OpenCV SDK path")
find_package(OpenCV QUIET)

if(OpenCV_FOUND)
    message(STATUS "OpenCV found: ${OpenCV_VERSION}")
    target_compile_definitions(lumina_engine PRIVATE LUMINA_HAS_OPENCV)
else()
    message(WARNING "OpenCV not found - some features will be disabled")
endif()

# ============================================================================
# Include Directories
# ============================================================================

target_include_directories(lumina_engine PRIVATE
    ${CMAKE_CURRENT_SOURCE_DIR}
    ${ANDROID_NDK}/sources/android/native_app_glue
    ${CMAKE_CURRENT_SOURCE_DIR}/generated
)

if(OpenCV_FOUND)
    target_include_directories(lumina_engine PRIVATE ${OpenCV_INCLUDE_DIRS})
endif()

# ============================================================================
# Link Libraries
# ============================================================================

target_link_libraries(lumina_engine
    # Android core
    ${log-lib}
    ${android-lib}
    ${jnigraphics-lib}
    
    # Graphics
    ${egl-lib}
    ${glesv3-lib}
    ${vulkan-lib}
    
    # Audio
    oboe::oboe
    
    # Android native support
    android
    mediandk
)

if(OpenCV_FOUND)
    target_link_libraries(lumina_engine ${OpenCV_LIBS})
endif()

# ============================================================================
# Compiler Definitions
# ============================================================================

target_compile_definitions(lumina_engine PRIVATE
    # Platform
    ANDROID
    LUMINA_VERSION_MAJOR=1
    LUMINA_VERSION_MINOR=0
    LUMINA_VERSION_PATCH=0
    
    # Graphics API selection
    LUMINA_USE_VULKAN
    LUMINA_USE_GLES3
    VK_USE_PLATFORM_ANDROID_KHR
    
    # Memory alignment for GPU buffers
    LUMINA_GPU_BUFFER_ALIGNMENT=256
)

# Debug-specific definitions
if(CMAKE_BUILD_TYPE MATCHES Debug)
    target_compile_definitions(lumina_engine PRIVATE
        LUMINA_DEBUG
        LUMINA_ENABLE_VALIDATION
    )
endif()

# ============================================================================
# Optimization Flags
# ============================================================================

if(CMAKE_BUILD_TYPE MATCHES Release)
    # Enable link-time optimization
    set_property(TARGET lumina_engine PROPERTY INTERPROCEDURAL_OPTIMIZATION TRUE)
    
    # ARM NEON optimizations (for arm64-v8a)
    if(ANDROID_ABI STREQUAL "arm64-v8a")
        target_compile_options(lumina_engine PRIVATE -march=armv8-a+simd)
    endif()
endif()

# ============================================================================
# Output Configuration
# ============================================================================

set_target_properties(lumina_engine PROPERTIES
    LIBRARY_OUTPUT_DIRECTORY "${CMAKE_LIBRARY_OUTPUT_DIRECTORY}/${ANDROID_ABI}"
    VERSION ${PROJECT_VERSION}
    SOVERSION 1
)

# Print configuration summary
message(STATUS "=== Lumina Engine Build Configuration ===")
message(STATUS "Version: ${PROJECT_VERSION}")
message(STATUS "ABI: ${ANDROID_ABI}")
message(STATUS "Build Type: ${CMAKE_BUILD_TYPE}")
message(STATUS "C++ Standard: ${CMAKE_CXX_STANDARD}")
message(STATUS "Vulkan: ${vulkan-lib}")
message(STATUS "OpenCV: ${OpenCV_FOUND}")
message(STATUS "==========================================")
