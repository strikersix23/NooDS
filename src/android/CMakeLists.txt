cmake_minimum_required(VERSION 3.4.1)

set(CMAKE_CXX_FLAGS "${CMAKE_CXX_FLAGS} -std=c++11 -DLOG_LEVEL=0")
set(CMAKE_CXX_FLAGS_RELEASE "${CMAKE_CXX_FLAGS_RELEASE} -Ofast -flto")

add_library(noods-core SHARED
            cpp/interface.cpp
            ../common/nds_icon.cpp
            ../common/screen_layout.cpp
            ../action_replay.cpp
            ../cartridge.cpp
            ../core.cpp
            ../cp15.cpp
            ../div_sqrt.cpp
            ../dldi.cpp
            ../dma.cpp
            ../gpu.cpp
            ../gpu_2d.cpp
            ../gpu_3d.cpp
            ../gpu_3d_renderer.cpp
            ../hle_arm7.cpp
            ../hle_bios.cpp
            ../input.cpp
            ../interpreter.cpp
            ../interpreter_alu.cpp
            ../interpreter_branch.cpp
            ../interpreter_lookup.cpp
            ../interpreter_transfer.cpp
            ../ipc.cpp
            ../memory.cpp
            ../rtc.cpp
            ../save_states.cpp
            ../settings.cpp
            ../spi.cpp
            ../spu.cpp
            ../timers.cpp
            ../wifi.cpp)

target_link_libraries(noods-core jnigraphics OpenSLES)
