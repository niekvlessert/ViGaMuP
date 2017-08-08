cmake_minimum_required(VERSION 2.8.9)

add_definitions(-DDISABLE_HWOPL_SUPPORT=1)
add_definitions(-DDISABLE_HW_SUPPORT)
add_definitions(-DUSE_LIBAO)
add_definitions(-DDEBUG=0)
add_definitions(-DBYTE_ORDER=1)
add_definitions(-DVGM_LITTLE_ENDIAN)
add_definitions(-DCONSOLE_MODE=0 -DADDITIONAL_FORMATS -DSET_CONSOLE_TITLE -DENABLE_ALL_CORES)

file(GLOB_RECURSE Foo_HEADERS "src/*.h")

set (Foo_INCLUDE_DIRS "")
foreach (_headerFile ${Foo_HEADERS})
    get_filename_component(_dir ${_headerFile} PATH)
    list (APPEND Foo_INCLUDE_DIRS ${_dir})
endforeach()
list(REMOVE_DUPLICATES Foo_INCLUDE_DIRS)
include_directories(${Foo_INCLUDE_DIRS})

file(GLOB_RECURSE SOURCES "src/*.c")
#message(STATUS ${SOURCES})
#message(STATUS ${PROJECT_SOURCE_DIR})
list(REMOVE_ITEM SOURCES "${PROJECT_SOURCE_DIR}/src/main/cpp/vgmplay/src/chips/opl.c")
list(REMOVE_ITEM SOURCES "${PROJECT_SOURCE_DIR}/src/main/cpp/vgmplay/src/VGMPlayUI.c")
list(REMOVE_ITEM SOURCES "${PROJECT_SOURCE_DIR}/src/main/cpp/vgmplay/src/Stream.c")
list(REMOVE_ITEM SOURCES "${PROJECT_SOURCE_DIR}/src/main/cpp/vgmplay/src/pt_ioctl.c")
list(REMOVE_ITEM SOURCES "${PROJECT_SOURCE_DIR}/src/main/cpp/vgmplay/src/chips/scsplfo.c")
list(REMOVE_ITEM SOURCES "${PROJECT_SOURCE_DIR}/src/main/cpp/vgmplay/src/chips/emu2413.c") #be careful, libss has this to... see fix.sh in libkss
#list(REMOVE_ITEM SOURCES "${PROJECT_SOURCE_DIR}/src/main/cpp/vgmplay/src/chips/sn76489.c")
#list(REMOVE_ITEM SOURCES "${PROJECT_SOURCE_DIR}/src/main/cpp/vgmplay/src/chips/sn76496.c")
#list(REMOVE_ITEM SOURCES "${PROJECT_SOURCE_DIR}/src/main/cpp/vgmplay/src/chips/ay8910.c")
list(REMOVE_ITEM SOURCES "${PROJECT_SOURCE_DIR}/src/main/cpp/vgmplay/src/chips/emu2149.c") #this will scew up psg emulation in libkss... disable it
list(REMOVE_ITEM SOURCES "${PROJECT_SOURCE_DIR}/src/main/cpp/vgmplay/src/vgm2pcm.c")
list(REMOVE_ITEM SOURCES "${PROJECT_SOURCE_DIR}/src/main/cpp/vgmplay/src/vgm2wav.c")

add_library(vgmplay STATIC ${SOURCES})
target_link_libraries(vgmplay z m)

add_executable(vgmplay_ui EXCLUDE_FROM_ALL src/VGMPlayUI.c)
target_link_libraries(vgmplay_ui vgmplay)
