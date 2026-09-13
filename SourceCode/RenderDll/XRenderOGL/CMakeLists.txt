project(XRenderOGL)

add_definitions(-DENABLE_FRAME_PROFILER -DPROC_INTEL -DOPENGL -D_USRDLL -DXRENDEROGL_EXPORTS -DDO_RENDERLOG -DDO_RENDERSTATS -D_RENDERER -DUSE_3DC)
if (${ARCH_TYPE} STREQUAL "x86")
    add_definitions(-DDO_ASM)
endif()

option(DISABLE_CG "DISABLE CG Compiler shaders" OFF)

# On Android, force DISABLE_CG (no libCg.so) and allow GLES via ANGLE
if(ANDROID)
    set(DISABLE_CG ON CACHE BOOL "Disable Cg on Android" FORCE)
    message(STATUS "[XRenderOGL] Android: forcing DISABLE_CG=ON, GLES via ANGLE/system")
endif()

if (NOT MSVC AND NOT DISABLE_CG)
#set(CG_LIB_PATH "/path/to/cg/lib")
if ("${CG_LIB_PATH}" STREQUAL "")
        message( FATAL_ERROR "CG_LIB_PATH is not set.\nYou must set it to a path containing libCgGL.so and libCg.so.\nYou can download the Cg library at https://developer.nvidia.com/cg-toolkit-download" )
endif()
endif() #NOT MSVC

add_definitions(-DUSE_SDL)
if (DISABLE_CG)
add_definitions(-DDISABLE_CG)
endif()

if(ANDROID)
    add_definitions(-DANDROID -DGLES)
endif()

set(SRC_FILES
    "../Common/3DUtils.cpp"
    "../Common/CRT.cpp"
    "../Common/EvalFuncs_C.cpp"
    "../Common/EvalFuncs_RE.cpp"
    "../Common/LeafBufferCreate.cpp"
    "../Common/LeafBufferRender.cpp"
    "../Common/LeafBufferSerialize.cpp"
    "../Common/LightMaterial.cpp"
    "../Common/Names.cpp"
    "../Common/NvTriStrip/NvTriStrip.cpp"
    "../Common/NvTriStrip/NvTriStripObjects.cpp"
    "../Common/pip_addons.cpp"
    "../Common/RendElements/CRE2DQuad.cpp"
    "../Common/RendElements/CREBeam.cpp"
    "../Common/RendElements/CREClientPoly.cpp"
    "../Common/RendElements/CREClientPoly2D.cpp"
    "../Common/RendElements/CREDummy.cpp"
    "../Common/RendElements/CREFlares.cpp"
    "../Common/RendElements/CREFlashBang.cpp"
    "../Common/RendElements/CREGlare.cpp"
    "../Common/RendElements/CREHDRProcess.cpp"
    "../Common/RendElements/CREOcean.cpp"
    "../Common/RendElements/CREOcLeaf.cpp"
    "../Common/RendElements/CREOclusionQuery.cpp"
    "../Common/RendElements/CREParticleSpray.cpp"
    "../Common/RendElements/CREPolyBlend.cpp"
    "../Common/RendElements/CREPolyMesh.cpp"
    "../Common/RendElements/CREPrefabGeom.cpp"
    "../Common/RendElements/CREScreenProcess.cpp"
    "../Common/RendElements/CRESky.cpp"
    "../Common/RendElements/CRETempMesh.cpp"
    "../Common/RendElements/CRETerrainSector.cpp"
    "../Common/RendElements/CRETriMeshAdditionalShadow.cpp"
    "../Common/RendElements/CRETriMeshShadow.cpp"
    "../Common/RendElements/FFT_SSE.cpp"
    "../Common/RendElements/RendElement.cpp"
    "../Common/Renderer.cpp"
    "../Common/ResFile.cpp"
    "../Common/Shaders/Parser.cpp"
    "../Common/Shaders/ShaderComponents.cpp"
    "../Common/Shaders/ShaderCore.cpp"
    "../Common/Shaders/ShaderParse.cpp"
    "../Common/Shaders/ShaderScript.cpp"
    "../Common/Shaders/ShaderTemplate.cpp"
    "../Common/Textures/Image/BmpImage.cpp"
    "../Common/Textures/Image/CImage.cpp"
    "../Common/Textures/Image/DDSImage.cpp"
    "../Common/Textures/Image/inv_cmap.cpp"
    "../Common/Textures/Image/JpgImage.cpp"
    "../Common/Textures/Image/PcxImage.cpp"
    "../Common/Textures/Image/Quantize.cpp"
    "../Common/Textures/Image/TgaImage.cpp"
    "../Common/Textures/TexMan.cpp"
    "../Common/Textures/TexManShadows.cpp"
    "../Common/Textures/TexManStreaming.cpp"
    "../RenderPCH.cpp"
    "GLCGPShader.cpp"
    "GLCGVProgram.cpp"
    "GLFont.cpp"
    "GLHelper.cpp"
    "GLLog.cpp"
    "GLObjSprites.cpp"
    "GLPBuffer.cpp"
    "GLRendPipeline.cpp"
    "GLREOcean.cpp"
    "GLRERender.cpp"
    "GLREShadowVolume.cpp"
    "GLScreenRender.cpp"
    "GLShaders.cpp"
    "GLSystem.cpp"
    "GLTextures.cpp"
    "GLTexturesStreaming.cpp"
    "GL_Renderer.cpp"
    "GL_Shadows.cpp"
    "GL_Textures.cpp"
    "GL_VertBuffer.cpp"
    "NVParse/nvparse.cpp"
    "NVParse/nvparse_errors.cpp"
    "NVParse/ps1.0_program.cpp"
    "NVParse/rc1.0_combiners.cpp"
    "NVParse/rc1.0_final.cpp"
    "NVParse/rc1.0_general.cpp"
    "NVParse/ts1.0_inst.cpp"
    "NVParse/ts1.0_inst_list.cpp"
    "NVParse/vcp1.0_impl.cpp"
    "NVParse/vp1.0_impl.cpp"
    "NVParse/vs1.0_inst.cpp"
    "NVParse/vs1.0_inst_list.cpp"
    "NVParse/vsp1.0_impl.cpp"
    "NVParse/_ps1.0_lexer.cpp"
    "NVParse/_ps1.0_parser.cpp"
    "NVParse/_rc1.0_lexer.cpp"
    "NVParse/_rc1.0_parser.cpp"
    "NVParse/_ts1.0_lexer.cpp"
    "NVParse/_ts1.0_parser.cpp"
    "NVParse/_vs1.0_lexer.cpp"
    "NVParse/_vs1.0_parser.cpp"
    "state_to_nvparse_text.cpp"
)

