package kr.co.donghyun.pinglauncher.presentation


import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kr.co.donghyun.pinglauncher.presentation.base.BaseActivity
import kr.co.donghyun.pinglauncher.presentation.ui.screen.CrashReportScreen
import kr.co.donghyun.pinglauncher.presentation.ui.theme.*
import java.io.File

data class ModEntry(
    val fileName: String,
    val modId: String,
    val enabled: Boolean,
    val isSuspected: Boolean
)

class CrashReportActivity : BaseActivity() {

    private val _mods = MutableStateFlow<List<ModEntry>>(emptyList())
    private val _crashSummary = MutableStateFlow("")
    private val _isLoading = MutableStateFlow(true)

    companion object {
        private const val EXTRA_INSTANCE_DIR = "instance_dir"

        fun start(context: Context, instanceDir: String) {
            context.startActivity(
                Intent(context, CrashReportActivity::class.java).apply {
                    putExtra(EXTRA_INSTANCE_DIR, instanceDir)
                }
            )
        }
    }

    override fun onCreated() {
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(scrim = android.graphics.Color.TRANSPARENT)
        )

        val instanceDir = intent.getStringExtra(EXTRA_INSTANCE_DIR) ?: run { finish(); return }
        val instanceDirFile = File(instanceDir)

        setContent {
            PingLauncherTheme {
                val mods by _mods.asStateFlow().collectAsState()
                val crashSummary by _crashSummary.asStateFlow().collectAsState()
                val isLoading by _isLoading.asStateFlow().collectAsState()

                CrashReportScreen(
                    mods = mods,
                    crashSummary = crashSummary,
                    isLoading = isLoading,
                    onToggleMod = { mod -> toggleMod(instanceDirFile, mod) },
                    onBack = { finish() },
                    onRelaunch = {
                        setResult(RESULT_OK)
                        finish()
                    }
                )
            }
        }

        lifecycleScope.launch(Dispatchers.IO) {
            loadData(instanceDirFile)
        }
    }

    private suspend fun loadData(instanceDir: File) {
        // 최신 크래시 리포트 찾기
        val crashDir = File(instanceDir, "crash-reports")
        val latestCrash = crashDir.listFiles()
            ?.filter { it.extension == "txt" }
            ?.maxByOrNull { it.lastModified() }

        val suspectedMods = mutableSetOf<String>()
        var summary = ""

        latestCrash?.let { crash ->
            val content = crash.readText()
            summary = parseCrashSummary(content)
            suspectedMods.addAll(parseSuspectedMods(content))
        }

        _crashSummary.value = summary

        // mods 폴더에서 모드 목록 로드
        val modsDir = File(instanceDir, "mods")
        val modEntries = modsDir.listFiles()
            ?.filter { it.extension == "jar" || it.name.endsWith(".jar.disabled") }
            ?.map { file ->
                val enabled = file.extension == "jar"
                val baseName = if (enabled) file.nameWithoutExtension
                else file.name.removeSuffix(".disabled").removeSuffix(".jar")
                val modId = extractModId(baseName)
                val isSuspected = suspectedMods.any {
                    modId.contains(it, ignoreCase = true) ||
                            baseName.contains(it, ignoreCase = true)
                }
                ModEntry(
                    fileName = file.name,
                    modId = modId,
                    enabled = enabled,
                    isSuspected = isSuspected
                )
            }
            ?.sortedWith(compareByDescending<ModEntry> { it.isSuspected }.thenBy { it.modId })
            ?: emptyList()

        _mods.value = modEntries
        _isLoading.value = false
    }

    private fun toggleMod(instanceDir: File, mod: ModEntry) {
        val modsDir = File(instanceDir, "mods")
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                if (mod.enabled) {
                    // 비활성화
                    val src = File(modsDir, mod.fileName)
                    val dst = File(modsDir, "${mod.fileName}.disabled")
                    src.renameTo(dst)
                } else {
                    // 활성화
                    val src = File(modsDir, mod.fileName)
                    val dst = File(modsDir, mod.fileName.removeSuffix(".disabled"))
                    src.renameTo(dst)
                }

                // 목록 갱신
                loadData(instanceDir)
            } catch (e: Exception) {
                Log.e("PING_LAUNCHER", "모드 토글 실패: ${e.message}")
            }
        }
    }

    private fun parseCrashSummary(content: String): String {
        // Description 줄 찾기
        val descLine = content.lines().find { it.startsWith("Description:") }
            ?: return ""
        val desc = descLine.removePrefix("Description:").trim()

        // 첫 번째 예외 줄 찾기
        val exceptionLine = content.lines()
            .dropWhile { !it.startsWith("Description:") }
            .drop(1)
            .firstOrNull { it.contains("Exception") || it.contains("Error") }
            ?.trim() ?: ""

        return if (exceptionLine.isNotEmpty()) "$desc\n$exceptionLine" else desc
    }

    private fun parseSuspectedMods(content: String): Set<String> {
        val suspected = mutableSetOf<String>()

        // "Suspected Mods:" 줄 파싱
        val suspectedLine = content.lines().find { it.contains("Suspected Mods:") } ?: ""
        if (suspectedLine.isNotEmpty()) {
            val modsStr = suspectedLine.substringAfter("Suspected Mods:").trim()
            // "ModName (modid)" 패턴에서 modid 추출
            Regex("\\(([^)]+)\\)").findAll(modsStr).forEach { match ->
                suspected.add(match.groupValues[1])
            }
        }

        // "provided by 'modid'" 패턴
        Regex("provided by '([^']+)'").findAll(content).forEach { match ->
            suspected.add(match.groupValues[1])
        }

        // "mod 'modname' (modid)" 패턴
        Regex("'([^']+)' \\(([^)]+)\\)").findAll(content).forEach { match ->
            suspected.add(match.groupValues[2])
        }

        return suspected
    }

    private fun extractModId(fileName: String): String {
        // "modid-1.2.3+mc1.20.1-fabric" → "modid"
        return fileName
            .replace(Regex("-\\d+\\..*"), "")
            .replace(Regex("_fabric.*"), "")
            .replace(Regex("-fabric.*"), "")
            .replace(Regex("_MC_.*"), "")
            .lowercase()
    }
}