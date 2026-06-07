/**
 * glfw_stubs.c
 *
 * PojavLauncher 의 커스텀 libglfw.so 는 pojav* 계열 함수만 export 하며,
 * 표준 GLFW 심볼(glfwInit, glfwGetError 등)을 export 하지 않는다.
 *
 * LWJGL 3.4.x 의 GLFW$Functions.<clinit> 은 libglfw.so 에서 dlsym 으로
 * 각 GLFW 심볼 주소를 조회해 long 필드에 저장한다. 심볼이 없으면
 *   - required 함수: NullPointerException → ExceptionInInitializerError → 크래시
 *   - optional 함수: NULL 저장 (호출 시 IllegalArgumentException)
 *
 * 이 파일은 GLFW$Functions.<clinit> 이 조회하는 모든 심볼에 대해
 * no-op / 안전한 기본값을 반환하는 stub 을 제공한다.
 * 실제 동작(init, swap, input 등)은 pojav* 함수와 JNI 브릿지가 담당한다.
 *
 * 참고: GLFW 상수
 *   GLFW_TRUE            = 1
 *   GLFW_FALSE           = 0
 *   GLFW_NO_ERROR        = 0
 *   GLFW_PLATFORM_NULL   = 0x00060007
 */

#include <stdint.h>
#include <stddef.h>
#include <string.h>

/* ── 타입 별칭 (GLFW 헤더 없이 사용) ─────────────────────────── */
typedef void*   GLFWwindow;
typedef void*   GLFWmonitor;
typedef void*   GLFWcursor;
typedef void*   GLFWallocator;
typedef struct { unsigned short* red; unsigned short* green; unsigned short* blue; unsigned int size; } GLFWgammaramp;
typedef struct { int width; int height; int redBits; int greenBits; int blueBits; int refreshRate; } GLFWvidmode;
typedef struct { float xscale; float yscale; } GLFWcontentscale; /* unused */
typedef struct { int width; int height; unsigned char* pixels; } GLFWimage;
typedef struct { unsigned char buttons[15]; float axes[6]; } GLFWgamepadstate;
typedef void (*GLFWerrorfun)(int, const char*);
typedef void (*GLFWmonitorfun)(GLFWmonitor*, int);
typedef void (*GLFWjoystickfun)(int, int);
typedef void* (*GLFWglproc)(void);

/* ── 초기화 / 종료 ─────────────────────────────────────────── */

int glfwInit(void) {
    /* 실제 초기화는 pojavInit() 에서 수행 */
    return 1; /* GLFW_TRUE */
}

void glfwTerminate(void) { }

void glfwInitHint(int hint, int value) { (void)hint; (void)value; }

void glfwInitAllocator(const GLFWallocator* allocator) { (void)allocator; }

void glfwGetVersion(int* major, int* minor, int* rev) {
    if (major) *major = 3;
    if (minor) *minor = 4;
    if (rev)   *rev   = 0;
}

const char* glfwGetVersionString(void) {
    return "3.4.0 Android-PojavStub";
}

int glfwGetError(const char** description) {
    if (description) *description = NULL;
    return 0; /* GLFW_NO_ERROR */
}

/* ── 에러 콜백 상태 저장 ──────────────────────────────────────
 * GLX._initGlfw() 는 GLFWErrorScope 를 사용해 아래 패턴으로 동작한다:
 *   1. prev = glfwSetErrorCallback(myCallback)  ← 새 콜백 등록, 이전 값 저장
 *   2. glfwInit() 등 GLFW 호출
 *   3. glfwSetErrorCallback(prev)               ← 이전 콜백 복원
 *   4. GLFWErrorScope.close() 에서 현재 콜백 == myCallback 인지 검증
 *      → 다르면 IllegalStateException: "GLFW error callback has unexpectedly changed"
 *
 * stub 이 항상 NULL 을 반환하면 prev=NULL 이 저장되고,
 * close() 에서 현재 콜백(myCallback) != prev(NULL) 이 되어 크래시 발생.
 * 따라서 이전 콜백 포인터를 static 변수에 저장하고 반환해야 한다.
 */
static GLFWerrorfun s_error_callback = NULL;

GLFWerrorfun glfwSetErrorCallback(GLFWerrorfun cbfun) {
    GLFWerrorfun prev = s_error_callback;
    s_error_callback = cbfun;
    return prev;
}

/* ── 플랫폼 ────────────────────────────────────────────────── */