if (${ARCH_TYPE} STREQUAL "x86")
    set(ARCH_LIBS ijl15.lib)
else() #x64
    set(ARCH_LIBS)
    list(APPEND SRC_FILES
    "../Common/Textures/Image/jpeg6/jcomapi.cpp"
    "../Common/Textures/Image/jpeg6/jdapimin.cpp"
    "../Common/Textures/Image/jpeg6/jdapistd.cpp"
    "../Common/Textures/Image/jpeg6/jdatasrc.cpp"
    "../Common/Textures/Image/jpeg6/jdcoefct.cpp"
    "../Common/Textures/Image/jpeg6/jdcolor.cpp"
    "../Common/Textures/Image/jpeg6/jddctmgr.cpp"
    "../Common/Textures/Image/jpeg6/jdhuff.cpp"
    "../Common/Textures/Image/jpeg6/jdinput.cpp"
    "../Common/Textures/Image/jpeg6/jdmainct.cpp"
    "../Common/Textures/Image/jpeg6/jdmarker.cpp"
    "../Common/Textures/Image/jpeg6/jdmaster.cpp"
    "../Common/Textures/Image/jpeg6/jdpostct.cpp"
    "../Common/Textures/Image/jpeg6/jdsample.cpp"
    "../Common/Textures/Image/jpeg6/jdtrans.cpp"
    "../Common/Textures/Image/jpeg6/jerror.cpp"
    "../Common/Textures/Image/jpeg6/jfdctflt.cpp"
    "../Common/Textures/Image/jpeg6/jidctflt.cpp"
    "../Common/Textures/Image/jpeg6/jmemmgr.cpp"
    "../Common/Textures/Image/jpeg6/jmemnobs.cpp"
    "../Common/Textures/Image/jpeg6/jutils.cpp"
    )
endif()

add_library(${PROJECT_NAME} SHARED ${SRC_FILES})

target_include_directories(${PROJECT_NAME} PRIVATE
    ${CMAKE_CURRENT_SOURCE_DIR}
    ${CMAKE_SOURCE_DIR}/SourceCode/CryCommon
    ${CMAKE_SOURCE_DIR}/SourceCode/RenderDll
    ${CMAKE_SOURCE_DIR}/SourceCode/RenderDll/Common/Shaders
)

target_link_directories(${PROJECT_NAME} PRIVATE
    Libraries
    ${CG_LIB_PATH}
)

if (MSVC)
    set(CMAKE_SHARED_LINKER_FLAGS "${CMAKE_SHARED_LINKER_FLAGS} /NODEFAULTLIB:libcp.lib /NODEFAULTLIB:LIBC.lib")
    set(SYSTEM_DEPS
        "nvDXTlib.lib"
        "glu32.lib"
        "OpenGL32.lib"
        "cg.lib"
        "cgGL.lib"
    )
else()
    if(ANDROID)
        # On Android link EGL/GLESv2 (via ANGLE if available) + android log
        find_library(ANDROID_LIB android)
        find_library(LOG_LIB log)
        set(SYSTEM_DEPS
            EGL
            GLESv2
            GLESv3
            ${ANDROID_LIB}
            ${LOG_LIB}
        )
        # EGL/GLES come from the system driver (no translation layer)
        message(STATUS "[XRenderOGL] Linking for Android with system EGL/GLES")
    else()
        set(SYSTEM_DEPS
            GLU
        )
    endif()
    if (NOT DISABLE_CG)
        if(NOT ANDROID)
            set (SYSTEM_DEPS ${SYSTEM_DEPS} Cg CgGL)
        endif()
    endif()
endif()

target_link_libraries(${PROJECT_NAME} PRIVATE
    ${ARCH_LIBS}
    ${SYSTEM_DEPS}
)

# Android: enable LTO and section GC for renderer too
if(ANDROID)
    target_compile_options(${PROJECT_NAME} PRIVATE -O3 -flto=thin -ffast-math -fomit-frame-pointer -ffunction-sections -fdata-sections -Wno-error)
    target_link_options(${PROJECT_NAME} PRIVATE -Wl,--gc-sections -Wl,--icf=all -flto=thin)
endif()
