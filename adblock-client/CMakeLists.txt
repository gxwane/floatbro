cmake_minimum_required(VERSION 3.22.1)

project(adblock-client)

if(NOT CMAKE_BUILD_TYPE)
    set(CMAKE_BUILD_TYPE Release CACHE STRING "Build type" FORCE)
endif()

set(CMAKE_C_FLAGS_RELEASE "${CMAKE_C_FLAGS_RELEASE} -Oz -s")
set(CMAKE_CXX_FLAGS_RELEASE "${CMAKE_CXX_FLAGS_RELEASE} -Oz -s")

set(CMAKE_CXX_FLAGS "${CMAKE_CXX_FLAGS} -fvisibility=hidden -fvisibility-inlines-hidden -ffunction-sections -fdata-sections")
set(CMAKE_CXX_FLAGS "${CMAKE_CXX_FLAGS} -fno-exceptions -fno-rtti -fno-unwind-tables -fno-asynchronous-unwind-tables")
set(CMAKE_C_FLAGS "${CMAKE_C_FLAGS} -fvisibility=hidden -ffunction-sections -fdata-sections")
set(CMAKE_SHARED_LINKER_FLAGS "${CMAKE_SHARED_LINKER_FLAGS} -Wl,--gc-sections -Wl,--exclude-libs,ALL -Wl,--icf=all -Wl,--pack-dyn-relocs=android")

# Android 15/16 16KB Page Size mandatory linker option
add_link_options("-Wl,-z,max-page-size=16384")

add_library(
    adblock-client
    SHARED
    src/main/cpp/adblockclient-lib.cpp
    src/main/cpp/third-party/ad-block/ad_block_client.cc
    src/main/cpp/third-party/ad-block/cosmetic_filter.cc
    src/main/cpp/third-party/ad-block/filter.cc
    src/main/cpp/third-party/ad-block/no_fingerprint_domain.cc
    src/main/cpp/third-party/ad-block/context_domain.cc
    src/main/cpp/third-party/ad-block/protocol.cc
    src/main/cpp/third-party/bloom-filter-cpp/BloomFilter.cpp
    src/main/cpp/third-party/hashset-cpp/hashFn.cc
    src/main/cpp/third-party/hashset-cpp/hash_set.cc
)

find_library(log-lib log)

target_link_libraries(
    adblock-client
    ${log-lib}
)

target_compile_options(
    adblock-client
    PRIVATE
    -Wall
    -Wextra
    -Werror=return-type
)

option(ENABLE_CLANG_TIDY "Enable clang-tidy static analysis" OFF)
if(ENABLE_CLANG_TIDY)
    find_program(CLANG_TIDY_EXE NAMES clang-tidy)
    if(CLANG_TIDY_EXE)
        set(CMAKE_CXX_CLANG_TIDY "${CLANG_TIDY_EXE};--config-file=${CMAKE_CURRENT_SOURCE_DIR}/../.clang-tidy")
        message(STATUS "Clang-tidy enabled: ${CLANG_TIDY_EXE}")
    else()
        message(WARNING "ENABLE_CLANG_TIDY was requested, but clang-tidy executable was not found.")
    endif()
endif()