int glfwGetPlatform(void) {
    return 0x00060007; /* GLFW_PLATFORM_NULL */
}

int glfwPlatformSupported(int platform) {
    return (platform == 0x00060007) ? 1 : 0; /* GLFW_PLATFORM_NULL only */
}

/* ── 모니터 ────────────────────────────────────────────────── */

GLFWmonitor** glfwGetMonitors(int* count) {
    if (count) *count = 0;
    return NULL;
}

GLFWmonitor* glfwGetPrimaryMonitor(void) { return NULL; }

void glfwGetMonitorPos(GLFWmonitor* monitor, int* xpos, int* ypos) {
    (void)monitor;
    if (xpos) *xpos = 0;
    if (ypos) *ypos = 0;
}

void glfwGetMonitorWorkarea(GLFWmonitor* monitor, int* xpos, int* ypos, int* width, int* height) {
    (void)monitor;
    if (xpos)   *xpos   = 0;
    if (ypos)   *ypos   = 0;
    if (width)  *width  = 0;
    if (height) *height = 0;
}

void glfwGetMonitorPhysicalSize(GLFWmonitor* monitor, int* widthMM, int* heightMM) {
    (void)monitor;
    if (widthMM)  *widthMM  = 0;
    if (heightMM) *heightMM = 0;
}

void glfwGetMonitorContentScale(GLFWmonitor* monitor, float* xscale, float* yscale) {
    (void)monitor;
    if (xscale) *xscale = 1.0f;
    if (yscale) *yscale = 1.0f;
}

const char* glfwGetMonitorName(GLFWmonitor* monitor) {
    (void)monitor;
    return "Android";
}

void glfwSetMonitorUserPointer(GLFWmonitor* monitor, void* pointer) {
    (void)monitor; (void)pointer;
}

void* glfwGetMonitorUserPointer(GLFWmonitor* monitor) {
    (void)monitor;
    return NULL;
}

GLFWmonitorfun glfwSetMonitorCallback(GLFWmonitorfun cbfun) {
    (void)cbfun;
    return NULL;
}

const GLFWvidmode* glfwGetVideoModes(GLFWmonitor* monitor, int* count) {
    (void)monitor;
    if (count) *count = 0;
    return NULL;
}

const GLFWvidmode* glfwGetVideoMode(GLFWmonitor* monitor) {
    (void)monitor;
    return NULL;
}

void glfwSetGamma(GLFWmonitor* monitor, float gamma) {
    (void)monitor; (void)gamma;
}

const GLFWgammaramp* glfwGetGammaRamp(GLFWmonitor* monitor) {
    (void)monitor;
    return NULL;
}

void glfwSetGammaRamp(GLFWmonitor* monitor, const GLFWgammaramp* ramp) {
    (void)monitor; (void)ramp;
}

/* ── 윈도우 힌트 / 생성 / 소멸 ────────────────────────────── */

void glfwDefaultWindowHints(void) { }

void glfwWindowHint(int hint, int value) { (void)hint; (void)value; }

void glfwWindowHintString(int hint, const char* value) {
    (void)hint; (void)value;
}

GLFWwindow* glfwCreateWindow(int width, int height, const char* title,
                              GLFWmonitor* monitor, GLFWwindow* share) {
    (void)width; (void)height; (void)title; (void)monitor; (void)share;
    /* pojav 는 자체 ANativeWindow 를 사용하므로 더미 포인터 반환 */
    return (GLFWwindow*)1;
}

void glfwDestroyWindow(GLFWwindow* window) { (void)window; }

int glfwWindowShouldClose(GLFWwindow* window) {
    (void)window;
    return 0;
}

void glfwSetWindowShouldClose(GLFWwindow* window, int value) {
    (void)window; (void)value;
}

const char* glfwGetWindowTitle(GLFWwindow* window) {
    (void)window;
    return "";
}

void glfwSetWindowTitle(GLFWwindow* window, const char* title) {
    (void)window; (void)title;
}

void glfwSetWindowIcon(GLFWwindow* window, int count, const GLFWimage* images) {
    (void)window; (void)count; (void)images;
}

void glfwGetWindowPos(GLFWwindow* window, int* xpos, int* ypos) {
    (void)window;
    if (xpos) *xpos = 0;
    if (ypos) *ypos = 0;
}

void glfwSetWindowPos(GLFWwindow* window, int xpos, int ypos) {
    (void)window; (void)xpos; (void)ypos;
}

