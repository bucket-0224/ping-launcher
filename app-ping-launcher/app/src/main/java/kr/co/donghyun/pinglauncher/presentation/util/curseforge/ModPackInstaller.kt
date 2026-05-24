package kr.co.donghyun.pinglauncher.presentation.util.curseforge

import android.util.Log
import com.google.gson.Gson
import kr.co.donghyun.pinglauncher.data.curseforge.CurseForgeManifest
import kr.co.donghyun.pinglauncher.data.curseforge.CurseForgeMod
import kr.co.donghyun.pinglauncher.data.curseforge.InstalledModPackCache
import kr.co.donghyun.pinglauncher.data.mojang.DownloadPhase
import kr.co.donghyun.pinglauncher.data.mojang.DownloadProgress
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.util.zip.ZipFile

data class ModPackInstallResult(
    val success: Boolean,
    val mcVersion: String = "",
    val forgeId: String? = null,       // e.g. "forge-1.20.1-47.2.0"
    val gameDir: File? = null,
    val error: String? = null
)

class ModPackInstaller(
    private val baseDir: File,
    private val curseForgeApi: CurseForgeAPI,
    private val onProgress: (DownloadProgress) -> Unit
) {
    private val client = OkHttpClient()
    private val gson = Gson()

    // 알려진 비호환 모드 목록
    private val INCOMPATIBLE_MODS = listOf(
        "sodium",
        "iris",
        "reeses-sodium",
        "sodium-extra",
        "sodiumoptionsapi",
        "xaerominimap",
        "xaeroworldmap",
        "immediatelyfast",
        "indium",
        "euphoriapatcher",
        "colorwheel",
        "colorwheel_patcher",
        "friendsforlife",
        "fabricskyboxes",
        "fsb-interop",
        "fsb_interop",
        "create-fabric",
        "create_oxidized",
        "createcontraptionterminals",
        "createdeco",
        "ponder",
        "catnip",
        "copycats",
        "sliceanddice",
        "railways",
        "steam_rails",
        "Steam_Rails",
        "particlerain",
        "aaa_particles",
    )

    fun install(mod: CurseForgeMod, fileId: Int): ModPackInstallResult {
        val gameDir = baseDir

        val existingManifest = File(gameDir, "manifest_cache.json")
        if (gameDir.exists() && existingManifest.exists()) {
            return try {
                val cached = gson.fromJson(existingManifest.readText(), InstalledModPackCache::class.java)
                Log.d("PING_LAUNCHER", "✅ 캐시에서 로드: ${mod.name}, mc=${cached.mcVersion}")
                ModPackInstallResult(
                    success = true,
                    mcVersion = cached.mcVersion,
                    forgeId = cached.forgeId,
                    gameDir = gameDir
                )
            } catch (e: Exception) {
                Log.w("PING_LAUNCHER", "캐시 파싱 실패, 재설치: ${e.message}")
                existingManifest.delete()
                null  // null 반환하면 아래 설치 로직 실행 안 됨
            } ?: return ModPackInstallResult(success = false, error = "재설치 필요")
        }


        onProgress(DownloadProgress(phase = DownloadPhase.FETCHING_MANIFEST, fileName = mod.name))

        // 1. 모드팩 zip 다운로드
        val downloadUrl = curseForgeApi.getFileDownloadUrl(mod.id, fileId)
            ?: return ModPackInstallResult(success = false, error = "다운로드 URL을 가져올 수 없음")

        val modpackZip = File(baseDir, "temp/modpack_${mod.id}_${fileId}.zip")
        modpackZip.parentFile?.mkdirs()

        onProgress(DownloadProgress(phase = DownloadPhase.DOWNLOADING_CLIENT, fileName = "${mod.name}.zip"))
        downloadFile(downloadUrl, modpackZip)

        // 2. manifest.json 파싱
        val manifest = extractManifest(modpackZip)
            ?: return ModPackInstallResult(success = false, error = "manifest.json을 찾을 수 없음")

        val mcVersion = manifest.minecraft.version
        val forgeLoader = manifest.minecraft.modLoaders.firstOrNull { it.primary }
        val forgeId = forgeLoader?.id // e.g. "forge-47.2.0"

        // 4. overrides 폴더 추출
        onProgress(DownloadProgress(phase = DownloadPhase.DOWNLOADING_LIBRARIES, fileName = "파일 추출 중..."))
        extractOverrides(modpackZip, gameDir, manifest.overrides)

        // 5. 모드 파일들 다운로드
        val totalMods = manifest.files.filter { it.required }.size
        var downloadedMods = 0
        val modsDir = File(gameDir, "mods").also { it.mkdirs() }

        val requiredFiles = manifest.files.filter { it.required }
        // 배치로 파일 정보 가져오기
        val fileIds = requiredFiles.map { it.fileID }
        val fileInfoMap = try {
            curseForgeApi.getFiles(fileIds).associateBy { it.id }
        } catch (e: Exception) {
            emptyMap()
        }

        requiredFiles.forEach { manifestFile ->
            downloadedMods++
            onProgress(DownloadProgress(
                phase = DownloadPhase.DOWNLOADING_ASSETS,
                current = downloadedMods,
                total = totalMods,
                fileName = "모드 다운로드 중..."
            ))

            try {
                val fileInfo = fileInfoMap[manifestFile.fileID]
                val modUrl = fileInfo?.downloadUrl
                    ?: curseForgeApi.getFileDownloadUrl(manifestFile.projectID, manifestFile.fileID)
                    ?: return@forEach

                val fileName = fileInfo?.fileName ?: "mod_${manifestFile.fileID}.jar"
                val destFile = File(modsDir, fileName)

                if (!destFile.exists() || destFile.length() == 0L) {
                    downloadFile(modUrl, destFile)
                }
            } catch (e: Exception) {
                Log.w("PING_LAUNCHER", "모드 다운로드 실패: ${manifestFile.fileID} - ${e.message}")
            }
        }

        // 정리
        modpackZip.delete()

        Log.d("PING_LAUNCHER", "✅ 모드팩 설치 완료: ${mod.name}, MC $mcVersion, Forge: $forgeId")

        // 비호환 모드 자동 비활성화
        disableIncompatibleMods(modsDir)

        return ModPackInstallResult(
            success = true,
            mcVersion = mcVersion,
            forgeId = forgeId?.let { "forge-$mcVersion-${it.removePrefix("forge-")}" },
            gameDir = gameDir
        )
    }

    private fun disableIncompatibleMods(modsDir: File) {
        if (!modsDir.exists()) return
        modsDir.listFiles()?.forEach { file ->
            if (file.extension == "jar") {
                val fileName = file.name.lowercase()
                val isIncompatible = INCOMPATIBLE_MODS.any { mod ->
                    fileName.contains(mod)
                }
                if (isIncompatible) {
                    val disabled = File(file.parent, "${file.name}.disabled")
                    file.renameTo(disabled)
                    Log.d("PING_LAUNCHER", "⚠ 비호환 모드 비활성화: ${file.name}")
                }
            }
        }
    }

    private fun extractManifest(zipFile: File): CurseForgeManifest? {
        return try {
            ZipFile(zipFile).use { zip ->
                val entry = zip.getEntry("manifest.json") ?: return null
                val json = zip.getInputStream(entry).bufferedReader().readText()
                gson.fromJson(json, CurseForgeManifest::class.java)
            }
        } catch (e: Exception) {
            Log.e("PING_LAUNCHER", "manifest.json 파싱 실패: ${e.message}")
            null
        }
    }

    private fun extractOverrides(zipFile: File, gameDir: File, overridesFolder: String) {
        try {
            ZipFile(zipFile).use { zip ->
                zip.entries().asSequence()
                    .filter { it.name.startsWith("$overridesFolder/") && !it.isDirectory }
                    .forEach { entry ->
                        val relativePath = entry.name.removePrefix("$overridesFolder/")
                        val destFile = File(gameDir, relativePath)
                        destFile.parentFile?.mkdirs()
                        zip.getInputStream(entry).use { input ->
                            FileOutputStream(destFile).use { input.copyTo(it) }
                        }
                    }
            }
        } catch (e: Exception) {
            Log.e("PING_LAUNCHER", "overrides 추출 실패: ${e.message}")
        }
    }

    private fun downloadFile(url: String, destFile: File) {
        if (destFile.exists() && destFile.length() > 0) return
        destFile.parentFile?.mkdirs()
        val request = Request.Builder().url(url).build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw Exception("다운로드 실패: $url (${response.code})")
            response.body?.byteStream()?.use { input ->
                FileOutputStream(destFile).use { input.copyTo(it) }
            }
        }
    }
}