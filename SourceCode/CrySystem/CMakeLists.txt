project(CrySystem)

add_definitions(-D_USRDLL -DCRYSYSTEM_EXPORTS)

set(SRC_FILES
    "ApplicationHelper.cpp"
    "CompressedFile.cpp"
    "CPUDetect.cpp"
    "CryMemoryManager.cpp"
    "CryPak.cpp"
    "CrySizerImpl.cpp"
    "CrySizerStats.cpp"
    "DataProbe.cpp"
    "DebugCallStack.cpp"
    "DllMain.cpp"
    "DownloadManager.cpp"
    "FrameProfileRender.cpp"
    "FrameProfileSystem.cpp"
    "getdxver.cpp"
    "HTTPDownloader.cpp"
    "Log.cpp"
    "Mailer.cpp"
    "md5.c"
    "MTSafeAllocator.cpp"
    "RandGen.cpp"
    "RefReadStream.cpp"
    "RefReadStreamProxy.cpp"
    "RefStreamEngine.cpp"
    "ScriptBinding.cpp"
    "ScriptObjectAnimation.cpp"
    "ScriptObjectEntity.cpp"
    "ScriptObjectMovie.cpp"
    "ScriptObjectParticle.cpp"
    "ScriptObjectScript.cpp"
    "ScriptObjectSound.cpp"
    "ScriptObjectSystem.cpp"
    "ScriptSink.cpp"
    "SourceSafeHelper.cpp"
    "StdAfx.cpp"
    "StreamEngine.cpp"
    "System.cpp"
    "SystemCFG.cpp"
    "SystemInit.cpp"
    "SystemRender.cpp"
    "SystemWin32.cpp"
    "Timer.cpp"
    "XConsole.cpp"
    "XConsoleVariable.cpp"
    "XML/Expat/xmlparse.c"
    "XML/Expat/xmlrole.c"
    "XML/Expat/xmltok.c"
    "XML/xml.cpp"
    "XML/XMLDOMDocumentImpl.cpp"
    "XML/XMLDOMNodeImpl.cpp"
    "XML/XMLDOMNodeListImpl.cpp"
    "XML/_XMLDOMParserImpl.cpp"
    "ZipDirCache.cpp"
    "ZipDirCacheFactory.cpp"
    "ZipDirCacheRW.cpp"
    "ZipDirFind.cpp"
    "ZipDirFindRW.cpp"
    "ZipDirList.cpp"
    "ZipDirStructures.cpp"
    "ZipDirTree.cpp"
    "zlib/adler32.c"
    "zlib/compress.c"
    "zlib/crc32.c"
    "zlib/deflate.c"
    "zlib/gzclose.c"
    "zlib/gzlib.c"
    "zlib/gzread.c"
    "zlib/gzwrite.c"
    "zlib/infback.c"
    "zlib/inffast.c"
    "zlib/inflate.c"
    "zlib/inftrees.c"
    "zlib/trees.c"
    "zlib/uncompr.c"
    "zlib/zutil.c"
)

if(ANDROID)
    list(APPEND SRC_FILES
        "AndroidOptimization.cpp"
        "AndroidStorage.cpp"
    )
    add_definitions(-DANDROID)
endif()

if (MSVC)
    set(SRC_FILES ${SRC_FILES}
        "LuaDebugger/FileTree.cpp"
        "LuaDebugger/LUADBG.cpp"
        "LuaDebugger/LuaDbgInterface.cpp"
    )
    set(SYSTEM_DEPS "wininet.lib")
endif()

add_library(${PROJECT_NAME} SHARED ${SRC_FILES})

target_include_directories(${PROJECT_NAME} PRIVATE
    ${CMAKE_CURRENT_SOURCE_DIR}
    ${CMAKE_CURRENT_SOURCE_DIR}/../CryCommon
)

if(ANDROID)
    find_library(ANDROID_LIB android)
    find_library(LOG_LIB log)
    target_link_libraries(${PROJECT_NAME} PRIVATE
        ${SYSTEM_DEPS} SDL3::SDL3 ${ANDROID_LIB} ${LOG_LIB}
    )
    target_compile_options(${PROJECT_NAME} PRIVATE -O3 -flto=thin -ffast-math -fomit-frame-pointer -ffunction-sections -fdata-sections)
    target_link_options(${PROJECT_NAME} PRIVATE -Wl,--gc-sections -Wl,--icf=all -flto=thin)
else()
    target_link_libraries(${PROJECT_NAME} PRIVATE
        ${SYSTEM_DEPS} SDL3
    )
endif()