void glfwGetWindowSize(GLFWwindow* window, int* width, int* height) {
    (void)window;
    if (width)  *width  = 0;
    if (height) *height = 0;
}

void glfwSetWindowSizeLimits(GLFWwindow* window, int minwidth, int minheight,
                              int maxwidth, int maxheight) {
    (void)window; (void)minwidth; (void)minheight; (void)maxwidth; (void)maxheight;
}

void glfwSetWindowAspectRatio(GLFWwindow* window, int numer, int denom) {
    (void)window; (void)numer; (void)denom;
}

void glfwSetWindowSize(GLFWwindow* window, int width, int height) {
    (void)window; (void)width; (void)height;
}

void glfwGetFramebufferSize(GLFWwindow* window, int* width, int* height) {
    (void)window;
    if (width)  *width  = 0;
    if (height) *height = 0;
}

void glfwGetWindowFrameSize(GLFWwindow* window, int* left, int* top,
                             int* right, int* bottom) {
    (void)window;
    if (left)   *left   = 0;
    if (top)    *top    = 0;
    if (right)  *right  = 0;
    if (bottom) *bottom = 0;
}

void glfwGetWindowContentScale(GLFWwindow* window, float* xscale, float* yscale) {
    (void)window;
    if (xscale) *xscale = 1.0f;
    if (yscale) *yscale = 1.0f;
}

float glfwGetWindowOpacity(GLFWwindow* window) {
    (void)window;
    return 1.0f;
}

void glfwSetWindowOpacity(GLFWwindow* window, float opacity) {
    (void)window; (void)opacity;
}

void glfwIconifyWindow(GLFWwindow* window)          { (void)window; }
void glfwRestoreWindow(GLFWwindow* window)          { (void)window; }
void glfwMaximizeWindow(GLFWwindow* window)         { (void)window; }
void glfwShowWindow(GLFWwindow* window)             { (void)window; }
void glfwHideWindow(GLFWwindow* window)             { (void)window; }
void glfwFocusWindow(GLFWwindow* window)            { (void)window; }
void glfwRequestWindowAttention(GLFWwindow* window) { (void)window; }

GLFWmonitor* glfwGetWindowMonitor(GLFWwindow* window) {
    (void)window;
    return NULL;
}

void glfwSetWindowMonitor(GLFWwindow* window, GLFWmonitor* monitor,
                          int xpos, int ypos, int width, int height, int refreshRate) {
    (void)window; (void)monitor; (void)xpos; (void)ypos;
    (void)width; (void)height; (void)refreshRate;
}

int glfwGetWindowAttrib(GLFWwindow* window, int attrib) {
    (void)window; (void)attrib;
    return 0;
}

void glfwSetWindowAttrib(GLFWwindow* window, int attrib, int value) {
    (void)window; (void)attrib; (void)value;
}

void glfwSetWindowUserPointer(GLFWwindow* window, void* pointer) {
    (void)window; (void)pointer;
}

void* glfwGetWindowUserPointer(GLFWwindow* window) {
    (void)window;
    return NULL;
}

/* 윈도우 콜백 (모두 no-op, NULL 반환) */
typedef void* GLFWwindowposfun;
typedef void* GLFWwindowsizefun;
typedef void* GLFWwindowclosefun;
typedef void* GLFWwindowrefreshfun;
typedef void* GLFWwindowfocusfun;
typedef void* GLFWwindowiconifyfun;
typedef void* GLFWwindowmaximizefun;
typedef void* GLFWframebuffersizefun;
typedef void* GLFWwindowcontentscalefun;

