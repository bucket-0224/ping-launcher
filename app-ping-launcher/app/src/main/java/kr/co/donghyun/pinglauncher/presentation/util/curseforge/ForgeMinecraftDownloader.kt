package kr.co.donghyun.pinglauncher.presentation.util.curseforge

import android.util.Log
import com.google.gson.Gson
import kr.co.donghyun.pinglauncher.data.mojang.DownloadPhase
import kr.co.donghyun.pinglauncher.data.mojang.DownloadProgress
import kr.co.donghyun.pinglauncher.data.mojang.VersionManifest
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream

// MinecraftDownloader의 새 버전 - URL과 ID를 직접 받음
class ForgeMinecraftDownloader(
    private val gameDir: File,
    private val versionUrl: String,
    private val versionId: String,
    private val onProgress: (DownloadProgress) -> Unit
) {
    private val client = OkHttpClient()
    private val gson = Gson()

    fun prepare(): String {
        Log.d("PING_LAUNCHER", "ForgeDownloader gameDir: ${gameDir.absolutePath}")

        onProgress(DownloadProgress(phase = DownloadPhase.FETCHING_MANIFEST))
        val manifest = fetchManifest(versionUrl)

        onProgress(DownloadProgress(phase = DownloadPhase.DOWNLOADING_CLIENT, fileName = "$versionId.jar"))
        val clientJar = File(gameDir, "versions/$versionId/$versionId.jar")
        downloadFile(manifest.downloads.client.url, clientJar, manifest.downloads.client.sha1)

        val assetIndexFile = File(gameDir, "assets/indexes/${manifest.assetIndex.id}.json")
        downloadFile(manifest.assetIndex.url, assetIndexFile, null)

        Log.d("PING_LAUNCHER", "assetIndexFile: ${assetIndexFile.absolutePath}")

        val librariesDir = File(gameDir, "libraries_$versionId")
        val artifacts = manifest.libraries.mapNotNull { lib -> lib.downloads.artifact?.let { lib to it } }

        artifacts.forEachIndexed { index, (lib, artifact) ->
            val path = getLibraryPath(lib.name)
            val libFile = File(librariesDir, path)
            onProgress(DownloadProgress(
                phase = DownloadPhase.DOWNLOADING_LIBRARIES,
                current = index + 1,
                total = artifacts.size,
                fileName = libFile.name
            ))
            downloadFile(artifact.url, libFile, artifact.sha1)
        }

        downloadAssets(assetIndexFile)
        Log.d("PING_LAUNCHER", "✅ MC $versionId 준비 완료")
        return manifest.assetIndex.id
    }

    private fun fetchManifest(url: String): VersionManifest {
        val request = Request.Builder().url(url).build()
        client.newCall(request).execute().use { response ->
            val json = response.body?.string() ?: throw Exception("버전 JSON 읽기 실패")
            return gson.fromJson(json, VersionManifest::class.java)
        }
    }

    private fun downloadFile(url: String, destFile: File, expectedSha1: String?) {
        if (destFile.exists() && destFile.length() > 0) return
        destFile.parentFile?.mkdirs()
        val request = Request.Builder().url(url).build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw Exception("다운로드 실패: $url")
            response.body?.byteStream()?.use { input ->
                FileOutputStream(destFile).use { input.copyTo(it) }
            }
        }
    }

    private fun downloadAssets(assetIndexFile: File) {
        if (!assetIndexFile.exists()) return
        val json = assetIndexFile.readText()
        val objects = com.google.gson.JsonParser.parseString(json).asJsonObject["objects"].asJsonObject
        val assetsDir = File(gameDir, "assets/objects")
        val entries = objects.entrySet().toList()
        val total = entries.size
        var downloaded = 0

        entries.forEach { (_, value) ->
            val hash = value.asJsonObject["hash"].asString
            val prefix = hash.substring(0, 2)
            val destFile = File(assetsDir, "$prefix/$hash")
            downloaded++
            onProgress(DownloadProgress(
                phase = DownloadPhase.DOWNLOADING_ASSETS,
                current = downloaded,
                total = total,
                fileName = hash.take(12) + "..."
            ))
            if (destFile.exists() && destFile.length() > 0) return@forEach
            destFile.parentFile?.mkdirs()
            try {
                val url = "https://resources.download.minecraft.net/$prefix/$hash"
                val request = Request.Builder().url(url).build()
                client.newCall(request).execute().use { response ->
                    if (response.isSuccessful) {
                        response.body?.byteStream()?.use { input ->
                            FileOutputStream(destFile).use { input.copyTo(it) }
                        }
                    }
                }
            } catch (_: Exception) {}
        }
    }

    private fun getLibraryPath(name: String): String {
        val parts = name.split(":")
        val basePath = "${parts[0].replace('.', '/')}/${parts[1]}/${parts[2]}/${parts[1]}-${parts[2]}"
        return "$basePath.jar"
    }
}