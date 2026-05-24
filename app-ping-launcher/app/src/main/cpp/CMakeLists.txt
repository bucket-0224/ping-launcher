cmake_minimum_required(VERSION 3.22.1)
project("pingjvm")


# ── 기존 pingjvm ──────────────────────────────────────────────
add_library(pingjvm SHARED pingjvm.cpp)
find_library(log-lib log)
target_link_libraries(pingjvm ${log-lib})

# ── libglfw.so (egl_bridge 기반) ──────────────────────────────
add_library(glfw SHARED
        pojav_jni/egl_bridge.c
        pojav_jni/utils.c
        pojav_jni/stdio_is.c
        pojav_jni/bigcoreaffinity.c
        pojav_jni/input_bridge_v3.c
        pojav_jni/environ/environ.c
        pojav_jni/ctxbridges/gl_bridge.c       # ✅ 추가
        pojav_jni/ctxbridges/egl_loader.c      # ✅ 추가
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

find_library(log-lib log)
find_library(EGL-lib EGL)
find_library(GLESv2-lib GLESv2)
find_library(android-lib android)
find_library(dl-lib dl)

target_include_directories(glfw PRIVATE
        pojav_jni
        pojav_jni/GL
        pojav_jni/ctxbridges
        pojav_jni/environ
        pojav_jni/driver_helper
        pojav_jni/jvm_hooks
        pojav_jni/native_hooks
        pojav_jni/awt_xawt
)

target_link_libraries(glfw
        ${log-lib}
        ${EGL-lib}
        ${GLESv2-lib}
        ${android-lib}
        ${dl-lib}
        android
)