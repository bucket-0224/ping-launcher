package kr.co.donghyun.pinglauncher.presentation

import android.content.Context
import android.content.Intent
import android.hardware.input.InputManager.InputDeviceListener
import android.util.Log
import android.view.InputDevice
import android.view.KeyEvent
import android.view.Surface
import android.view.View
import androidx.activity.OnBackPressedCallback
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.core.view.ViewCompat.setOnApplyWindowInsetsListener
import kr.co.donghyun.pinglauncher.data.auth.MicrosoftAuthManager
import kr.co.donghyun.pinglauncher.data.instance.InstanceManager
import kr.co.donghyun.pinglauncher.data.jvm.JvmSettings
import kr.co.donghyun.pinglauncher.data.jvm.JvmSettingsManager
import kr.co.donghyun.pinglauncher.data.jvm.isLegacyVersion
import kr.co.donghyun.pinglauncher.data.renderer.Renderer
import kr.co.donghyun.pinglauncher.data.renderer.RendererManager
import kr.co.donghyun.pinglauncher.presentation.base.BaseActivity
import kr.co.donghyun.pinglauncher.presentation.ui.components.GameControllerView
import kr.co.donghyun.pinglauncher.presentation.ui.components.MinecraftSurface
import kr.co.donghyun.pinglauncher.presentation.ui.theme.PingLauncherTheme
import kr.co.donghyun.pinglauncher.presentation.util.dns.DnsHookNative
import kr.co.donghyun.pinglauncher.presentation.util.jni.JavaNativeLauncher
import kr.co.donghyun.pinglauncher.presentation.util.minecraft.MinecraftJREPreparer
import kr.co.donghyun.pinglauncher.presentation.util.renderer.VirGLLauncher
import org.lwjgl.glfw.GLFW.GLFW_KEY_A
import org.lwjgl.glfw.GLFW.GLFW_KEY_D
import org.lwjgl.glfw.GLFW.GLFW_KEY_E
import org.lwjgl.glfw.GLFW.GLFW_KEY_ENTER
import org.lwjgl.glfw.GLFW.GLFW_KEY_ESCAPE
import org.lwjgl.glfw.GLFW.GLFW_KEY_LEFT_CONTROL
import org.lwjgl.glfw.GLFW.GLFW_KEY_LEFT_SHIFT
import org.lwjgl.glfw.GLFW.GLFW_KEY_S
import org.lwjgl.glfw.GLFW.GLFW_KEY_SPACE
import org.lwjgl.glfw.GLFW.GLFW_KEY_TAB
import org.lwjgl.glfw.GLFW.GLFW_KEY_W
import org.lwjgl.glfw.GLFW.GLFW_PRESS
import org.lwjgl.glfw.GLFW.GLFW_RELEASE
import org.objectweb.asm.ClassReader
import org.objectweb.asm.ClassVisitor
import org.objectweb.asm.ClassWriter
import org.objectweb.asm.Opcodes
import java.io.File
import java.io.IOException
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream


class MinecraftActivity : BaseActivity() {

    private external fun nativeIsGrabbing(): Boolean
    private external fun nativeSetupBridgeWindow(surface: Surface)
    private external fun nativeTrySetupShowingWindow(): Boolean
    private external fun nativeDumpInputState()


    // Intent로 전달받은 버전 정보
    private lateinit var versionId: String
    private lateinit var assetIndex: String
    private lateinit var extraJars: List<String>
    private lateinit var mainClass: String
    internal var instanceDir: String? = null
    private var customGameDir: String? = null
    private var currentSurface: Surface? = null
    @Volatile var combatMode: Boolean = false

    private var forceShowController: Boolean? = null

    private val PROCESSOR_ONLY_JAR_PREFIXES = listOf(
        "ForgeAutoRenamingTool",
        "BinaryPatcher", "binarypatcher",
        "jarsplitter",
        "installertools",
        "vignette",
        "DiffPatch", "diffpatch",
        "mergetool"   // ※ 부팅에 필요한 mergetool-*-api.jar 는 보존 필요 — 아래 헬퍼에서 별도 처리
    )

    private var gameControllerView: GameControllerView? = null
    private var inputDeviceListener: InputDeviceListener? = null

    internal val isGrabbing: Boolean
        get() = try { nativeIsGrabbing() } catch (_: Exception) { false }

    private var jvmStarted = false
    private var javaMajor: Int = 21


    companion object {
        private const val EXTRA_VERSION_ID = "version_id"
        private const val EXTRA_ASSET_INDEX = "asset_index"
        private const val EXTRA_EXTRA_JARS = "extra_jars"
        private const val EXTRA_MAIN_CLASS = "main_class"
        private const val EXTRA_GAME_DIR = "game_dir"
        private const val EXTRA_INSTANCE_DIR = "instance_dir"

        @JvmStatic
        var currentInstance: MinecraftActivity? = null

        fun start(
            context: Context,
            versionId: String,
            assetIndex: String,
            extraJars: List<String> = emptyList(),
            mainClass: String = "net.minecraft.client.main.Main",
            customGameDir: String? = null,
            instanceDir: String? = null
        ) {
            Log.d("PING_LAUNCHER", "MC 시작: mainClass=$mainClass, extraJars=${extraJars.size}개")
            Log.d("PING_LAUNCHER", "instanceDir 전달: $instanceDir")  // ← 추가
            Log.d("PING_LAUNCHER", "customGameDir 전달: $customGameDir")  // ← 추가

            context.startActivity(
                Intent(context, MinecraftActivity::class.java).apply {
                    instanceDir?.let { putExtra(EXTRA_INSTANCE_DIR, it) }
                    putExtra(EXTRA_VERSION_ID, versionId)
                    putExtra(EXTRA_ASSET_INDEX, assetIndex)
                    putStringArrayListExtra(EXTRA_EXTRA_JARS, ArrayList(extraJars))
                    putExtra(EXTRA_MAIN_CLASS, mainClass)
                    customGameDir?.let { putExtra(EXTRA_GAME_DIR, it) }
                }
            )
        }
    }


