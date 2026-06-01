cmake_minimum_required(VERSION 3.22.1)
project("pingjvm")


# ── 기존 pingjvm ──────────────────────────────────────────────
add_library(pingjvm SHARED pingjvm.cpp)
find_library(log-lib log)
target_link_libraries(pingjvm ${log-lib})


# ── PojavLauncher 공통 소스 ─────────────────────────────────────
set(POJAV_SOURCES
        pojav_jni/egl_bridge.c
        pojav_jni/utils.c
        pojav_jni/stdio_is.c
        pojav_jni/bigcoreaffinity.c
        pojav_jni/input_bridge_v3.c
        pojav_jni/environ/environ.c
        pojav_jni/ctxbridges/gl_bridge.c
        pojav_jni/ctxbridges/egl_loader.c
        pojav_jni/ctxbridges/osm_bridge.c
        pojav_jni/ctxbridges/osmesa_loader.c
        pojav_jni/ctxbridges/loader_dlopen.c
        pojav_jni/ctxbridges/swap_interval_no_egl.c
        pojav_jni/driver_helper/nsbypass.c
        pojav_jni/driver_helper/hook.c
        pojav_jni/awt_xawt/xawt_fake.c
        pojav_jni/jvm_hooks/lwjgl_dlopen_hook.c
        pojav_jni/jvm_hooks/java_exec_hooks.c
        pojav_jni/jvm_hooks/emui_iterator_fix_hook.c
        pojav_jni/jre_launcher.c
)

set(POJAV_INCLUDES
        pojav_jni
        pojav_jni/GL
        pojav_jni/ctxbridges
        pojav_jni/environ
        pojav_jni/driver_helper
        pojav_jni/jvm_hooks
        pojav_jni/native_hooks
        pojav_jni/awt_xawt
)


find_library(log-lib log)
find_library(EGL-lib EGL)
find_library(GLESv2-lib GLESv2)
find_library(android-lib android)
find_library(dl-lib dl)


# ── libglfw.so (자바측 System.loadLibrary("glfw") 가 로드) ──────
add_library(glfw SHARED ${POJAV_SOURCES})
target_include_directories(glfw PRIVATE ${POJAV_INCLUDES})
target_link_libraries(glfw
        ${log-lib}
        ${EGL-lib}
        ${GLESv2-lib}
        ${android-lib}
        ${dl-lib}
        android
)


# ── libpojavexec.so (자체 또는 dlopen 으로 로드되는 PojavLauncher core) ──
# 같은 소스를 사용하므로 우리 패치(PATCH_V4_ACTIVE 등)가 양쪽에 모두 들어감.
# 어디서 호출하든 패치 코드 실행.
add_library(pojavexec SHARED ${POJAV_SOURCES})
target_include_directories(pojavexec PRIVATE ${POJAV_INCLUDES})
target_link_libraries(pojavexec
        ${log-lib}
        ${EGL-lib}
        ${GLESv2-lib}
        ${android-lib}
        ${dl-lib}
        android
)