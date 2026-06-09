cmake_minimum_required(VERSION 3.22.1)
project("pingjvm")

# ── bytehook ──────────────────────────────────────────────────
find_package(bytehook REQUIRED CONFIG)

# ── 시스템 라이브러리 ─────────────────────────────────────────
find_library(log-lib log)
find_library(EGL-lib EGL)
find_library(GLESv2-lib GLESv2)
find_library(android-lib android)
find_library(dl-lib dl)

# ── pingjvm (JVM 부팅 — PingLauncher 전용, 유지) ──────────────
add_library(pingjvm SHARED pingjvm.cpp)
target_include_directories(pingjvm PRIVATE
        pojav_jni
        pojav_jni/environ
)
target_link_libraries(pingjvm ${log-lib})

# ─────────────────────────────────────────────────────────────
# 아래 4개는 ZalithLauncher2 의 native libs 사용 (jniLibs/arm64-v8a/)
# 우리 CMake 빌드는 비활성화 — ZL2 patched LWJGL 3.3.6 과 ABI 일치 보장
# ─────────────────────────────────────────────────────────────

#[[
# libglfw.so — ZL2 의 libpojavexec.so 가 통합 제공
add_library(glfw SHARED ${POJAV_SOURCES})
target_include_directories(glfw PRIVATE ${POJAV_INCLUDES})
target_compile_options(glfw PRIVATE ${POJAV_WARN_FLAGS})
target_link_libraries(glfw
        ${log-lib} ${EGL-lib} ${GLESv2-lib} ${android-lib} ${dl-lib}
        bytehook::bytehook
)

# libpojavexec.so — ZL2 의 것 사용
add_library(pojavexec SHARED ${POJAV_SOURCES})
target_include_directories(pojavexec PRIVATE ${POJAV_INCLUDES})
target_compile_options(pojavexec PRIVATE ${POJAV_WARN_FLAGS})
target_link_libraries(pojavexec
        ${log-lib} ${EGL-lib} ${GLESv2-lib} ${android-lib} ${dl-lib}
        bytehook::bytehook
)

# libawt_xawt.so — ZL2 의 것 사용
add_library(awt_xawt SHARED pojav_jni/awt_xawt/xawt_fake.c)
target_include_directories(awt_xawt PRIVATE ${POJAV_INCLUDES})
target_compile_options(awt_xawt PRIVATE ${POJAV_WARN_FLAGS})
target_link_libraries(awt_xawt
        ${log-lib} ${android-lib} ${dl-lib}
)

# libpojavexec_awt.so — ZL2 의 것 사용
add_library(pojavexec_awt SHARED pojav_jni/awt_bridge.c)
target_include_directories(pojavexec_awt PRIVATE ${POJAV_INCLUDES})
target_compile_options(pojavexec_awt PRIVATE ${POJAV_WARN_FLAGS})
target_link_libraries(pojavexec_awt
        ${log-lib} ${android-lib} ${dl-lib}
)
]]