    override fun onCreated() {
        hideNavigation()
        currentInstance = this

        versionId = intent.getStringExtra(EXTRA_VERSION_ID) ?: "1.16.2"
        assetIndex = intent.getStringExtra(EXTRA_ASSET_INDEX) ?: "1.16"
        extraJars = intent.getStringArrayListExtra(EXTRA_EXTRA_JARS) ?: emptyList()
        mainClass = intent.getStringExtra(EXTRA_MAIN_CLASS) ?: "net.minecraft.client.main.Main"
        instanceDir = intent.getStringExtra(EXTRA_INSTANCE_DIR)
        Log.d("PING_LAUNCHER", "instanceDir 수신: $instanceDir")  // ← 추가
        customGameDir = intent.getStringExtra(EXTRA_GAME_DIR)
        Log.d("PING_LAUNCHER", "customGameDir 수신: $customGameDir")  // ← 추가

        setOnApplyWindowInsetsListener(window.decorView) { _, insets ->
            val ime = insets.isVisible(androidx.core.view.WindowInsetsCompat.Type.ime())
            gameControllerView?.setImeVisibleExternal(ime)
            insets
        }

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() { return }
        })

        setContent {
            PingLauncherTheme {
                Box(modifier = Modifier.fillMaxSize()) {
                    MinecraftSurface(
                        onSurfaceCreated = { surface, _ ->
                            currentSurface = surface
                            if (!jvmStarted) {
                                jvmStarted = true
                                setupAndLaunch(surface)
                            } else {
                                try {
                                    System.loadLibrary("pingjvm")
                                    nativeSetupBridgeWindow(surface)
                                    Log.d("PING_LAUNCHER", "✅ Surface 재바인딩 완료 (resume 후)")
                                } catch (e: Exception) {
                                    Log.e("PING_LAUNCHER", "Surface 재바인딩 실패: ${e.message}", e)
                                }
                            }
                        },
                        onSurfaceChanged = { w, h -> sendScreenSize(w, h) },
                        onSurfaceDestroyed = {
                            Log.d("PING_LAUNCHER", "Surface destroyed — JVM 유지")
                            currentSurface = null
                        },
                    )
                    // GameControllerOverlay() ← 제거
                }
            }
        }


        gameControllerView = GameControllerView(this).also { view ->
            addContentView(
                view,
                android.view.ViewGroup.LayoutParams(
                    android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                    android.view.ViewGroup.LayoutParams.MATCH_PARENT
                )
            )
        }

        // 초기 상태 반영 + 디바이스 변화 감지
        setupInputDeviceWatching()
        installPhysicalKeyboardInterceptor()
    }

    private fun setupInputDeviceWatching() {
        val im = getSystemService(INPUT_SERVICE)
                as android.hardware.input.InputManager

        // 초기 상태 반영
        updateGameControllerVisibility()

        // 디바이스 add/remove/change 감지
        inputDeviceListener = object : InputDeviceListener {
            override fun onInputDeviceAdded(deviceId: Int) {
                Log.d("PING_LAUNCHER", "🎮 입력 디바이스 추가: id=$deviceId")
                updateGameControllerVisibility()
            }
            override fun onInputDeviceRemoved(deviceId: Int) {
                Log.d("PING_LAUNCHER", "🎮 입력 디바이스 제거: id=$deviceId")
                updateGameControllerVisibility()
            }
            override fun onInputDeviceChanged(deviceId: Int) {
                updateGameControllerVisibility()
            }
        }
        im.registerInputDeviceListener(inputDeviceListener, null)
    }

    /** 현재 물리 키보드 또는 마우스가 연결되어 있는지 */
    private fun hasHardwareKeyboardOrMouse(): Boolean {
        val im = getSystemService(INPUT_SERVICE)
                as android.hardware.input.InputManager

        for (id in im.inputDeviceIds) {
            val dev = im.getInputDevice(id) ?: continue
            if (dev.isVirtual) continue

            val src = dev.sources

            // 알파벳 키보드 (소프트 IME 제외)
            if ((src and InputDevice.SOURCE_KEYBOARD) != 0
                && dev.keyboardType == InputDevice.KEYBOARD_TYPE_ALPHABETIC) {
                return true
            }

            // 마우스 / 터치패드
            if ((src and InputDevice.SOURCE_MOUSE) == InputDevice.SOURCE_MOUSE
                || (src and InputDevice.SOURCE_TOUCHPAD) == InputDevice.SOURCE_TOUCHPAD) {
                return true
            }
        }
        return false
    }


    /**
     * 물리 키보드/마우스가 연결되어 있으면 GameControllerView 를 숨긴다.
     *
     * 판정 기준:
     *  - 키보드: SOURCE_KEYBOARD 비트 + KEYBOARD_TYPE_ALPHABETIC (소프트 IME 제외)
     *  - 마우스: SOURCE_MOUSE 비트 (또는 SOURCE_MOUSE_RELATIVE)
     *  - 둘 중 하나라도 있으면 숨김
     */
    private fun updateGameControllerVisibility() {
        val shouldHide = hasHardwareKeyboardOrMouse()

        Log.e("PING_LAUNCHER",   // ← Debug → Error 로 변경 (디버그용 일시 변경)
            "🎮 GameController visibility: → ${if (shouldHide) "HIDE" else "SHOW"}")

        runOnUiThread {
            gameControllerView?.visibility =
                if (shouldHide) View.GONE else View.VISIBLE
        }
    }

    private fun setupAndLaunch(surface: Surface) {
        val nativesDir = File(applicationContext.filesDir, "natives")

        // ★ mcVersion 기반으로 Java major 결정
        javaMajor = MinecraftJREPreparer.pickJavaMajor(versionId)
        Log.d("PING_LAUNCHER", "선택된 Java major: $javaMajor (mc=$versionId)")

        // pingjvm 은 반드시 떠야 하므로 별도 처리
        try {
            System.loadLibrary("pingjvm")
        } catch (e: UnsatisfiedLinkError) {
            Log.e("PING_LAUNCHER", "❌ libpingjvm.so 로드 실패 — 진행 불가: ${e.message}", e)
            return
        }

        // ── 렌더러별 .so 먼저 로드 (preloadAwtStubs 이전에) ───────────────
        // preloadAwtStubs 같은 JNI 바인딩 함수에서 실패가 나더라도,
        // 핵심 .so 들은 이미 메모리에 올라와 있어야 JVM 부팅이 가능하다.
        val renderer = RendererManager.load(this)
        when (renderer.id) {
            "mobileglues" -> {
                // info_getter 가 먼저! libmobileglues.so 의 의존성임
                loadSoSafely(File(nativesDir, "libmobileglues_info_getter.so"), required = false)
                if (loadSoSafely(File(nativesDir, "libmobileglues.so"), required = false)) {
                    Log.d("PING_LAUNCHER", "✅ 렌더러: MobileGlues")
                } else {
                    Log.w("PING_LAUNCHER", "⚠️ libmobileglues.so 없음 — RendererManager 선택만 됐고 .so 미배포")
                }
            }
            "zink" -> {
                try { System.loadLibrary("vulkan") } catch (_: Throwable) {}
                if (loadSoSafely(File(nativesDir, "libOSMesa.so"), required = true)) {
                    Log.d("PING_LAUNCHER", "✅ 렌더러: Zink")
                }
            }
            "ltw" -> {
                try {
                    System.loadLibrary("GLESv2")
                    Log.d("PING_LAUNCHER", "✅ libGLESv2.so preloaded for LTW")
                } catch (e: Throwable) {
                    Log.w("PING_LAUNCHER", "⚠️ libGLESv2.so preload 실패: ${e.message}")
                }
                Log.d("PING_LAUNCHER", "✅ 렌더러: LTW (LWJGL 측에서 자체 로드)")
            }
            else -> {
                // gl4es / gl4es_desktop / holy_gl4es
                if (loadSoSafely(File(nativesDir, "libgl4es_114.so"), required = true)) {
                    Log.d("PING_LAUNCHER", "✅ 렌더러: GL4ES")
                }
            }
        }

        // 공통 .so — 하나가 실패해도 다음 것은 계속 시도
        loadSoSafely(File(nativesDir, "libopenal.so"), required = false)
        loadSoSafely(File(nativesDir, "libpojavexec.so"), required = true)
        loadSoSafely(File(nativesDir, "liblwjgl.so"), required = false)
        loadSoSafely(File(nativesDir, "liblwjgl_opengl.so"), required = false)

        DnsHookNative.setup(this)

        // ── AWT stub preload (실패해도 무시 — JNI 바인딩 불일치여도 핵심 .so 는 이미 떠 있음) ──
        try {
            JavaNativeLauncher.preloadAwtStubs(applicationInfo.nativeLibraryDir)
        } catch (e: UnsatisfiedLinkError) {
            Log.w("PING_LAUNCHER", "⚠️ preloadAwtStubs 바인딩 실패 (무시 가능): ${e.message}")
        } catch (e: Throwable) {
            Log.w("PING_LAUNCHER", "⚠️ preloadAwtStubs 예외 (무시 가능): ${e.message}")
        }

        try {
            nativeSetupBridgeWindow(surface)
            Log.d("PING_LAUNCHER", "✅ setupBridgeWindow 완료")
        } catch (e: Exception) {
            Log.e("PING_LAUNCHER", "setupBridgeWindow 실패: ${e.message}", e)
        }

        startCrashWatcher()
        startMinecraft()
    }

    /**
     * .so 한 개를 안전하게 로드. 이미 로드되어 있거나 파일이 없으면 false 반환.
     * required=true 인데 실패하면 ERROR 로그, 아니면 WARN 로그만 남기고 계속 진행.
     */
    private fun loadSoSafely(soFile: File, required: Boolean): Boolean {
        if (!soFile.exists()) {
            if (required) Log.e("PING_LAUNCHER", "❌ 필수 .so 파일 없음: ${soFile.name}")
            else Log.w("PING_LAUNCHER", "⚠️ .so 파일 없음 (스킵): ${soFile.name}")
            return false
        }
        return try {
            System.load(soFile.absolutePath)
            Log.d("PING_LAUNCHER", "📦 .so 로드: ${soFile.name}")
            true
        } catch (e: UnsatisfiedLinkError) {
            // 이미 로드된 경우도 여기로 옴 — 무해
            val msg = e.message ?: ""
            if (msg.contains("already loaded", ignoreCase = true)) {
                Log.d("PING_LAUNCHER", "ℹ️ 이미 로드됨: ${soFile.name}")
                true
            } else {
                if (required) Log.e("PING_LAUNCHER", "❌ ${soFile.name} 로드 실패: $msg", e)
                else Log.w("PING_LAUNCHER", "⚠️ ${soFile.name} 로드 실패 (무시): $msg")
                false
            }
        }
    }

    private fun resolveForgePlaceholders(
        raw: String,
        librariesDir: File,
        nativesDir: File,
        versionId: String
    ): String = raw
        .replace("\${library_directory}",   librariesDir.absolutePath)
        .replace("\${libraries_directory}", librariesDir.absolutePath)   // 표기 둘 다 본 적 있음
        .replace("\${classpath_separator}", File.pathSeparator)
        .replace("\${version_name}",        versionId)
        .replace("\${natives_directory}",   nativesDir.absolutePath)


    private fun startCrashWatcher() {
        val instanceBase = instanceDir?.let { File(it) }
            ?: customGameDir?.let { File(it) }  // instanceDir 없을 때만 fallback
            ?: File(getExternalFilesDir(null), "instances/vanilla_$versionId")

        Thread {
            val crashDir = File(instanceBase, "crash-reports")
            val existingFiles = crashDir.listFiles()?.map { it.name }?.toSet() ?: emptySet()

            // JVM이 실행되는 동안 새 크래시 파일 감시
            while (!isFinishing) {
                Thread.sleep(1000)
                val newCrash = crashDir.listFiles()
                    ?.any { it.extension == "txt" && !existingFiles.contains(it.name) } == true
                if (newCrash) {
                    Log.d("PING_LAUNCHER", "새 크래시 감지!")
                    val intent = Intent(this, CrashReportActivity::class.java).apply {
                        putExtra("instance_dir", instanceBase.absolutePath)
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
                    }
                    startActivity(intent)
                    break
                }
            }
        }.start()
    }

    private fun sendScreenSize(width: Int, height: Int) {
        try {
            Class.forName("org.lwjgl.glfw.CallbackBridge")
                .getMethod("nativeSendScreenSize", Int::class.java, Int::class.java)
                .invoke(null, width, height)
        } catch (_: Exception) {}
    }

    internal var currentCursorX = 1280f  // 화면 중앙 근처
    internal var currentCursorY = 720f
    internal val MOUSE_SENSITIVITY = 1.5f


    /**
     * PojavLauncher patched lwjgl-glfw-classes.jar 에 누락된 GLFW 3.4 API 를
     * ASM 으로 노옵 스텁 주입.
     *
     * 1.21.5+ (특히 26w14a) 가 부팅 단계에서 호출하는 API:
     *   - glfwPlatformSupported(int)Z   ← 26w14a 가 NoSuchMethodError 로 죽는 지점
     *   - glfwGetPlatform()I
     *   - glfwFocusWindow / glfwHideWindow / glfwMaximizeWindow / glfwRestoreWindow (J)V
     *
     * 실제 GLFW 백엔드는 어차피 libglfw.so/libpojavexec.so 가 자체 구현이라,
     * 노옵 스텁이 있어도 MC 가 부팅 단계는 통과한다. HiDPI / IME 같은 부가 기능은
     * 동작 안 할 수 있지만 게임 자체는 켜짐.
     */
    /**
     * lwjgl-glfw-classes.jar 안에 GLFW 3.4 신규 API 스텁이 들어있는지
     * 실제 클래스 바이트를 검사해서 보장한다. 마커 파일에 의존하지 않음 —
     * 런처 업데이트로 필요한 스텁 목록이 늘어나도 자동 재패치되도록.
     */
    private fun patchLwjglGlfwIfNeeded(lwjgl3Dir: File) {
        if (!lwjgl3Dir.exists()) return
        val candidates = lwjgl3Dir.listFiles()
            ?.filter { it.name.startsWith("lwjgl-glfw-classes") && it.extension == "jar" }
            ?: return

        // 26.1.x 부팅에 필요한 GLFW 3.4 API 들
        val required = setOf(
            "glfwPlatformSupported(I)Z",
            "glfwGetPlatform()I",
            "glfwFocusWindow(J)V",
            "glfwHideWindow(J)V",
            "glfwMaximizeWindow(J)V",
            "glfwRestoreWindow(J)V",
            "glfwRequestWindowAttention(J)V",
            // ── 3.3.6 신규 (ZL2 가 추가로 채워둔 것) ──
            "glfwInitAllocator(J)V",
            "glfwSetWindowContentScaleCallback(JLorg/lwjgl/glfw/GLFWWindowContentScaleCallbackI;)Lorg/lwjgl/glfw/GLFWWindowContentScaleCallback;",
            // ── IME 관련 (3.4 preview, 3.3.6 에도 일부 포함) ──
            "glfwSetPreeditCursorRectangle(JIIII)V",
            "glfwInitAllocator(J)V",
            "glfwGetWindowContentScale(J[F[F)V",
            "glfwGetWindowContentScale(JLjava/nio/FloatBuffer;Ljava/nio/FloatBuffer;)V",
            "glfwGetPreeditCursorRectangle(JLjava/nio/IntBuffer;Ljava/nio/IntBuffer;Ljava/nio/IntBuffer;Ljava/nio/IntBuffer;)V",
            "glfwGetMonitorName(J)Ljava/lang/String;",
            )

        for (jar in candidates) {
            val missing = findMissingMethods(jar, required)
            if (missing.isEmpty()) {
                Log.d("PING_LAUNCHER", "✅ GLFW 3.4 stubs 이미 있음: ${jar.name}")
                // 옛 마커 파일 청소 (있을 수도 없을 수도)
                File(jar.parent, "${jar.name}.patched_glfw34").delete()
                continue
            }
            Log.w("PING_LAUNCHER", "🩹 GLFW 패치 필요: ${jar.name} — 누락 메서드 $missing")
            try {
                patchGlfwJar(jar)
                Log.d("PING_LAUNCHER", "✅ 패치 완료: ${jar.name}")
                // 검증
                val stillMissing = findMissingMethods(jar, required)
                if (stillMissing.isNotEmpty()) {
                    Log.e("PING_LAUNCHER", "❌ 패치 후에도 여전히 누락: $stillMissing — patcher 버그 의심")
                }
            } catch (e: Exception) {
                Log.e("PING_LAUNCHER", "❌ GLFW 패치 실패: ${e.message}", e)
            }
        }
    }

    /**
     * jar 안의 org/lwjgl/glfw/GLFW.class 를 열어서 [required] 중 빠진 메서드 시그니처 목록 반환.
     * jar 가 깨졌거나 GLFW.class 가 없으면 required 전체를 반환 (= 무조건 패치 시도).
     */
    private fun findMissingMethods(jar: File, required: Set<String>): Set<String> {
        return try {
            ZipFile(jar).use { zip ->
                val entry = zip.getEntry("org/lwjgl/glfw/GLFW.class")
                    ?: return required
                val bytes = zip.getInputStream(entry).readBytes()
                val found = HashSet<String>()
                ClassReader(bytes).accept(object : ClassVisitor(Opcodes.ASM9) {
                    override fun visitMethod(
                        access: Int, name: String, descriptor: String,
                        signature: String?, exceptions: Array<out String>?
                    ): org.objectweb.asm.MethodVisitor? {
                        found.add("$name$descriptor")
                        return null
                    }
                }, ClassReader.SKIP_CODE)
                required - found
            }
        } catch (e: Exception) {
            Log.w("PING_LAUNCHER", "jar 메서드 스캔 실패 (${jar.name}): ${e.message}")
            required
        }
    }

    private fun patchGlfwJar(jar: File) {
        val tmp = File(jar.parent, jar.name + ".tmp")
        ZipFile(jar).use { zin ->
            ZipOutputStream(tmp.outputStream()).use { zout ->
                val entries = zin.entries()
                while (entries.hasMoreElements()) {
                    val entry = entries.nextElement()
                    val bytes = zin.getInputStream(entry).readBytes()
                    val finalBytes = when (entry.name) {
                        "org/lwjgl/glfw/GLFW.class" -> patchGlfwClass(bytes)
                        "org/lwjgl/system/MemoryUtil.class" -> patchMemoryUtilClass(bytes)
                        else -> bytes
                    }

                    // 새 ZipEntry 로 만들어야 CRC/size 자동 계산. DEFLATED 로 통일.
                    val newEntry = ZipEntry(entry.name).apply {
                        method = ZipEntry.DEFLATED
                    }
                    zout.putNextEntry(newEntry)
                    zout.write(finalBytes)
                    zout.closeEntry()
                }
            }
        }
        if (!jar.delete()) throw IOException("기존 jar 삭제 실패: ${jar.absolutePath}")
        if (!tmp.renameTo(jar)) throw IOException("임시 jar rename 실패")
    }

    private fun patchMemoryUtilClass(bytes: ByteArray): ByteArray {
        val ASM = Opcodes.ASM9

        val existing = HashSet<String>()
        ClassReader(bytes).accept(object : ClassVisitor(ASM) {
            override fun visitMethod(
                access: Int, name: String, descriptor: String,
                signature: String?, exceptions: Array<out String>?
            ): org.objectweb.asm.MethodVisitor? {
                existing.add("$name$descriptor")
                return null
            }
        }, ClassReader.SKIP_CODE)

        val reader = ClassReader(bytes)
        val writer = ClassWriter(reader, ClassWriter.COMPUTE_FRAMES)

        reader.accept(object : ClassVisitor(ASM, writer) {
            override fun visitEnd() {
                // memFree 다른 시그니처들도 같이 (ByteBuffer 외)
                addBufferOverload("memFree", "Ljava/nio/ByteBuffer;")
                addBufferOverload("memFree", "Ljava/nio/IntBuffer;")
                addBufferOverload("memFree", "Ljava/nio/FloatBuffer;")
                addBufferOverload("memFree", "Ljava/nio/DoubleBuffer;")
                addBufferOverload("memFree", "Ljava/nio/LongBuffer;")
                addBufferOverload("memFree", "Ljava/nio/ShortBuffer;")
                super.visitEnd()
            }

            private fun addBufferOverload(name: String, bufferDesc: String) {
                val targetDesc = "($bufferDesc)V"
                if ("$name$targetDesc" in existing) return

                val mv = cv.visitMethod(
                    Opcodes.ACC_PUBLIC or Opcodes.ACC_STATIC,
                    name, targetDesc, null, null
                )
                mv.visitCode()
                mv.visitVarInsn(Opcodes.ALOAD, 0)
                mv.visitMethodInsn(
                    Opcodes.INVOKESTATIC,
                    "org/lwjgl/system/MemoryUtil",
                    name, "(Ljava/nio/Buffer;)V", false
                )
                mv.visitInsn(Opcodes.RETURN)
                mv.visitMaxs(1, 1)
                mv.visitEnd()
                Log.d("PING_LAUNCHER", "  + $name$targetDesc → Buffer 위임")
            }
        }, 0)

        return writer.toByteArray()
    }

    private fun patchGlfwClass(bytes: ByteArray): ByteArray {
        val ASM = Opcodes.ASM9

        // 이미 같은 시그니처 메서드가 있으면 덮어쓰지 않도록 1차 스캔
        val existing = HashSet<String>()
        ClassReader(bytes).accept(object : ClassVisitor(ASM) {
            override fun visitMethod(
                access: Int, name: String, descriptor: String,
                signature: String?, exceptions: Array<out String>?
            ): org.objectweb.asm.MethodVisitor? {
                existing.add("$name$descriptor")
                return null
            }
        }, ClassReader.SKIP_CODE)

        val reader = ClassReader(bytes)
        val writer = ClassWriter(reader, ClassWriter.COMPUTE_FRAMES)

        val visitor = object : ClassVisitor(ASM, writer) {
            override fun visitEnd() {
                // GLFW_PLATFORM_X11 = 0x60004
                if ("glfwPlatformSupported(I)Z" !in existing) {
                    emitPlatformSupported(); Log.d("PING_LAUNCHER", "  + glfwPlatformSupported(I)Z")
                }
                if ("glfwGetPlatform()I" !in existing) {
                    emitGetPlatform(); Log.d("PING_LAUNCHER", "  + glfwGetPlatform()I")
                }
                listOf(
                    "glfwFocusWindow", "glfwHideWindow",
                    "glfwMaximizeWindow", "glfwRestoreWindow",
                    "glfwRequestWindowAttention"
                ).forEach { n ->
                    if ("$n(J)V" !in existing) {
                        emitNoopJV(n); Log.d("PING_LAUNCHER", "  + $n(J)V")
                    }
                }

                // ↓ 여기 추가
                if ("glfwGetMonitorName(J)Ljava/lang/String;" !in existing) {
                    emitGetMonitorName()
                    Log.d("PING_LAUNCHER", "  + glfwGetMonitorName(J)Ljava/lang/String;")
                }

                // IME callback setter 들 — long(window) + 콜백 → 이전 콜백 반환 (null 박음)
                listOf(
                    Triple("glfwSetPreeditCallback",
                        "Lorg/lwjgl/glfw/GLFWPreeditCallbackI;",
                        "Lorg/lwjgl/glfw/GLFWPreeditCallback;"),
                    Triple("glfwSetPreeditCandidateCallback",
                        "Lorg/lwjgl/glfw/GLFWPreeditCandidateCallbackI;",
                        "Lorg/lwjgl/glfw/GLFWPreeditCandidateCallback;"),
                    Triple("glfwSetIMEStatusCallback",
                        "Lorg/lwjgl/glfw/GLFWIMEStatusCallbackI;",
                        "Lorg/lwjgl/glfw/GLFWIMEStatusCallback;")
                ).forEach { (name, param, ret) ->
                    val desc = "(J$param)$ret"
                    if ("$name$desc" !in existing) {
                        emitNullCallbackSetter(name, desc)
                        Log.d("PING_LAUNCHER", "  + $name$desc")
                    }
                }

// IME 관련 GLFW.class 메서드들
                if ("glfwResetPreeditText(J)V" !in existing) {
                    emitNoopJV("glfwResetPreeditText")
                    Log.d("PING_LAUNCHER", "  + glfwResetPreeditText(J)V")
                }

                if ("glfwSetPreeditCursorRectangle(JIIII)V" !in existing) {
                    val mv = cv.visitMethod(
                        Opcodes.ACC_PUBLIC or Opcodes.ACC_STATIC,
                        "glfwSetPreeditCursorRectangle", "(JIIII)V", null, null
                    )
                    mv.visitCode()
                    mv.visitInsn(Opcodes.RETURN)
                    mv.visitMaxs(0, 6)   // long(2) + int(1)*4 = 6
                    mv.visitEnd()
                    Log.d("PING_LAUNCHER", "  + glfwSetPreeditCursorRectangle(JIIII)V")
                }
                super.visitEnd()
            }

            // 그리고 emit 헬퍼 추가 (emitNoopJV 옆에)
            private fun emitNullCallbackSetter(name: String, desc: String) {
                val mv = cv.visitMethod(
                    Opcodes.ACC_PUBLIC or Opcodes.ACC_STATIC,
                    name, desc, null, null
                )
                mv.visitCode()
                mv.visitInsn(Opcodes.ACONST_NULL)   // 이전 callback null
                mv.visitInsn(Opcodes.ARETURN)
                mv.visitMaxs(1, 3)   // long(2) + callback(1) = 3
                mv.visitEnd()
            }

            private fun emitGetMonitorName() {
                val mv = cv.visitMethod(
                    Opcodes.ACC_PUBLIC or Opcodes.ACC_STATIC,
                    "glfwGetMonitorName", "(J)Ljava/lang/String;", null, null
                )
                mv.visitCode()
                mv.visitLdcInsn("Android Display")
                mv.visitInsn(Opcodes.ARETURN)
                mv.visitMaxs(1, 2)   // stack=1 (String 한 개), locals=2 (long 인자가 2 slot)
                mv.visitEnd()
            }

            private fun emitPlatformSupported() {
                val mv = cv.visitMethod(
                    Opcodes.ACC_PUBLIC or Opcodes.ACC_STATIC,
                    "glfwPlatformSupported", "(I)Z", null, null
                )
                mv.visitCode()
                mv.visitVarInsn(Opcodes.ILOAD, 0)
                mv.visitLdcInsn(0x60004)
                val notEqual = org.objectweb.asm.Label()
                mv.visitJumpInsn(Opcodes.IF_ICMPNE, notEqual)
                mv.visitInsn(Opcodes.ICONST_1)
                mv.visitInsn(Opcodes.IRETURN)
                mv.visitLabel(notEqual)
                mv.visitInsn(Opcodes.ICONST_0)
                mv.visitInsn(Opcodes.IRETURN)
                mv.visitMaxs(2, 1)
                mv.visitEnd()
            }

            private fun emitGetPlatform() {
                val mv = cv.visitMethod(
                    Opcodes.ACC_PUBLIC or Opcodes.ACC_STATIC,
                    "glfwGetPlatform", "()I", null, null
                )
                mv.visitCode()
                mv.visitLdcInsn(0x60004)
                mv.visitInsn(Opcodes.IRETURN)
                mv.visitMaxs(1, 0)
                mv.visitEnd()
            }

            private fun emitNoopJV(name: String) {
                val mv = cv.visitMethod(
                    Opcodes.ACC_PUBLIC or Opcodes.ACC_STATIC,
                    name, "(J)V", null, null
                )
                mv.visitCode()
                mv.visitInsn(Opcodes.RETURN)
                mv.visitMaxs(0, 2)   // long = 2 slot
                mv.visitEnd()
            }
        }
        reader.accept(visitor, 0)
        return writer.toByteArray()
    }

    private fun patchLaunchwrapperIfNeeded(searchDirs: List<File>) {
        searchDirs.forEach { dir ->
            dir.walkTopDown()
                .filter { it.name.startsWith("launchwrapper") && it.extension == "jar" }
                .forEach { lwJar ->
                    // 이미 패치됐는지 확인 (패치 마커 파일)
                    val markerFile = File(lwJar.parent, "${lwJar.name}.patched")
                    if (markerFile.exists()) return@forEach

                    Log.d("PING_LAUNCHER", "launchwrapper 패치 중: ${lwJar.absolutePath}")
                    try {
                        patchLaunchJar(lwJar)
                        markerFile.createNewFile() // 패치 완료 마커
                        Log.d("PING_LAUNCHER", "✅ launchwrapper 패치 완료")
                    } catch (e: Exception) {
                        Log.e("PING_LAUNCHER", "launchwrapper 패치 실패: ${e.message}")
                    }
                }
        }
    }

    private fun patchLaunchJar(lwJar: File) {
        val zipIn = ZipFile(lwJar)
        val patchedJar = File(lwJar.parent, lwJar.name + ".tmp")
        val zipOut = ZipOutputStream(patchedJar.outputStream())

        zipIn.entries().asSequence().forEach { entry ->
            val bytes = zipIn.getInputStream(entry).readBytes()
            val patched = if (entry.name == "net/minecraft/launchwrapper/Launch.class") {
                patchLaunchClass(bytes)
            } else bytes
            zipOut.putNextEntry(ZipEntry(entry.name))
            zipOut.write(patched)
            zipOut.closeEntry()
        }

        zipIn.close()
        zipOut.close()
        lwJar.delete()
        patchedJar.renameTo(lwJar)
    }

    private fun patchLaunchClass(bytes: ByteArray): ByteArray {
        val reader = ClassReader(bytes)
        val writer = ClassWriter(reader, ClassWriter.COMPUTE_FRAMES)

        val visitor = object : ClassVisitor(Opcodes.ASM9, writer) {
            override fun visitMethod(
                access: Int, name: String, descriptor: String,
                signature: String?, exceptions: Array<out String>?
            ): org.objectweb.asm.MethodVisitor {
                val mv = super.visitMethod(access, name, descriptor, signature, exceptions)
                if (name == "<init>" && descriptor == "()V") {
                    return object : org.objectweb.asm.MethodVisitor(Opcodes.ASM9, mv) {
                        override fun visitTypeInsn(opcode: Int, type: String) {
                            if (opcode == Opcodes.CHECKCAST && type == "java/net/URLClassLoader") {
                                visitInsn(Opcodes.POP)
                                visitLdcInsn("java.class.path")
                                visitLdcInsn("")
                                visitMethodInsn(Opcodes.INVOKESTATIC, "java/lang/System", "getProperty", "(Ljava/lang/String;Ljava/lang/String;)Ljava/lang/String;", false)
                                visitLdcInsn(File.pathSeparator)
                                visitMethodInsn(Opcodes.INVOKEVIRTUAL, "java/lang/String", "split", "(Ljava/lang/String;)[Ljava/lang/String;", false)
                                visitMethodInsn(Opcodes.INVOKESTATIC, "net/minecraft/launchwrapper/Launch", "pingStringsToUrls", "([Ljava/lang/String;)[Ljava/net/URL;", false)
                                return
                            }
                            super.visitTypeInsn(opcode, type)
                        }
                        override fun visitMethodInsn(opcode: Int, owner: String, name: String, descriptor: String, isInterface: Boolean) {
                            if (owner == "java/net/URLClassLoader" && name == "getURLs") return
                            super.visitMethodInsn(opcode, owner, name, descriptor, isInterface)
                        }
                    }
                }
                return mv
            }

            override fun visitEnd() {
                // 헬퍼 메서드 추가
                val mv = cv.visitMethod(
                    Opcodes.ACC_PRIVATE or Opcodes.ACC_STATIC,
                    "pingStringsToUrls", "([Ljava/lang/String;)[Ljava/net/URL;", null, null
                )
                mv.visitCode()
                mv.visitVarInsn(Opcodes.ALOAD, 0)
                mv.visitInsn(Opcodes.ARRAYLENGTH)
                mv.visitTypeInsn(Opcodes.ANEWARRAY, "java/net/URL")
                mv.visitVarInsn(Opcodes.ASTORE, 1)
                mv.visitInsn(Opcodes.ICONST_0)
                mv.visitVarInsn(Opcodes.ISTORE, 2)
                val loopStart = org.objectweb.asm.Label()
                val loopEnd = org.objectweb.asm.Label()
                mv.visitLabel(loopStart)
                mv.visitVarInsn(Opcodes.ILOAD, 2)
                mv.visitVarInsn(Opcodes.ALOAD, 0)
                mv.visitInsn(Opcodes.ARRAYLENGTH)
                mv.visitJumpInsn(Opcodes.IF_ICMPGE, loopEnd)
                val tryStart = org.objectweb.asm.Label()
                val tryEnd = org.objectweb.asm.Label()
                val catchBlock = org.objectweb.asm.Label()
                mv.visitTryCatchBlock(tryStart, tryEnd, catchBlock, "java/lang/Exception")
                mv.visitLabel(tryStart)
                mv.visitVarInsn(Opcodes.ALOAD, 1)
                mv.visitVarInsn(Opcodes.ILOAD, 2)
                mv.visitTypeInsn(Opcodes.NEW, "java/io/File")
                mv.visitInsn(Opcodes.DUP)
                mv.visitVarInsn(Opcodes.ALOAD, 0)
                mv.visitVarInsn(Opcodes.ILOAD, 2)
                mv.visitInsn(Opcodes.AALOAD)
                mv.visitMethodInsn(Opcodes.INVOKESPECIAL, "java/io/File", "<init>", "(Ljava/lang/String;)V", false)
                mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "java/io/File", "toURI", "()Ljava/net/URI;", false)
                mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "java/net/URI", "toURL", "()Ljava/net/URL;", false)
                mv.visitInsn(Opcodes.AASTORE)
                mv.visitLabel(tryEnd)
                mv.visitIincInsn(2, 1)
                mv.visitJumpInsn(Opcodes.GOTO, loopStart)
                mv.visitLabel(catchBlock)
                mv.visitInsn(Opcodes.POP)
                mv.visitIincInsn(2, 1)
                mv.visitJumpInsn(Opcodes.GOTO, loopStart)
                mv.visitLabel(loopEnd)
                mv.visitVarInsn(Opcodes.ALOAD, 1)
                mv.visitInsn(Opcodes.ARETURN)
                mv.visitMaxs(5, 3)
                mv.visitEnd()
                super.visitEnd()
            }
        }
        reader.accept(visitor, 0)
        return writer.toByteArray()
    }
    internal fun sendMouseButton(button: Int, action: Int) {
        try {
            Class.forName("org.lwjgl.glfw.CallbackBridge")
                .getMethod("nativeSendMouseButton", Int::class.java, Int::class.java, Int::class.java)
                .invoke(null, button, action, 0)
        } catch (_: Exception) {}
    }

    internal fun sendKey(glfwKeyCode: Int, action: Int) {
        Log.d("PING_LAUNCHER", "sendKey 호출: $glfwKeyCode, action=$action")

        try {
            val cb = Class.forName("org.lwjgl.glfw.CallbackBridge")

            // ★ InputReady 강제 ON (진단 + 임시 수정)
            cb.getMethod("nativeSetInputReady", Boolean::class.java).invoke(null, true)

            val scancode = getScancode(glfwKeyCode)
            cb.getMethod("nativeSendKey", Int::class.java, Int::class.java, Int::class.java, Int::class.java)
                .invoke(null, glfwKeyCode, scancode, action, 0)
//            if (action == 1) {
//                val keyChar = glfwKeyToChar(glfwKeyCode)
//                if (keyChar != '\u0000') {
//                    cb.getMethod("nativeSendChar", Char::class.java).invoke(null, keyChar)
//                }
//            }
        } catch (e: Exception) {
            Log.e("PING_LAUNCHER", "sendKey 예외", e)
        }
    }


    internal fun glfwKeyToChar(glfwKey: Int): Char = when (glfwKey) {
        in 65..90 -> ('a' + (glfwKey - 65))  // a-z
        in 48..57 -> ('0' + (glfwKey - 48))  // 0-9
        32 -> ' '
        else -> '\u0000'
    }

    internal fun getScancode(glfwKey: Int): Int = when (glfwKey) {
        // 알파벳 A=65 → scancode 30, B=66 → 48, C=67 → 46 ...
        65 -> 30   // A
        66 -> 48   // B
        67 -> 46   // C
        68 -> 32   // D
        69 -> 18   // E
        70 -> 33   // F
        71 -> 34   // G
        72 -> 35   // H
        73 -> 23   // I
        74 -> 36   // J
        75 -> 37   // K
        76 -> 38   // L
        77 -> 50   // M
        78 -> 49   // N
        79 -> 24   // O
        80 -> 25   // P
        81 -> 16   // Q
        82 -> 19   // R
        83 -> 31   // S
        84 -> 20   // T
        85 -> 22   // U
        86 -> 47   // V
        87 -> 17   // W
        88 -> 45   // X
        89 -> 21   // Y
        90 -> 44   // Z
        // 숫자
        48 -> 11   // 0
        49 -> 2    // 1
        50 -> 3    // 2
        51 -> 4    // 3
        52 -> 5    // 4
        53 -> 6    // 5
        54 -> 7    // 6
        55 -> 8    // 7
        56 -> 9    // 8
        57 -> 10   // 9
        // 특수키
        32 -> 57   // Space
        256 -> 1   // ESC
        257 -> 28  // Enter
        258 -> 15  // Tab
        259 -> 14  // Backspace
        261 -> 211 // Delete
        265 -> 103 // Up
        264 -> 108 // Down
        263 -> 105 // Left
        262 -> 106 // Right
        340 -> 42  // Left Shift
        341 -> 29  // Left Ctrl
        342 -> 56  // Left Alt
        344 -> 54  // Right Shift
        345 -> 97  // Right Ctrl
        346 -> 100 // Right Alt
        else -> 0
    }

    fun gaKey(jarPath: String, librariesRoot: String): String? {
        if (!jarPath.startsWith("$librariesRoot/")) return null
        val rel = jarPath.removePrefix("$librariesRoot/")
        val parts = rel.split("/")
        if (parts.size < 4) return null
        // [group..., artifact, version, filename]
        val artifactIdx = parts.size - 3
        val artifact = parts[artifactIdx]
        val group = parts.subList(0, artifactIdx).joinToString(".")
        return "$group:$artifact"
    }

    /**
     * JLI(java 커맨드 파서)는 "--add-opens X" 같은 두-토큰 형식을 "--add-opens=X" 로
     * 합쳐 JVM에 넘기지만, JNI_CreateJavaVM 직접 호출 경로엔 JLI 가 없다.
     * 두 토큰 그대로 들어가면 JVM 은 "--add-opens" 만 보고 값을 못 찾아 그냥 무시한다
     * (ignoreUnrecognized=JNI_TRUE 라 에러도 안 남). 그래서 여기서 미리 합쳐준다.
     *
     * 추가로 "-p" 는 JLI 전용 짧은 형이라 hotspot 자체는 못 알아먹음 → "--module-path" 로 정규화.
     */
    private val JLI_TWO_TOKEN_OPTS = setOf(
        "--add-opens", "--add-exports", "--add-reads", "--add-modules",
        "--patch-module", "--module-path", "-p", "--upgrade-module-path",
        "--limit-modules", "--module", "-m"
    )

    private fun normalizeJvmArgsForJni(args: Array<String>): Array<String> {
        val out = ArrayList<String>(args.size)
        var i = 0
        while (i < args.size) {
            val a = args[i]
            // 이미 "--add-opens=..." 처럼 = 가 붙어있으면 그대로
            val isBare = a in JLI_TWO_TOKEN_OPTS
            if (isBare && i + 1 < args.size) {
                val v = args[i + 1]
                val canonical = if (a == "-p") "--module-path" else a
                out.add("$canonical=$v")
                i += 2
            } else {
                out.add(a)
                i++
            }
        }
        return out.toTypedArray()
    }

    /**
     * Pojav 의 patched `lwjgl-glfw-classes*.jar` 는 lwjgl 의 모든 서브패키지
     * (glfw, opengl, openal, stb, system 등) 를 한 jar 에 통합한 fat jar.
     * vanilla `lwjgl-openal-*.jar`, `lwjgl-opengl-*.jar` 등이 같이 classpath 에 있으면
     * BootstrapLauncher 가 자동 모듈로 등록하다가 split package 폭발.
     * → patched fat jar 만 남기고 나머지 vanilla lwjgl-* jar 들은 classpath 에서 제거.
     */
    private fun isRedundantLwjglJar(file: File): Boolean {
        val n = file.name
        if (n.startsWith("lwjgl-glfw-classes", ignoreCase = true)) return false

        if (n.matches(Regex(
                "^lwjgl-(spvc|vma|shaderc|freetype|vulkan|glfw-callbacks|core)-\\d.*\\.jar$",
                RegexOption.IGNORE_CASE))) return false

        if (n.contains("natives", ignoreCase = true) && n.startsWith("lwjgl", ignoreCase = true)) return true
        return n.startsWith("lwjgl-", ignoreCase = true) || n == "lwjgl.jar"
    }

    private fun startMinecraft() {
        val base = applicationContext.filesDir
        val nativesDir = File(base, "natives")
        val jarList = mutableListOf<String>()
        val seenGA = mutableSetOf<String>()

        val instanceBase = instanceDir?.let { File(it) }
            ?: customGameDir?.let { File(it) }
            ?: File(getExternalFilesDir(null), "instances/vanilla_$versionId")

        val isLegacy = isLegacyVersion(versionId)

        val mcDir = if (isLegacy) {
            // 레거시 MC 는 user.home/.minecraft 를 강제로 사용.
            // 인스턴스 베이스 안에 .minecraft 폴더를 만들고 거기로 user.home 을 가리키게 함.
            val legacyRoot = File(instanceBase, ".minecraft")
            legacyRoot.mkdirs()
            File(legacyRoot, "logs").mkdirs()
            File(legacyRoot, "mods").mkdirs()
            legacyRoot
        } else {
            instanceBase.also {
                it.mkdirs()
                File(it, "logs").mkdirs()
                File(it, "mods").mkdirs()
            }
        }


        Log.d("PING_LAUNCHER", "instanceBase: ${instanceBase.absolutePath}")

        // 인스턴스 메타 로드 — Fabric의 gameJvmArgs/gameArgs 가져오기
        val instanceMeta = InstanceManager.loadMeta(instanceBase)
        val isFabric = mainClass.contains("knot", ignoreCase = true)
                || mainClass.contains("fabric", ignoreCase = true)
                || instanceMeta?.loaderType == "fabric"
        Log.d("PING_LAUNCHER", "isFabric=$isFabric, loaderType=${instanceMeta?.loaderType}, mainClass=$mainClass")

        // PojavLauncher 패치 LWJGL은 모든 MC 버전에 필요 (libglfw.so가 pojavInit 라우팅을 가정함)
        copyLwjglJars(base)
        patchLwjglGlfwIfNeeded(File(base, "lwjgl3"))
        val lwjgl3Dir = File(base, "lwjgl3")
        // 수정 — patched GLFW를 무조건 0번 인덱스에
        val lwjglJars = lwjgl3Dir.listFiles()
            ?.filter { it.extension == "jar" }
            ?.toMutableList() ?: mutableListOf()

        // patched glfw를 분리해서 맨 앞으로
        val patchedGlfw = lwjglJars.find { it.name.contains("glfw-classes") }
        val vanillaCore = lwjglJars.find { it.name.matches(Regex("^lwjgl-core-.*\\.jar$")) }
        val vanillaCallbacks = lwjglJars.find { it.name.contains("glfw-callbacks") }

        lwjglJars.remove(patchedGlfw)
        lwjglJars.remove(vanillaCore)
        lwjglJars.remove(vanillaCallbacks)
        lwjglJars.sortBy { it.name }

// 새 순서: vanilla core (APIUtil 등) → vanilla callbacks → patched (GLFW, opengl, openal, stb...) → 나머지
        if (vanillaCore != null) {
            Log.d("PING_LAUNCHER", "🔧 vanilla LWJGL core 우선 주입: ${vanillaCore.name}")
            jarList.add(vanillaCore.absolutePath)
        }
        if (vanillaCallbacks != null) {
            Log.d("PING_LAUNCHER", "🔧 vanilla GLFW callbacks 주입: ${vanillaCallbacks.name}")
            jarList.add(vanillaCallbacks.absolutePath)
        }
        if (patchedGlfw != null) {
            Log.d("PING_LAUNCHER", "🔧 patched GLFW fat 주입: ${patchedGlfw.name}")
            jarList.add(patchedGlfw.absolutePath)
        }
        lwjglJars.forEach { jar -> jarList.add(jar.absolutePath) }

        val cleanedExtraJars = extraJars.filter { p ->
            val f = File(p)
            when {
                isProcessorOnlyJar(f) -> { Log.d("PING_LAUNCHER", "🚫 extraJars processor-only 제거: ${f.name}"); false }
                isRedundantLwjglJar(f) -> { Log.d("PING_LAUNCHER", "🚫 extraJars vanilla lwjgl 제거: ${f.name}"); false }
                else -> true
            }
        }
        jarList.addAll(0, cleanedExtraJars)

        val searchDirs = listOfNotNull(
            instanceBase,
            getExternalFilesDir(null),
            base
        ).distinct()

        // 이미 jarList에 들어있는 extraJars(Fabric 라이브러리)의 GA를 먼저 점유
        jarList.toList().forEach { jp ->
            for (rootCandidate in searchDirs) {
                val libRoot = File(rootCandidate, "libraries").absolutePath
                val ga = gaKey(jp, libRoot)
                if (ga != null) { seenGA.add(ga); break }
            }
        }
        searchDirs.forEach { dir ->
            val librariesDir = File(dir, "libraries")
            if (librariesDir.exists()) {
                librariesDir.walkTopDown().forEach { f ->
                    if (!f.isFile || f.extension != "jar") return@forEach
                    if (f.name.contains("natives-linux")) return@forEach
                    if (jarList.contains(f.absolutePath)) return@forEach
                    if (isProcessorOnlyJar(f)) {
                        Log.d("PING_LAUNCHER", "🚫 processor-only jar 제외: ${f.name}")
                        return@forEach
                    }
                    if (isRedundantLwjglJar(f)) {                                  // ★ 추가
                        Log.d("PING_LAUNCHER", "🚫 vanilla lwjgl jar 제외 (patched fat 만 keep): ${f.name}")
                        return@forEach
                    }

                    // 마인크래프트 번들 LWJGL은 PojavLauncher 패치 버전과 충돌하므로 제외
                    // PojavLauncher 패치 GLFW만 제외. core/opengl/openal 등 다른 LWJGL 모듈은
                    // 1.14 번들 그대로 쓰는 게 호환성 안전.
                    val lowerName = f.name.lowercase()

                    // 변경 → glfw-classes 동명 클래스 충돌 방지를 위해 lwjgl-glfw-*만 제외
                    val lwjglGlfwPattern = Regex("^lwjgl-glfw-\\d.*\\.jar$")
                    if (lwjglGlfwPattern.matches(lowerName)) {
                        Log.d("PING_LAUNCHER", "번들 lwjgl-glfw 제외 (PojavLauncher patched 사용): ${f.name}")
                        return@forEach
                    }

                    val ga = gaKey(f.absolutePath, librariesDir.absolutePath)
                    if (ga != null && seenGA.contains(ga)) {
                        Log.d("PING_LAUNCHER", "중복 라이브러리 스킵: $ga (${f.name})")
                        return@forEach
                    }
                    if (ga != null) seenGA.add(ga)
                    jarList.add(f.absolutePath)
                }
            }

            val legacyDir = File(dir, "libraries_$versionId")
            if (legacyDir.exists()) {
                legacyDir.walkTopDown().forEach { f ->
                    if (!f.isFile || f.extension != "jar") return@forEach
                    if (f.name.contains("natives-linux")) return@forEach
                    if (jarList.contains(f.absolutePath)) return@forEach

                    val lowerName = f.name.lowercase()
                    // 변경
                    val lwjglGlfwPattern = Regex("^lwjgl-glfw-\\d.*\\.jar$")
                    if (lwjglGlfwPattern.matches(lowerName)) {
                        Log.d("PING_LAUNCHER", "번들 lwjgl-glfw 제외 (PojavLauncher patched 사용): ${f.name}")
                        return@forEach
                    }

                    val ga = gaKey(f.absolutePath, legacyDir.absolutePath)
                    if (ga != null && seenGA.contains(ga)) {
                        Log.d("PING_LAUNCHER", "중복 라이브러리 스킵: $ga (${f.name})")
                        return@forEach
                    }
                    if (ga != null) seenGA.add(ga)
                    jarList.add(f.absolutePath)
                }
            }
        }

        if (mainClass.contains("launchwrapper")) {
            patchLaunchwrapperIfNeeded(searchDirs)
        }

        val versionJar = searchDirs
            .map { File(it, "versions/$versionId/$versionId.jar") }
            .firstOrNull { it.exists() }
        versionJar?.let {
            if (!jarList.contains(it.absolutePath)) jarList.add(it.absolutePath)
        }

        val assetsDir = searchDirs
            .map { File(it, "assets") }
            .firstOrNull { File(it, "indexes").exists() && File(it, "indexes").listFiles()?.isNotEmpty() == true }
            ?: File(getExternalFilesDir(null) ?: base, "assets")

        val irisConfig = File(mcDir, "config/iris.properties")
        if (!irisConfig.exists()) {                       // ← 이미 존재하면 손대지 않음 (지금도 이 조건은 있음)
            irisConfig.parentFile?.mkdirs()
            irisConfig.writeText("shaders.enabled=false\n")
        }


        // ★ versionId 전달
        val libJvmPath = MinecraftJREPreparer.prepareJreAndGetPath(this, versionId)
        val jvmSettings = JvmSettingsManager.load(this)

        syncOptionsTxt(File(mcDir, "options.txt"), jvmSettings)

// ★ JDK 9+ 전용 플래그는 javaMajor>=9 일 때만 부착
        val isModularJre = javaMajor >= 9

// ★ JDK 8 에서는 미지원 옵션을 무시하도록 (G1NewSizePercent 등이 문제)
        val jvm8CompatArgs: Array<String> =
            if (!isModularJre) arrayOf("-XX:+IgnoreUnrecognizedVMOptions") else emptyArray()

        val launchWrapperArgs = if (isModularJre && mainClass.contains("launchwrapper")) {
            arrayOf(
                "--add-opens", "java.base/java.lang=ALL-UNNAMED",
                "--add-opens", "java.base/java.lang.reflect=ALL-UNNAMED",
                "--add-opens", "java.base/java.io=ALL-UNNAMED",
                "--add-opens", "java.base/sun.nio.ch=ALL-UNNAMED",
                "--add-exports", "java.base/jdk.internal.loader=ALL-UNNAMED",
                "--add-opens", "java.base/java.net=ALL-UNNAMED",
                "--add-opens", "java.base/java.util=ALL-UNNAMED",
                "--add-opens", "java.base/java.util.jar=ALL-UNNAMED",
                "--add-opens", "java.base/java.util.zip=ALL-UNNAMED",
            )
        } else emptyArray()

        val fabricJvmArgs = if (isModularJre && isFabric) {
            arrayOf(
                "--add-opens", "java.base/java.lang=ALL-UNNAMED",
                "--add-opens", "java.base/java.lang.reflect=ALL-UNNAMED",
                "--add-opens", "java.base/java.io=ALL-UNNAMED",
                "--add-opens", "java.base/java.net=ALL-UNNAMED",
                "--add-opens", "java.base/java.util=ALL-UNNAMED",
                "--add-opens", "java.base/java.util.jar=ALL-UNNAMED",
                "--add-opens", "java.base/java.util.zip=ALL-UNNAMED",
                "--add-opens", "java.base/sun.nio.ch=ALL-UNNAMED",
                "--add-exports", "java.base/jdk.internal.loader=ALL-UNNAMED",
            )
        } else emptyArray()


// ── Modern Forge / NeoForge 감지 (1.17+) ──
        val isModernForge = (instanceMeta?.loaderType == "forge" || instanceMeta?.loaderType == "neoforge")
                && (mainClass.startsWith("cpw.mods")
                || mainClass.contains("BootstrapLauncher", ignoreCase = true)
                || mainClass.contains("ProcessorLauncher", ignoreCase = true))   // ← 추가

// ── ${library_directory} 등 placeholder 해석 ──
        val forgeLibrariesDir = File(instanceBase, "libraries")
        val metaJvmArgsRaw = (instanceMeta?.gameJvmArgs ?: emptyList())
            .map { raw ->
                raw
                    .replace("\${library_directory}",   forgeLibrariesDir.absolutePath)
                    .replace("\${libraries_directory}", forgeLibrariesDir.absolutePath)
                    .replace("\${classpath_separator}", File.pathSeparator)
                    .replace("\${version_name}",        versionId)
                    .replace("\${natives_directory}",   nativesDir.absolutePath)
            }

// ── BootstrapLauncher 의 -DignoreList 에 LWJGL 들 추가 ──
//   PojavLauncher patched lwjgl-glfw-classes.jar 는 다른 LWJGL 서브모듈(openal, opengl 등)
//   클래스도 통합 포함되어 있어서 자동 모듈로 잡히면 split package 충돌 발생.
//   ignoreList 에 prefix 매칭시키면 classpath unnamed module 로 남아 충돌 회피.
        val metaJvmArgs: Array<String> = metaJvmArgsRaw.map { arg ->
            if (isModernForge && arg.startsWith("-DignoreList=")) {
                // 이미 들어있는지 확인 후 없으면 추가. lwjgl 한 prefix 로 lwjgl-glfw-classes,
                // lwjgl-openal, lwjgl-opengl, lwjgl-stb 등 한 번에 커버.
                val needed = listOf(
                    "ForgeAutoRenamingTool", "BinaryPatcher", "binarypatcher",
                    "jarsplitter", "installertools", "vignette", "DiffPatch", "diffpatch"
                ).filterNot { arg.contains(",$it") || arg.endsWith("=$it") }

                if (needed.isNotEmpty()) {
                    val patched = arg + "," + needed.joinToString(",")
                    Log.d("PING_LAUNCHER", "🩹 ignoreList 보강: +${needed.joinToString(",")}")
                    patched
                } else arg
            } else arg
        }.toTypedArray()

// ── Modern Forge fallback: 모듈 안 로드돼도 reflection 통과시키는 ALL-UNNAMED opens ──
        val modernForgeArgs: Array<String> = if (isModularJre && isModernForge) {
            arrayOf(
                "--add-opens", "java.base/java.util.jar=ALL-UNNAMED",
                "--add-opens", "java.base/java.lang.invoke=ALL-UNNAMED",
                "--add-opens", "java.base/java.lang=ALL-UNNAMED",
                "--add-opens", "java.base/java.lang.reflect=ALL-UNNAMED",
                "--add-opens", "java.base/java.net=ALL-UNNAMED",
                "--add-opens", "java.base/java.io=ALL-UNNAMED",
                "--add-opens", "java.base/sun.nio.ch=ALL-UNNAMED",
                "--add-exports", "java.base/sun.security.util=ALL-UNNAMED",
                "--add-exports", "jdk.naming.dns/com.sun.jndi.dns=java.naming",
            )
        } else emptyArray()

        Log.d("PING_LAUNCHER",
            "isModernForge=$isModernForge metaJvmArgs(resolved)=${metaJvmArgs.toList()}")

        val rendererPreference = RendererManager.load(this@MinecraftActivity)
        // MinecraftActivity.startMinecraft 안에서

        Log.d("PING_LAUNCHER", "isLegacy=$isLegacy, mcDir=${mcDir.absolutePath}")

        // Legacy MC 는 fixed-function GL 필요 — MobileGlues 로 불가
        val renderer = if (isLegacy && rendererPreference.id == "mobileglues") {
            Log.w("PingLauncherJVM", "Legacy $versionId — forcing GL4ES instead of MobileGlues")
            Renderer.fromId("holy_gl4es")  // 또는 "gl4es" / "gl4es_desktop" 중 enum 에 있는 것
        } else {
            rendererPreference
        }

        val glLibName = when (renderer.id) {
            "mobileglues" -> "libmobileglues.so"
            "gl4es", "gl4es_desktop", "holy_gl4es" -> "libgl4es_114.so"
            "zink" -> "libOSMesa.so"
            "ltw"  -> "libltw.so"     // ★ 추가
            else   -> "libgl4es_114.so"
        }

        Log.i("PingLauncherJVM", "🎨 Selected glLibName=$glLibName (renderer=${renderer.id})")

        // ── classpath 중복 제거 ─────────────────────────────────────
        val seenAbs = HashSet<String>()
        val seenFileName = HashSet<String>()
        val originalSize = jarList.size
        var dedupedJars = jarList.filter { abs ->
            if (!seenAbs.add(abs)) {
                Log.d("PING_LAUNCHER", "🗑 절대경로 중복 jar 제거: $abs")
                return@filter false
            }
            val fname = File(abs).name
            if (!seenFileName.add(fname)) {
                Log.d("PING_LAUNCHER", "🗑 동일 파일명 jar 중복 제거: $fname")
                return@filter false
            }
            true
        }

        if (isModernForge) {
            val moduleJarsFromMp = mutableSetOf<String>()
            var i = 0
            while (i < metaJvmArgs.size) {
                val a = metaJvmArgs[i]
                val mpValue: String? = when {
                    a.startsWith("--module-path=") -> a.removePrefix("--module-path=")
                    a == "--module-path" || a == "-p" -> metaJvmArgs.getOrNull(i + 1)
                    else -> null
                }
                if (mpValue != null) {
                    mpValue.split(File.pathSeparator)
                        .map { File(it).name }
                        .filter { it.isNotBlank() }
                        .forEach { moduleJarsFromMp.add(it) }
                }
                i++
            }
            Log.d("PING_LAUNCHER", "🎯 module-path jars (${moduleJarsFromMp.size}): $moduleJarsFromMp")

            val before = dedupedJars.size
            dedupedJars = dedupedJars.filter { abs ->
                val name = File(abs).name
                when {
                    name in moduleJarsFromMp -> {
                        Log.d("PING_LAUNCHER", "🚫 module-path 에 있어 classpath 제외: $name")
                        false
                    }
                    // processor-launcher 분기 자체를 삭제 — 이 jar 는 mainClass 일 때 classpath 필수
                    else -> true
                }
            }
            Log.d("PING_LAUNCHER", "📦 modern Forge classpath 정리: $before → ${dedupedJars.size}")
        }

        if (dedupedJars.size != originalSize) {
            Log.d("PING_LAUNCHER", "📦 classpath dedupe total: $originalSize → ${dedupedJars.size}")
        }
        val classPathStr = dedupedJars.joinToString(File.pathSeparator)

        val jvmArgs = jvm8CompatArgs +
                jvmSettings.toJvmArgArray(
                    context = this,
                    mcDir = mcDir,
                    userDir = mcDir.absolutePath,
                    classPath = classPathStr,
                    libraryPath = nativesDir.absolutePath,
                    mainClass = mainClass,
                    versionId = versionId
                ) +
                launchWrapperArgs +
                fabricJvmArgs +
                modernForgeArgs +     // ★ 추가 — metaJvmArgs 보다 앞에 둬서 version.json 인자가 덮어쓰도록
                metaJvmArgs

        Log.d("PING_LAUNCHER", "버전: $versionId, mcDir: ${mcDir.absolutePath}, isFabric=$isFabric, javaMajor=$javaMajor")

        Log.d("PING_LAUNCHER", "═══ classpath 항목 ${dedupedJars.size}개 ═══")
        dedupedJars.forEachIndexed { i, p -> Log.d("PING_LAUNCHER", "  [$i] ${File(p).name}") }
        Log.d("PING_LAUNCHER", "═══ mods/ 폴더 ═══")
        File(mcDir, "mods").listFiles()?.forEach {
            Log.d("PING_LAUNCHER", "  ${it.name} (${it.length()}B)")
        }

        Thread {
            try {
                val session = MicrosoftAuthManager.loadSession(this)
                val validSession = if (session != null && !MicrosoftAuthManager.isSessionValid(session)) {
                    try {
                        MicrosoftAuthManager.refreshSession(session.refreshToken)
                            .also { MicrosoftAuthManager.saveSession(this, it) }
                    } catch (_: Exception) { session }
                } else session

                val username    = validSession?.username    ?: "Player"
                val uuid        = validSession?.uuid        ?: "00000000-0000-0000-0000-000000000000"
                val accessToken = validSession?.accessToken ?: "0"
                val userType    = if (validSession != null) "msa" else "mojang"

                // 매니페스트가 minecraftArguments(=공백 구분 단일 문자열)를 줬다면 그게 1.12 이하 레거시 포맷이다.
                // gameArgs 안에 ${...} placeholder가 있다는 사실 자체가 그 시그널.
                val legacyArgs = instanceMeta?.gameArgs.orEmpty()
                val isLegacyArgs = legacyArgs.any { it.contains("\${") }

                val mcArgs: Array<String> = if (isLegacyArgs) {
                    // ── 1.12 이하: placeholder 치환만 해서 그대로 사용 ──
                    val placeholders = mapOf(
                        "\${auth_player_name}"  to username,
                        "\${auth_session}"      to "token:$accessToken:$uuid", // 1.5.x 시절 단일 토큰 포맷
                        "\${auth_uuid}"         to uuid,
                        "\${auth_access_token}" to accessToken,
                        "\${version_name}"      to versionId,
                        "\${game_directory}"    to mcDir.absolutePath,
                        "\${game_assets}"       to assetsDir.absolutePath,    // pre-1.6 legacy assets
                        "\${assets_root}"       to assetsDir.absolutePath,
                        "\${assets_index_name}" to assetIndex,
                        "\${user_type}"         to userType,
                        "\${version_type}"      to if (isFabric) "Fabric" else "release",
                        "\${user_properties}"   to "{}",
                        "\${profile_name}"      to username,
                        "\${launcher_name}"     to "PingLauncher",
                        "\${launcher_version}"  to "1.0"
                    )
                    val resolved = legacyArgs.map { arg ->
                        placeholders.entries.fold(arg) { acc, (k, v) -> acc.replace(k, v) }
                    }
                    Log.d("PING_LAUNCHER", "legacy mcArgs (${resolved.size}): $resolved")
                    resolved.toTypedArray()
                } else {
                    // ── 1.13+ (또는 Fabric/모드팩): 기존 하드코딩 + 메타 추가 인자 ──
                    val baseMcArgs = arrayOf(
                        "--username",   username,
                        "--version",    versionId,
                        "--gameDir",    mcDir.absolutePath,
                        "--assetsDir",  assetsDir.absolutePath,
                        "--assetIndex", assetIndex,
                        "--uuid",       uuid,
                        "--accessToken",accessToken,
                        "--userType",   userType,
                        "--versionType",if (isFabric) "Fabric" else "release"
                    )
                    val metaGameArgs = legacyArgs.toTypedArray() // placeholder 없는 추가 인자만 들어옴
                    baseMcArgs + metaGameArgs
                }

                val launcher = JavaNativeLauncher()
                val renderer = RendererManager.load(this@MinecraftActivity)
                val rendererEnv = renderer.buildEnv(
                    cacheDir = applicationContext.cacheDir.absolutePath,
                    nativeDir = applicationInfo.nativeLibraryDir
                ).toMutableMap().apply {
                    if (renderer.id == "virgl") {
                        this["VTEST_SOCKET_NAME"] =
                            VirGLLauncher.socketPath(this@MinecraftActivity)
                    }

                    if (jvmSettings.unlockFps) {
                        this["FORCE_VSYNC"]       = "false"
                        this["POJAV_VSYNC"]       = "0"
                        this["LIBGL_VSYNC"]       = "0"     // GL4ES 계열
                        this["POJAV_VSYNC_IN_ZINK"] = "0"   // Zink/OSMesa 경로 (swap_interval_no_egl.c)
                    }
                }

                Log.d("PING_LAUNCHER", "🎨 적용된 렌더러: ${renderer.displayName}")
                rendererEnv.forEach { (k, v) -> Log.d("PING_LAUNCHER", "  env $k=$v") }
                launcher.applyEnv(rendererEnv)

                val normalizedJvmArgs = normalizeJvmArgsForJni(jvmArgs)

                Log.d("PING_LAUNCHER", "정규화 후 JVM 인자 ${normalizedJvmArgs.size}개")
                normalizedJvmArgs.forEachIndexed { idx, a ->
                    if (a.startsWith("--add-") || a.startsWith("--module-path") || a.startsWith("--patch-module")) {
                        Log.d("PING_LAUNCHER", "  [$idx] $a")
                    }
                }

                launcher.bootMinecraftJVM(libJvmPath, normalizedJvmArgs, mcArgs)
            } catch (e: Exception) {
                Log.e("PING_LAUNCHER", "MC 실행 예외: ${e.message}")
            } finally {
                val crashDir = File(instanceBase, "crash-reports")
                val files = crashDir.listFiles()
                val hasCrash = files
                    ?.any { it.extension == "txt" &&
                            System.currentTimeMillis() - it.lastModified() < 60_000 } == true
                if (hasCrash) {
                    runOnUiThread { CrashReportActivity.start(this, instanceBase.absolutePath) }
                }
            }
        }.start()

        Thread {
            Log.d("PING_LAUNCHER", "🔵 showingWindow 워치독 시작")
            val deadline = System.currentTimeMillis() + 120_000
            var attempts = 0
            var success = false

            while (System.currentTimeMillis() < deadline) {
                attempts++
                try {
                    if (nativeTrySetupShowingWindow()) {
                        Log.d("PING_LAUNCHER", "✅ showingWindow 세팅 완료 (시도 $attempts)")
                        success = true
                        // 한 번 성공해도 MC 가 풀스크린/리사이즈 하면 새 window 가 생길 수 있어서
                        // 5초마다 재확인. 같은 핸들이면 native 쪽이 어차피 노옵.
                        Thread.sleep(5000)
                        continue
                    }
                    if (attempts % 20 == 0) {
                        Log.d("PING_LAUNCHER", "🔵 대기중... (시도 $attempts)")
                    }
                } catch (e: Throwable) {
                    Log.w("PING_LAUNCHER", "워치독 예외: ${e.message}")
                }
                Thread.sleep(500)
            }
            Log.d("PING_LAUNCHER", "🔵 워치독 종료 (success=$success)")
        }.apply { isDaemon = true; start() }
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        if (keyCode == KeyEvent.KEYCODE_BACK) {
            // 마우스 소스면 우클릭, 아니면 ESC
            if (event.source and InputDevice.SOURCE_MOUSE != 0) {
                sendMouseButton(1, GLFW_PRESS)
            } else {
                sendKey(256, GLFW_PRESS) // ESC
            }
            return true
        }
        val glfwKey = androidKeyToGlfw(keyCode) ?: return false
        sendKey(glfwKey, GLFW_PRESS)
        return true
    }

    override fun onKeyUp(keyCode: Int, event: KeyEvent): Boolean {
        if (keyCode == KeyEvent.KEYCODE_BACK) {
            sendKey(256, GLFW_RELEASE)
            return true
        }
        val glfwKey = androidKeyToGlfw(keyCode) ?: return false
        sendKey(glfwKey, GLFW_RELEASE)
        return true
    }


    private fun installPhysicalKeyboardInterceptor() {
        val originalCallback = window.callback
        window.callback = object : android.view.Window.Callback by originalCallback {
            override fun dispatchKeyEvent(event: KeyEvent): Boolean {
                if (handlePhysicalKey(event)) return true
                return originalCallback.dispatchKeyEvent(event)
            }
        }
    }

    private fun handlePhysicalKey(event: KeyEvent): Boolean {
        Log.d("PING_LAUNCHER",
            "🔑 handlePhysicalKey: keyCode=${event.keyCode} " +
                    "action=${event.action} src=0x${event.source.toString(16)}")

        // JVM 부팅 전엔 무시
        if (!jvmStarted) return false

        if (!jvmStarted) return false

        // ★ ACTION_MULTIPLE 은 IME 가 합성한 이벤트 — 절대 가로채지 말 것
        if (event.action == KeyEvent.ACTION_MULTIPLE) return false

        // ★ IME 가 처리해야 하는 키 — 한글 전환, 한자 등
        when (event.keyCode) {
            KeyEvent.KEYCODE_LANGUAGE_SWITCH,   // 한영 키
            KeyEvent.KEYCODE_KANA,
            KeyEvent.KEYCODE_HENKAN,
            KeyEvent.KEYCODE_MUHENKAN,
            KeyEvent.KEYCODE_EISU -> return false
        }

        val hasKeyboardSource =
            (event.source and InputDevice.SOURCE_KEYBOARD) ==
                    InputDevice.SOURCE_KEYBOARD

        if (!hasKeyboardSource) return false

        // 물리 키보드 / 외장 키보드만 가로채기
        // (소프트 IME 는 deviceId == -1 또는 KeyCharacterMap.VIRTUAL_KEYBOARD)
        val isPhysical =
                (event.source and InputDevice.SOURCE_KEYBOARD) != 0 &&
                event.deviceId != android.view.KeyCharacterMap.VIRTUAL_KEYBOARD

        if (!isPhysical) return false

        // 시스템 키는 패스 (볼륨, 전원, 홈 등)
        when (event.keyCode) {
            KeyEvent.KEYCODE_VOLUME_UP,
            KeyEvent.KEYCODE_VOLUME_DOWN,
            KeyEvent.KEYCODE_VOLUME_MUTE,
            KeyEvent.KEYCODE_POWER,
            KeyEvent.KEYCODE_HOME -> return false
        }

        val isDown = event.action == KeyEvent.ACTION_DOWN
        val glfwAction = if (isDown) 1 else 0

        // 1) 특수키 우선 매핑 (androidKeyToGlfw 가 못 잡는 것들)
        val specialKey = when (event.keyCode) {
            KeyEvent.KEYCODE_DEL          -> 259  // Backspace
            KeyEvent.KEYCODE_FORWARD_DEL  -> 261  // Delete
            KeyEvent.KEYCODE_ENTER,
            KeyEvent.KEYCODE_NUMPAD_ENTER -> 257
            KeyEvent.KEYCODE_DPAD_LEFT    -> 263
            KeyEvent.KEYCODE_DPAD_RIGHT   -> 262
            KeyEvent.KEYCODE_DPAD_UP      -> 265
            KeyEvent.KEYCODE_DPAD_DOWN    -> 264
            else -> null
        }

        if (specialKey != null) {
            sendKey(specialKey, glfwAction)
            return true
        }

        // 2) 일반 키 → GLFW 매핑
        // 일반 키 → GLFW 매핑
        val glfwKey = androidKeyToGlfw(event.keyCode)
        if (glfwKey != null) {
            sendKey(glfwKey, glfwAction)

            if (isDown) {
                var unicodeChar = event.getUnicodeChar(event.metaState)

                // ★ fallback: unicodeChar 가 0 이지만 우리가 아는 키면 직접 매핑
                if (unicodeChar == 0) {
                    unicodeChar = when (event.keyCode) {
                        KeyEvent.KEYCODE_SPACE -> ' '.code
                        KeyEvent.KEYCODE_TAB   -> '\t'.code
                        else -> 0
                    }
                }

                if (unicodeChar != 0) {
                    val glfwMods = (
                            (if (event.isShiftPressed) 0x0001 else 0) or
                                    (if (event.isCtrlPressed)  0x0002 else 0) or
                                    (if (event.isAltPressed)   0x0004 else 0)
                            )
                    sendCharToMc(unicodeChar.toChar(), glfwMods)
                }
            }
            return true
        }

        // 4) GLFW 매핑은 없지만 unicodeChar 가 있으면 (예: 한글, 특수문자)
        //    채팅창용으로 문자만 송신
        if (isDown) {
            val unicodeChar = event.getUnicodeChar(event.metaState)
            if (unicodeChar != 0) {
                sendCharToMc(unicodeChar.toChar())
                return true
            }
        }

        return false
    }

    private fun sendCharToMc(c: Char, mods: Int = 0) {
        Log.d("PING_LAUNCHER", "📝 sendCharToMc: '$c' (0x${c.code.toString(16)}) mods=$mods")
        try {
            val cb = Class.forName("org.lwjgl.glfw.CallbackBridge")

            // 1) Char 콜백 (1.12 이하 + 일부 모드용)
            cb.getMethod("nativeSendChar", Char::class.java).invoke(null, c)

            // 2) CharMods 콜백 (1.13+ MC 본체용)
            cb.getMethod("nativeSendCharMods", Char::class.java, Int::class.java)
                .invoke(null, c, mods)
        } catch (e: Exception) {
            Log.e("PING_LAUNCHER", "📝 sendChar 예외", e)
        }
    }


    private fun isProcessorOnlyJar(file: File): Boolean {
        val name = file.name
        // mergetool 은 두 종류 — "*-api.jar" 는 게임 부팅에도 필요하니 보존
        if (name.startsWith("mergetool", ignoreCase = true) && name.endsWith("-api.jar")) return false
        return PROCESSOR_ONLY_JAR_PREFIXES.any { name.startsWith(it, ignoreCase = true) }
    }

    /**
     * options.txt 의 maxFps / enableVsync 만 강제로 우리 설정에 맞춰 덮어쓴다.
     * 나머지 옵션(키 바인딩, 볼륨 등) 은 사용자 변경을 보존.
     */
    private fun syncOptionsTxt(optionsFile: File, settings: JvmSettings) {
        val targetMaxFps   = if (settings.unlockFps) 260 else 120
        val targetVsync    = if (settings.unlockFps) "false" else "true"
        val targetRenderD  = settings.renderDistance
        val targetGfxMode  = settings.graphicsMode

        val existing: MutableList<String> = if (optionsFile.exists())
            optionsFile.readLines().toMutableList()
        else
            mutableListOf()

        fun upsert(key: String, value: String) {
            val idx = existing.indexOfFirst { it.startsWith("$key:") }
            val line = "$key:$value"
            if (idx >= 0) existing[idx] = line else existing.add(line)
        }

        upsert("maxFps",         targetMaxFps.toString())
        upsert("enableVsync",    targetVsync)
        upsert("renderDistance", targetRenderD.toString())
        upsert("graphicsMode",   targetGfxMode.toString())
        upsert("renderClouds",   "false")

        optionsFile.writeText(existing.joinToString("\n"))
        Log.d("PING_LAUNCHER",
            "📝 options.txt sync: maxFps=$targetMaxFps vsync=$targetVsync renderDist=$targetRenderD")
    }

    fun androidKeyToGlfw(keyCode: Int): Int? = when (keyCode) {
        KeyEvent.KEYCODE_W -> GLFW_KEY_W
        KeyEvent.KEYCODE_A -> GLFW_KEY_A
        KeyEvent.KEYCODE_S -> GLFW_KEY_S
        KeyEvent.KEYCODE_D -> GLFW_KEY_D
        KeyEvent.KEYCODE_E -> GLFW_KEY_E
        KeyEvent.KEYCODE_SPACE -> GLFW_KEY_SPACE
        KeyEvent.KEYCODE_SHIFT_LEFT -> GLFW_KEY_LEFT_SHIFT
        KeyEvent.KEYCODE_CTRL_LEFT -> GLFW_KEY_LEFT_CONTROL
        KeyEvent.KEYCODE_Q -> 81
        KeyEvent.KEYCODE_F -> 70
        KeyEvent.KEYCODE_R -> 82
        KeyEvent.KEYCODE_T -> 84
        KeyEvent.KEYCODE_ESCAPE -> GLFW_KEY_ESCAPE
        KeyEvent.KEYCODE_ENTER -> GLFW_KEY_ENTER
        KeyEvent.KEYCODE_TAB -> GLFW_KEY_TAB
        KeyEvent.KEYCODE_1 -> 49
        KeyEvent.KEYCODE_2 -> 50
        KeyEvent.KEYCODE_3 -> 51
        KeyEvent.KEYCODE_4 -> 52
        KeyEvent.KEYCODE_5 -> 53
        KeyEvent.KEYCODE_6 -> 54
        KeyEvent.KEYCODE_7 -> 55
        KeyEvent.KEYCODE_8 -> 56
        KeyEvent.KEYCODE_9 -> 57
        KeyEvent.KEYCODE_SLASH       -> 47   // /
        KeyEvent.KEYCODE_PERIOD      -> 46   // .
        KeyEvent.KEYCODE_COMMA       -> 44   // ,
        KeyEvent.KEYCODE_MINUS       -> 45   // -
        KeyEvent.KEYCODE_EQUALS      -> 61   // =
        KeyEvent.KEYCODE_SEMICOLON   -> 59   // ;
        KeyEvent.KEYCODE_APOSTROPHE  -> 39   // '
        KeyEvent.KEYCODE_LEFT_BRACKET  -> 91  // [
        KeyEvent.KEYCODE_RIGHT_BRACKET -> 93  // ]
        KeyEvent.KEYCODE_BACKSLASH   -> 92   // \
        KeyEvent.KEYCODE_GRAVE       -> 96   // `
        else -> null
    }

    private fun copyLwjglJars(base: File) {
        val targetDir = File(base, "lwjgl3").apply { mkdirs() }
        try {
            val jarNames = assets.list("lwjgl3") ?: return
            for (jarName in jarNames) {
                if (!jarName.endsWith(".jar")) continue
                val target = File(targetDir, jarName)
                if (target.exists() && target.length() > 0) continue
                assets.open("lwjgl3/$jarName").use { input ->
                    target.outputStream().use { output -> input.copyTo(output) }
                }
                Log.d("PING_LAUNCHER", "📦 LWJGL jar 추출: $jarName (${target.length()} bytes)")
            }
        } catch (e: Exception) {
            Log.e("PING_LAUNCHER", "LWJGL jar 추출 실패", e)
        }
    }


    override fun onResume() {
        super.onResume()
        window.decorView.findViewWithTag<View>("minecraft_surface")
            ?.let { it.requestFocus() }
            ?: window.decorView.requestFocus()

        Log.d("PING_LAUNCHER", "onResume — surface 재바인딩 대기")
    }

    override fun onPause() {
        super.onPause()
        if (jvmStarted && isGrabbing) {
            sendKey(256, GLFW_PRESS)    // ESC 누름
            sendKey(256, GLFW_RELEASE)
        }
    }

    override fun onDestroy() {
        currentInstance = null
        // ★ 리스너 해제
        inputDeviceListener?.let { listener ->
            try {
                val im = getSystemService(INPUT_SERVICE)
                        as android.hardware.input.InputManager
                im.unregisterInputDeviceListener(listener)
            } catch (_: Throwable) {}
        }
        inputDeviceListener = null
        gameControllerView = null

        currentInstance = null
        super.onDestroy()
    }
}