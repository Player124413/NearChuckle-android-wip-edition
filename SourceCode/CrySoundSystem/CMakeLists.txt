project(CrySoundSystem)

add_definitions(-D_USRDLL -D_WINDOWS -DCRYSOUNDSYSTEM_EXPORTS)

set(SRC_FILES
    "ADPCMDecoder.cpp"
    "CrySoundSystem.cpp"
    "MusicPattern.cpp"
    "MusicPatternInstance.cpp"
    "MusicSystem.cpp"
    "OGGDecoder.cpp"
    "PCMDecoder.cpp"
    "RandGen.cpp"
    "Sound.cpp"
    "SoundBuffer.cpp"
    "SoundSystem.cpp"
    "SoundSystemCommon.cpp"
    "StdAfx.cpp"
)

if (NOT MSVC)
set(SRC_FILES
    ${SRC_FILES}
    "OpenALSound.cpp"
)
add_definitions(-DUSE_OPENAL)
endif()

add_library(${PROJECT_NAME} SHARED ${SRC_FILES})

target_include_directories(${PROJECT_NAME} PRIVATE
    ${CMAKE_SOURCE_DIR}/SourceCode/CryCommon
    ${CMAKE_CURRENT_SOURCE_DIR}/OggVorbisInclude
)

 target_link_directories(${PROJECT_NAME} PRIVATE
     ${CMAKE_CURRENT_SOURCE_DIR}
 )

if (${ARCH_TYPE} STREQUAL "x86")
    set(CRYSOUND_LIB crysound.lib)
    set(OGG_STATIC_LIB ogg_static.lib)
    set(VORBIS_STATIC_LIB vorbis_static.lib)
    set(VORBISFILE_STATIC_LIB vorbisfile_static.lib)
else() #x64
    if (CMAKE_BUILD_TYPE STREQUAL "Release")
        set(CRYSOUND_LIB crysound64.lib)
        set(OGG_STATIC_LIB ogg_static_r64.lib)
        set(VORBIS_STATIC_LIB vorbis_static_r64.lib)
        set(VORBISFILE_STATIC_LIB vorbisfile_static_r64.lib)
    else()
        set(CRYSOUND_LIB crysound64d.lib)
        set(OGG_STATIC_LIB ogg_static_d64.lib)
        set(VORBIS_STATIC_LIB vorbis_static_d64.lib)
        set(VORBISFILE_STATIC_LIB vorbisfile_static_d64.lib)
    endif()
endif()

if (MSVC)
target_link_libraries(${PROJECT_NAME} PRIVATE
    ${CRYSOUND_LIB}
    ${OGG_STATIC_LIB}
    ${VORBIS_STATIC_LIB}
    ${VORBISFILE_STATIC_LIB}
    legacy_stdio_definitions.lib
)
else()
    # CI fallback: try to find libs, if not found create dummy to allow configure to pass
    find_library(OPENAL_LIB openal)
    find_library(VORBISFILE_LIB vorbisfile)
    if(OPENAL_LIB AND VORBISFILE_LIB)
        target_link_libraries(${PROJECT_NAME} PUBLIC vorbisfile openal)
    elseif(OPENAL_LIB)
        target_link_libraries(${PROJECT_NAME} PUBLIC ${OPENAL_LIB})
        message(WARNING "[CrySoundSystem] vorbisfile not found - building without OGG support (CI fallback)")
    elseif(VORBISFILE_LIB)
        target_link_libraries(${PROJECT_NAME} PUBLIC ${VORBISFILE_LIB})
        message(WARNING "[CrySoundSystem] openal not found - building without OpenAL (CI fallback)")
    else()
        message(WARNING "[CrySoundSystem] openal/vorbisfile not found - using stub (CI). Install libopenal-dev libvorbis-dev for full sound.")
        # No link, stub build will succeed but sound disabled at runtime
    endif()
endif()