GLFWwindowposfun          glfwSetWindowPosCallback(GLFWwindow* w, GLFWwindowposfun cb)          { (void)w;(void)cb; return NULL; }
GLFWwindowsizefun         glfwSetWindowSizeCallback(GLFWwindow* w, GLFWwindowsizefun cb)         { (void)w;(void)cb; return NULL; }
GLFWwindowclosefun        glfwSetWindowCloseCallback(GLFWwindow* w, GLFWwindowclosefun cb)        { (void)w;(void)cb; return NULL; }
GLFWwindowrefreshfun      glfwSetWindowRefreshCallback(GLFWwindow* w, GLFWwindowrefreshfun cb)    { (void)w;(void)cb; return NULL; }
GLFWwindowfocusfun        glfwSetWindowFocusCallback(GLFWwindow* w, GLFWwindowfocusfun cb)        { (void)w;(void)cb; return NULL; }
GLFWwindowiconifyfun      glfwSetWindowIconifyCallback(GLFWwindow* w, GLFWwindowiconifyfun cb)    { (void)w;(void)cb; return NULL; }
GLFWwindowmaximizefun     glfwSetWindowMaximizeCallback(GLFWwindow* w, GLFWwindowmaximizefun cb)  { (void)w;(void)cb; return NULL; }
GLFWframebuffersizefun    glfwSetFramebufferSizeCallback(GLFWwindow* w, GLFWframebuffersizefun cb){ (void)w;(void)cb; return NULL; }
GLFWwindowcontentscalefun glfwSetWindowContentScaleCallback(GLFWwindow* w, GLFWwindowcontentscalefun cb) { (void)w;(void)cb; return NULL; }

/* ── 이벤트 처리 ────────────────────────────────────────────── */

void glfwPollEvents(void)                      { }
void glfwWaitEvents(void)                      { }
void glfwWaitEventsTimeout(double timeout)     { (void)timeout; }
void glfwPostEmptyEvent(void)                  { }

/* ── 입력 ───────────────────────────────────────────────────── */

int glfwGetInputMode(GLFWwindow* window, int mode) {
    (void)window; (void)mode;
    return 0;
}

void glfwSetInputMode(GLFWwindow* window, int mode, int value) {
    (void)window; (void)mode; (void)value;
}

int glfwRawMouseMotionSupported(void) { return 0; }

const char* glfwGetKeyName(int key, int scancode) {
    (void)key; (void)scancode;
    return NULL;
}

int glfwGetKeyScancode(int key) {
    (void)key;
    return -1;
}

int glfwGetKey(GLFWwindow* window, int key) {
    (void)window; (void)key;
    return 0; /* GLFW_RELEASE */
}

int glfwGetMouseButton(GLFWwindow* window, int button) {
    (void)window; (void)button;
    return 0;
}

void glfwGetCursorPos(GLFWwindow* window, double* xpos, double* ypos) {
    (void)window;
    if (xpos) *xpos = 0.0;
    if (ypos) *ypos = 0.0;
}

void glfwSetCursorPos(GLFWwindow* window, double xpos, double ypos) {
    (void)window; (void)xpos; (void)ypos;
}

/* ── 커서 ───────────────────────────────────────────────────── */

GLFWcursor* glfwCreateCursor(const GLFWimage* image, int xhot, int yhot) {
    (void)image; (void)xhot; (void)yhot;
    return NULL;
}

GLFWcursor* glfwCreateStandardCursor(int shape) {
    (void)shape;
    return NULL;
}

void glfwDestroyCursor(GLFWcursor* cursor) { (void)cursor; }

void glfwSetCursor(GLFWwindow* window, GLFWcursor* cursor) {
    (void)window; (void)cursor;
}

/* ── IME / Preedit (GLFW 3.4 확장) ─────────────────────────── */

void glfwGetPreeditCursorRectangle(GLFWwindow* window, int* x, int* y, int* w, int* h) {
    (void)window;
    if (x) *x = 0; if (y) *y = 0; if (w) *w = 0; if (h) *h = 0;
}

void glfwSetPreeditCursorRectangle(GLFWwindow* window, int x, int y, int w, int h) {
    (void)window; (void)x; (void)y; (void)w; (void)h;
}

void glfwResetPreeditText(GLFWwindow* window) { (void)window; }

const uint32_t* glfwGetPreeditCandidate(GLFWwindow* window, int list_index, int* text_count) {
    (void)window; (void)list_index;
    if (text_count) *text_count = 0;
    return NULL;
}

/* 콜백 (no-op) */
typedef void* GLFWkeyfun;
typedef void* GLFWcharfun;
typedef void* GLFWcharmodsfun;
typedef void* GLFWpreeditfun;
typedef void* GLFWimestatusfun;
typedef void* GLFWpreeditcandidatefun;
typedef void* GLFWmousebuttonfun;
typedef void* GLFWcursorposfun;
typedef void* GLFWcursorenterfun;
typedef void* GLFWscrollfun;
typedef void* GLFWdropfun;

