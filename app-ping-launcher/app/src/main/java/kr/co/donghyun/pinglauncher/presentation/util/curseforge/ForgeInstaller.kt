package kr.co.donghyun.pinglauncher.presentation.util.curseforge


import android.util.Log
import com.google.gson.Gson
import kr.co.donghyun.pinglauncher.data.curseforge.ForgeLibrary
import kr.co.donghyun.pinglauncher.data.curseforge.ForgeVersionManifest
import okhttp3.OkHttpClient
import okhttp3.Request
import org.objectweb.asm.ClassReader
import org.objectweb.asm.ClassVisitor
import org.objectweb.asm.ClassWriter
import org.objectweb.asm.MethodVisitor
import org.objectweb.asm.Opcodes
import java.io.File
import java.io.FileOutputStream
import java.util.zip.ZipFile

class ForgeInstaller(
    private val gameDir: File,
    private val onProgress: (String) -> Unit
) {
    private val client = OkHttpClient()
    private val gson = Gson()

    // e.g. "forge-1.20.1-47.2.0"
//    fun install(forgeId: String): ForgeInstallResult {
//
//        if (forgeId.contains("fabric")) {
//            return installFabric(forgeId)
//        }
//
//        val parts = forgeId.removePrefix("forge-").split("-")
//        val mcVersion = parts[0]
//        val forgeVersion = parts[1]
//
//        onProgress("Forge $forgeVersion 설치 중...")
//
//        // 1. Forge 버전 JSON 다운로드
//        val forgeManifest = fetchForgeManifest(mcVersion, forgeVersion)
//            ?: return ForgeInstallResult(success = false, error = "Forge 버전 정보를 가져올 수 없음")
//
//        // 2. Forge 라이브러리 다운로드
//        val librariesDir = File(gameDir, "libraries_forge_${mcVersion}_${forgeVersion}")
//        val jarList = mutableListOf<String>()
//        val totalLibs = forgeManifest.libraries.size
//
//        forgeManifest.libraries.forEachIndexed { index, lib ->
//            onProgress("Forge 라이브러리 다운로드 (${index + 1}/$totalLibs): ${lib.name}")
//            try {
//                val jarFile = downloadForgeLibrary(lib, librariesDir)
//                jarFile?.let { jarList.add(it.absolutePath) }
//            } catch (e: Exception) {
//                Log.w("PING_LAUNCHER", "Forge 라이브러리 다운로드 실패: ${lib.name} - ${e.message}")
//            }
//        }
//
//        // 3. Forge 클라이언트 jar 경로 확인
//        val forgeJar = File(gameDir, "versions/forge-$mcVersion-$forgeVersion/forge-$mcVersion-$forgeVersion.jar")
//        if (!forgeJar.exists()) {
//            downloadForgeJar(mcVersion, forgeVersion, forgeJar)
//        }
//        if (forgeJar.exists()) jarList.add(0, forgeJar.absolutePath)
//
//        // launchwrapper 패치 (JRE 9+ 호환)
//        patchLaunchwrapper(librariesDir)  // ← 여기
//
//        onProgress("Forge 설치 완료!")
//        Log.d("PING_LAUNCHER", "✅ Forge 설치 완료: ${jarList.size}개 라이브러리")
//
//        return ForgeInstallResult(
//            success = true,
//            mainClass = forgeManifest.mainClass,
//            extraJars = jarList,
//            mcVersion = mcVersion,
//            forgeVersion = forgeVersion
//        )
//    }

    fun install(forgeId: String): ForgeInstallResult {
        if (forgeId.contains("fabric")) {
            return installFabric(forgeId)
        }

        return ForgeInstallResult(
            success = false,
            error = "Forge는 현재 지원되지 않습니다. Fabric 모드팩을 사용하세요."
        )
    }

    private fun installFabric(forgeId: String): ForgeInstallResult {
        // forge-1.21.1-fabric-0.18.4 → mcVersion=1.21.1, fabricVersion=0.18.4
        val parts = forgeId.removePrefix("forge-").split("-fabric-")
        val mcVersion = parts[0]
        val fabricVersion = parts.getOrNull(1) ?: return ForgeInstallResult(success = false, error = "Fabric 버전 파싱 실패")

        onProgress("Fabric $fabricVersion 설치 중...")

        // Fabric 라이브러리 목록 가져오기
        val metaUrl = "https://meta.fabricmc.net/v2/versions/loader/$mcVersion/$fabricVersion/profile/json"
        val manifest = try {
            val request = Request.Builder().url(metaUrl).build()
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return ForgeInstallResult(success = false, error = "Fabric manifest 실패: ${response.code}")
                val json = response.body?.string() ?: return ForgeInstallResult(success = false, error = "Fabric manifest 비어있음")
                gson.fromJson(json, ForgeVersionManifest::class.java)
            }
        } catch (e: Exception) {
            return ForgeInstallResult(success = false, error = "Fabric manifest 오류: ${e.message}")
        }

        val librariesDir = File(gameDir, "libraries_fabric_${mcVersion}_${fabricVersion}")
        val jarList = mutableListOf<String>()
        val total = manifest.libraries.size


        manifest.libraries.forEachIndexed { index, lib ->
            onProgress("Fabric 라이브러리 (${index + 1}/$total): ${lib.name}")
            try {
                val jarFile = downloadForgeLibrary(lib, librariesDir)
                jarFile?.let { jarList.add(it.absolutePath) }
            } catch (e: Exception) {
                Log.w("PING_LAUNCHER", "Fabric 라이브러리 실패: ${lib.name}")
            }
        }

        return ForgeInstallResult(
            success = true,
            mainClass = manifest.mainClass,
            extraJars = jarList,
            mcVersion = mcVersion,
            forgeVersion = fabricVersion
        )
    }


    // ForgeInstaller.kt에 추가
    private fun patchLaunchwrapper(librariesDir: File) {
        // launchwrapper jar 찾기
        val lwJar = librariesDir.walkTopDown()
            .find { it.name.startsWith("launchwrapper") && it.extension == "jar" }
            ?: return

        Log.d("PING_LAUNCHER", "launchwrapper 패치 중: ${lwJar.absolutePath}")

        try {
            val zipIn = ZipFile(lwJar)
            val patchedJar = File(lwJar.parent, lwJar.name + ".patched")
            val zipOut = java.util.zip.ZipOutputStream(patchedJar.outputStream())

            zipIn.entries().asSequence().forEach { entry ->
                val bytes = zipIn.getInputStream(entry).readBytes()

                val patched = if (entry.name == "net/minecraft/launchwrapper/Launch.class") {
                    patchLaunchClass(bytes)
                } else bytes

                zipOut.putNextEntry(java.util.zip.ZipEntry(entry.name))
                zipOut.write(patched)
                zipOut.closeEntry()
            }

            zipIn.close()
            zipOut.close()

            lwJar.delete()
            patchedJar.renameTo(lwJar)
            Log.d("PING_LAUNCHER", "✅ launchwrapper 패치 완료")
        } catch (e: Exception) {
            Log.e("PING_LAUNCHER", "launchwrapper 패치 실패: ${e.message}")
        }
    }

    private fun patchLaunchClass(bytes: ByteArray): ByteArray {
        // ASM으로 checkcast URLClassLoader를 우회
        val reader = ClassReader(bytes)
        val writer = ClassWriter(reader, ClassWriter.COMPUTE_FRAMES)

        val visitor = object : ClassVisitor(Opcodes.ASM9, writer) {
            override fun visitMethod(
                access: Int, name: String, descriptor: String,
                signature: String?, exceptions: Array<out String>?
            ): MethodVisitor {
                val mv = super.visitMethod(access, name, descriptor, signature, exceptions)

                // 생성자만 패치
                if (name == "<init>" && descriptor == "()V") {
                    return object : MethodVisitor(Opcodes.ASM9, mv) {
                        override fun visitTypeInsn(opcode: Int, type: String) {
                            if (opcode == Opcodes.CHECKCAST
                                && type == "java/net/URLClassLoader") {
                                // checkcast URLClassLoader 제거하고 대신
                                // classpath 기반으로 URL[] 생성하는 코드 삽입

                                // 스택에서 classloader 팝
                                visitInsn(Opcodes.POP)

                                // System.getProperty("java.class.path") 로 URL[] 생성
                                visitLdcInsn("java.class.path")
                                visitLdcInsn("")
                                visitMethodInsn(
                                    Opcodes.INVOKESTATIC,
                                    "java/lang/System", "getProperty",
                                    "(Ljava/lang/String;Ljava/lang/String;)Ljava/lang/String;",
                                    false
                                )
                                visitLdcInsn(File.pathSeparator)
                                visitMethodInsn(
                                    Opcodes.INVOKEVIRTUAL,
                                    "java/lang/String", "split",
                                    "(Ljava/lang/String;)[Ljava/lang/String;",
                                    false
                                )
                                // String[] -> URL[] 변환 헬퍼 호출
                                visitMethodInsn(
                                    Opcodes.INVOKESTATIC,
                                    "net/minecraft/launchwrapper/Launch", "stringsToUrls",
                                    "([Ljava/lang/String;)[Ljava/net/URL;",
                                    false
                                )
                                return // checkcast 건너뜀
                            }
                            super.visitTypeInsn(opcode, type)
                        }

                        override fun visitMethodInsn(
                            opcode: Int, owner: String, name: String,
                            descriptor: String, isInterface: Boolean
                        ) {
                            // getURLs() 호출 제거 (이미 URL[]이 스택에 있음)
                            if (owner == "java/net/URLClassLoader" && name == "getURLs") {
                                return
                            }
                            super.visitMethodInsn(opcode, owner, name, descriptor, isInterface)
                        }
                    }
                }
                return mv
            }

            // 헬퍼 메서드 추가
            override fun visitEnd() {
                val mv = cv.visitMethod(
                    Opcodes.ACC_PRIVATE or Opcodes.ACC_STATIC,
                    "stringsToUrls",
                    "([Ljava/lang/String;)[Ljava/net/URL;",
                    null, null
                )
                mv.visitCode()
                // URL[] urls = new URL[paths.length]
                mv.visitVarInsn(Opcodes.ALOAD, 0)
                mv.visitInsn(Opcodes.ARRAYLENGTH)
                mv.visitTypeInsn(Opcodes.ANEWARRAY, "java/net/URL")
                mv.visitVarInsn(Opcodes.ASTORE, 1)
                // for loop
                mv.visitInsn(Opcodes.ICONST_0)
                mv.visitVarInsn(Opcodes.ISTORE, 2)
                val loopStart = org.objectweb.asm.Label()
                val loopEnd = org.objectweb.asm.Label()
                mv.visitLabel(loopStart)
                mv.visitVarInsn(Opcodes.ILOAD, 2)
                mv.visitVarInsn(Opcodes.ALOAD, 0)
                mv.visitInsn(Opcodes.ARRAYLENGTH)
                mv.visitJumpInsn(Opcodes.IF_ICMPGE, loopEnd)
                // try { urls[i] = new File(paths[i]).toURI().toURL(); } catch(Exception e) {}
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
                mv.visitJumpInsn(Opcodes.GOTO, loopStart.also {
                    mv.visitIincInsn(2, 1)
                })
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

    private fun fetchForgeManifest(mcVersion: String, forgeVersion: String): ForgeVersionManifest? {
        // Maven 저장소에서 Forge 버전 JSON 가져오기
        "https://maven.minecraftforge.net/net/minecraftforge/forge/$mcVersion-$forgeVersion/forge-$mcVersion-$forgeVersion-installer.jar"

        // Forge 버전 JSON은 installer jar 안에 있음 - 먼저 version.json 시도
        val versionJsonUrl = "https://maven.minecraftforge.net/net/minecraftforge/forge/$mcVersion-$forgeVersion/forge-$mcVersion-$forgeVersion-universal.json"

        return try {
            val request = Request.Builder().url(versionJsonUrl).build()
            client.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    val json = response.body?.string() ?: return null
                    gson.fromJson(json, ForgeVersionManifest::class.java)
                } else {
                    // fallback: installer jar에서 추출
                    fetchForgeManifestFromInstaller(mcVersion, forgeVersion)
                }
            }
        } catch (e: Exception) {
            Log.e("PING_LAUNCHER", "Forge manifest 가져오기 실패: ${e.message}")
            fetchForgeManifestFromInstaller(mcVersion, forgeVersion)
        }
    }

    private fun fetchForgeManifestFromInstaller(mcVersion: String, forgeVersion: String): ForgeVersionManifest? {
        val installerUrl = "https://maven.minecraftforge.net/net/minecraftforge/forge/$mcVersion-$forgeVersion/forge-$mcVersion-$forgeVersion-installer.jar"
        val tempFile = File(gameDir, "temp/forge-installer.jar")
        tempFile.parentFile?.mkdirs()

        return try {
            onProgress("Forge installer 다운로드 중...")
            val request = Request.Builder().url(installerUrl).build()
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return null
                response.body?.byteStream()?.use { input ->
                    FileOutputStream(tempFile).use { input.copyTo(it) }
                }
            }

            // installer jar에서 version.json 추출
            ZipFile(tempFile).use { zip ->
                val entry = zip.getEntry("version.json") ?: zip.getEntry("install_profile.json") ?: return null
                val json = zip.getInputStream(entry).bufferedReader().readText()
                gson.fromJson(json, ForgeVersionManifest::class.java)
            }
        } catch (e: Exception) {
            Log.e("PING_LAUNCHER", "Forge installer 처리 실패: ${e.message}")
            null
        } finally {
            tempFile.delete()
        }
    }

    private fun downloadForgeLibrary(lib: ForgeLibrary, librariesDir: File): File? {
        val path = mavenNameToPath(lib.name)
        val destFile = File(librariesDir, path)

        if (destFile.exists() && destFile.length() > 0) return destFile
        destFile.parentFile?.mkdirs()

        // URL 결정
        val url = lib.downloads?.artifact?.url
            ?: lib.url?.let { base ->
                if (base.endsWith("/")) "$base$path" else "$base/$path"
            }
            ?: "https://maven.minecraftforge.net/$path"

        return try {
            val request = Request.Builder().url(url).build()
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    // fallback to libraries.minecraft.net
                    val fallbackUrl = "https://libraries.minecraft.net/$path"
                    val fallbackReq = Request.Builder().url(fallbackUrl).build()
                    client.newCall(fallbackReq).execute().use { resp ->
                        if (resp.isSuccessful) {
                            resp.body?.byteStream()?.use { input ->
                                FileOutputStream(destFile).use { input.copyTo(it) }
                            }
                        }
                    }
                } else {
                    response.body?.byteStream()?.use { input ->
                        FileOutputStream(destFile).use { input.copyTo(it) }
                    }
                }
            }
            if (destFile.exists() && destFile.length() > 0) destFile else null
        } catch (e: Exception) {
            null
        }
    }

    private fun downloadForgeJar(mcVersion: String, forgeVersion: String, destFile: File) {
        destFile.parentFile?.mkdirs()
        val urls = listOf(
            "https://maven.minecraftforge.net/net/minecraftforge/forge/$mcVersion-$forgeVersion/forge-$mcVersion-$forgeVersion-universal.jar",
            "https://maven.minecraftforge.net/net/minecraftforge/forge/$mcVersion-$forgeVersion/forge-$mcVersion-$forgeVersion.jar"
        )
        for (url in urls) {
            try {
                val request = Request.Builder().url(url).build()
                client.newCall(request).execute().use { response ->
                    if (response.isSuccessful) {
                        response.body?.byteStream()?.use { input ->
                            FileOutputStream(destFile).use { input.copyTo(it) }
                        }
                        return
                    }
                }
            } catch (_: Exception) {}
        }
    }

    private fun mavenNameToPath(name: String): String {
        // "net.minecraftforge:forge:1.20.1-47.2.0" → "net/minecraftforge/forge/1.20.1-47.2.0/forge-1.20.1-47.2.0.jar"
        val parts = name.split(":")
        if (parts.size < 3) return name
        val group = parts[0].replace('.', '/')
        val artifact = parts[1]
        val versionFull = parts[2]
        val classifier = if (parts.size > 3) "-${parts[3]}" else ""
        return "$group/$artifact/$versionFull/$artifact-$versionFull$classifier.jar"
    }
}

data class ForgeInstallResult(
    val success: Boolean,
    val mainClass: String = "net.minecraft.client.main.Main",
    val extraJars: List<String> = emptyList(),
    val mcVersion: String = "",
    val forgeVersion: String = "",
    val error: String? = null
)
