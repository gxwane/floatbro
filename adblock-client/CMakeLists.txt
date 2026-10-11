cmake_minimum_required(VERSION 3.22.1)

project("adblock-client")

set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)

# Android 15 16KB Page Size mandatory linker flag
add_link_options("-Wl,-z,max-page-size=16384")

# Native source directory
file(GLOB_RECURSE NATIVE_SOURCES
    "src/main/cpp/*.cpp"
    "src/main/cpp/*.c"
)

# Placeholder if sources are not yet extracted
if(NOT NATIVE_SOURCES)
    set(NATIVE_SOURCES "src/main/cpp/adblock_placeholder.cpp")
    file(WRITE "${CMAKE_CURRENT_SOURCE_DIR}/src/main/cpp/adblock_placeholder.cpp"
        "// FloatBro native placeholder for build system initialization\n"
        "#include <jni.h>\n"
    )
endif()

add_library(adblock-client SHARED ${NATIVE_SOURCES})

find_library(log-lib log)

target_link_libraries(adblock-client
    ${log-lib}
)
