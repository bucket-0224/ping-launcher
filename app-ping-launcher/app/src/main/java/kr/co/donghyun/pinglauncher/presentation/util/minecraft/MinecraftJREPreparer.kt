package kr.co.donghyun.pinglauncher.presentation.util.minecraft

import android.content.Context
import java.io.File
import java.io.FileOutputStream
import java.util.zip.ZipInputStream

class MinecraftJREPreparer  {
    companion object {
        fun prepareJreAndGetPath(context: Context): String {
            val targetDir = File(context.filesDir, "jre21_runtime")

            if (!targetDir.exists() || targetDir.listFiles()?.isEmpty() == true) {
                targetDir.mkdirs()
                println("📦 JRE 21 최초 압축 해제 시작...")

                context.assets.open("jre21.zip").use { inputStream ->
                    ZipInputStream(inputStream).use { zip ->
                        var entry = zip.nextEntry
                        while (entry != null) {
                            val outFile = File(targetDir, entry.name)
                            if (entry.isDirectory) {
                                outFile.mkdirs()
                            } else {
                                outFile.parentFile?.mkdirs()
                                FileOutputStream(outFile).use { output ->
                                    zip.copyTo(output)
                                }
                            }
                            zip.closeEntry()
                            entry = zip.nextEntry
                        }
                    }
                }
                println("✅ JRE 21 압축 해제 완료!")
            }

            var libJvmFile: File? = null
            targetDir.walkTopDown().forEach { file ->
                if (file.name == "libjvm.so") libJvmFile = file
            }

            if (libJvmFile != null && libJvmFile.exists()) {
                libJvmFile.setExecutable(true)
                println("🎯 JVM 21 경로: ${libJvmFile.absolutePath}")
                return libJvmFile.absolutePath
            } else {
                throw Exception("❌ libjvm.so 파일을 찾을 수 없습니다.")
            }
        }
    }
}