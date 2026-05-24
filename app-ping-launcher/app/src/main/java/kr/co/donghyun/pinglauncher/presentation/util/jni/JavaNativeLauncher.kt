package kr.co.donghyun.pinglauncher.presentation.util.jni

class JavaNativeLauncher {
    companion object {
        init {
            // cpp 폴더에 만들 네이티브 라이브러리 이름 (libpingjvm.so)
            System.loadLibrary("pingjvm")
        }
    }

    /**
     * @param libJvmPath 다운받아 압축을 푼 jre 내부의 'libjvm.so' 파일의 절대 경로
     * @param jvmArgs JVM에 넘겨줄 옵션들 (-Xmx1024M, -Djava.library.path=... 등)
     * @param mcArgs 마인크래프트 Main 클래스에 넘겨줄 인자들 (--username, --version 등)
     */
    external fun bootMinecraftJVM(
        libJvmPath: String,
        jvmArgs: Array<String>,
        mcArgs: Array<String>
    ): Int
}