GLFWkeyfun                  glfwSetKeyCallback(GLFWwindow* w, GLFWkeyfun cb)                         { (void)w;(void)cb; return NULL; }
GLFWcharfun                 glfwSetCharCallback(GLFWwindow* w, GLFWcharfun cb)                        { (void)w;(void)cb; return NULL; }
GLFWcharmodsfun             glfwSetCharModsCallback(GLFWwindow* w, GLFWcharmodsfun cb)                { (void)w;(void)cb; return NULL; }
GLFWpreeditfun              glfwSetPreeditCallback(GLFWwindow* w, GLFWpreeditfun cb)                  { (void)w;(void)cb; return NULL; }
GLFWimestatusfun            glfwSetIMEStatusCallback(GLFWwindow* w, GLFWimestatusfun cb)              { (void)w;(void)cb; return NULL; }
GLFWpreeditcandidatefun     glfwSetPreeditCandidateCallback(GLFWwindow* w, GLFWpreeditcandidatefun cb){ (void)w;(void)cb; return NULL; }
GLFWmousebuttonfun          glfwSetMouseButtonCallback(GLFWwindow* w, GLFWmousebuttonfun cb)          { (void)w;(void)cb; return NULL; }
GLFWcursorposfun            glfwSetCursorPosCallback(GLFWwindow* w, GLFWcursorposfun cb)              { (void)w;(void)cb; return NULL; }
GLFWcursorenterfun          glfwSetCursorEnterCallback(GLFWwindow* w, GLFWcursorenterfun cb)          { (void)w;(void)cb; return NULL; }
GLFWscrollfun               glfwSetScrollCallback(GLFWwindow* w, GLFWscrollfun cb)                   { (void)w;(void)cb; return NULL; }
GLFWdropfun                 glfwSetDropCallback(GLFWwindow* w, GLFWdropfun cb)                       { (void)w;(void)cb; return NULL; }

/* ── 조이스틱 / 게임패드 ────────────────────────────────────── */

int glfwJoystickPresent(int jid) { (void)jid; return 0; }

const float* glfwGetJoystickAxes(int jid, int* count) {
    (void)jid;
    if (count) *count = 0;
    return NULL;
}

const unsigned char* glfwGetJoystickButtons(int jid, int* count) {
    (void)jid;
    if (count) *count = 0;
    return NULL;
}

const unsigned char* glfwGetJoystickHats(int jid, int* count) {
    (void)jid;
    if (count) *count = 0;
    return NULL;
}

const char* glfwGetJoystickName(int jid)  { (void)jid; return NULL; }
const char* glfwGetJoystickGUID(int jid)  { (void)jid; return NULL; }

void glfwSetJoystickUserPointer(int jid, void* pointer) {
    (void)jid; (void)pointer;
}

void* glfwGetJoystickUserPointer(int jid) { (void)jid; return NULL; }

int glfwJoystickIsGamepad(int jid) { (void)jid; return 0; }

GLFWjoystickfun glfwSetJoystickCallback(GLFWjoystickfun cbfun) {
    (void)cbfun;
    return NULL;
}

int glfwUpdateGamepadMappings(const char* string) {
    (void)string;
    return 0;
}

const char* glfwGetGamepadName(int jid) { (void)jid; return NULL; }

int glfwGetGamepadState(int jid, GLFWgamepadstate* state) {
    (void)jid;
    if (state) memset(state, 0, sizeof(*state));
    return 0;
}

/* ── 클립보드 ───────────────────────────────────────────────── */

void glfwSetClipboardString(GLFWwindow* window, const char* string) {
    (void)window; (void)string;
}

const char* glfwGetClipboardString(GLFWwindow* window) {
    (void)window;
    return NULL;
}

/* ── 타이머 ─────────────────────────────────────────────────── */

double glfwGetTime(void)             { return 0.0; }
void   glfwSetTime(double time)      { (void)time; }
uint64_t glfwGetTimerValue(void)     { return 0; }
uint64_t glfwGetTimerFrequency(void) { return 1000000000ULL; }

/* ── 컨텍스트 ───────────────────────────────────────────────── */

void glfwMakeContextCurrent(GLFWwindow* window) { (void)window; }

GLFWwindow* glfwGetCurrentContext(void) { return NULL; }

void glfwSwapBuffers(GLFWwindow* window) { (void)window; }

void glfwSwapInterval(int interval) { (void)interval; }

int glfwExtensionSupported(const char* extension) {
    (void)extension;
    return 0;
}

GLFWglproc glfwGetProcAddress(const char* procname) {
    (void)procname;
    return NULL;
}
