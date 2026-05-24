package kr.co.donghyun.pinglauncher.presentation.ui.screen

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kr.co.donghyun.pinglauncher.data.jvm.JvmSettingsManager

@Composable
fun JvmSettingsScreen(onBack: () -> Unit) {
    val context = LocalContext.current

    // 기기 실제 RAM 계산
    val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as android.app.ActivityManager
    val memInfo = android.app.ActivityManager.MemoryInfo()
    activityManager.getMemoryInfo(memInfo)
    val totalRamMb = (memInfo.totalMem / 1024 / 1024).toInt()
    val maxHeapMb = (totalRamMb / 256) * 256  // 256MB 단위로 내림
    val defaultHeapMb = maxHeapMb / 2

    var settings by remember {
        val loaded = JvmSettingsManager.load(context)
        // 처음 설치면 기기 RAM 기반으로 기본값 설정
        val adjusted = if (loaded.maxHeapMb == 2048) {
            loaded.copy(maxHeapMb = defaultHeapMb, minHeapMb = defaultHeapMb / 4)
        } else loaded
        mutableStateOf(adjusted)
    }
    var saved by remember { mutableStateOf(false) }

    val Pink = Color(0xFFE91E8C)
    val BgDark = Color(0xFF120B10)
    val BgSurface = Color(0xFF1E0E1A)
    val BgBorder = Color(0xFF3D1A32)
    val TextMain = Color(0xFFFCE4EC)
    val TextSub = Color(0xFFBB86A0)

    // 총 RAM 표시 (MB)
    val totalRam = Runtime.getRuntime().totalMemory() / 1024 / 1024
    val maxRam = Runtime.getRuntime().maxMemory() / 1024 / 1024

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(BgDark)
            .systemBarsPadding()
    ) {
        // 툴바
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(BgSurface)
                .border(1.dp, BgBorder, RoundedCornerShape(0.dp))
                .padding(horizontal = 12.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            TextButton(onClick = onBack) {
                Text("뒤로", color = TextSub, fontSize = 14.sp)
            }
            Text("JVM 설정", color = TextMain, fontSize = 16.sp, fontWeight = FontWeight.Bold)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = {
                    settings = JvmSettingsManager.reset(context)
                    saved = false
                }) {
                    Text("초기화", color = Color(0xFFFF6B6B), fontSize = 13.sp)
                }
                Button(
                    onClick = {
                        JvmSettingsManager.save(context, settings)
                        saved = true
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Pink),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text("저장", color = Color.White, fontSize = 13.sp)
                }
            }
        }

        if (saved) {
            Text(
                "✅ 저장됨 — 다음 실행부터 적용됩니다",
                color = Color(0xFF69DB7C),
                fontSize = 12.sp,
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFF0A2010))
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            )
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // ── 메모리 ──────────────────────────────
            SettingsSection(title = "🧠 메모리", borderColor = Pink, bgColor = BgSurface, borderBg = BgBorder) {
                Text("기기 RAM: ${totalRamMb}MB / 권장 최대: ${defaultHeapMb}MB", color = TextSub, fontSize = 11.sp)
                Spacer(modifier = Modifier.height(8.dp))

                SliderSetting(
                    label = "최대 힙 (Xmx)",
                    value = settings.maxHeapMb.toFloat(),
                    range = 512f..maxHeapMb.toFloat(),  // ← 기기 RAM까지
                    step = 256f,
                    unit = "MB",
                    color = Pink,
                    textColor = TextMain,
                    subColor = TextSub,
                    onValueChange = { settings = settings.copy(maxHeapMb = it.toInt()) }
                )
                SliderSetting(
                    label = "최소 힙 (Xms)",
                    value = settings.minHeapMb.toFloat(),
                    range = 256f..(maxHeapMb / 2).toFloat(),  // ← 절반까지
                    step = 128f,
                    unit = "MB",
                    color = Pink,
                    textColor = TextMain,
                    subColor = TextSub,
                    onValueChange = { settings = settings.copy(minHeapMb = it.toInt()) }
                )
            }

            // ── GC 설정 ──────────────────────────────
            SettingsSection(title = "⚙️ GC 설정", borderColor = Pink, bgColor = BgSurface, borderBg = BgBorder) {
                SwitchSetting(
                    label = "G1GC 사용",
                    desc = "대용량 힙에 적합한 GC",
                    checked = settings.useG1GC,
                    color = Pink,
                    textColor = TextMain,
                    subColor = TextSub,
                    onCheckedChange = { settings = settings.copy(useG1GC = it) }
                )
                if (settings.useG1GC) {
                    Spacer(modifier = Modifier.height(4.dp))
                    SliderSetting(
                        label = "최대 GC 일시정지",
                        value = settings.gcPauseMillis.toFloat(),
                        range = 50f..500f,
                        step = 50f,
                        unit = "ms",
                        color = Pink,
                        textColor = TextMain,
                        subColor = TextSub,
                        onValueChange = { settings = settings.copy(gcPauseMillis = it.toInt()) }
                    )
                    SliderSetting(
                        label = "힙 리전 크기",
                        value = settings.heapRegionSizeMb.toFloat(),
                        range = 4f..64f,
                        step = 4f,
                        unit = "MB",
                        color = Pink,
                        textColor = TextMain,
                        subColor = TextSub,
                        onValueChange = { settings = settings.copy(heapRegionSizeMb = it.toInt()) }
                    )
                    SwitchSetting(
                        label = "병렬 레퍼런스 처리",
                        desc = "GC 성능 향상",
                        checked = settings.parallelRefProc,
                        color = Pink,
                        textColor = TextMain,
                        subColor = TextSub,
                        onCheckedChange = { settings = settings.copy(parallelRefProc = it) }
                    )
                }
            }

            // ── 그래픽 ──────────────────────────────
            SettingsSection(title = "🎮 그래픽", borderColor = Pink, bgColor = BgSurface, borderBg = BgBorder) {
                SwitchSetting(
                    label = "구름 비활성화",
                    desc = "렌더링 오류 방지",
                    checked = settings.disableClouds,
                    color = Pink,
                    textColor = TextMain,
                    subColor = TextSub,
                    onCheckedChange = { settings = settings.copy(disableClouds = it) }
                )
                Spacer(modifier = Modifier.height(8.dp))
                SliderSetting(
                    label = "렌더 거리",
                    value = settings.renderDistance.toFloat(),
                    range = 2f..16f,
                    step = 1f,
                    unit = "청크",
                    color = Pink,
                    textColor = TextMain,
                    subColor = TextSub,
                    onValueChange = { settings = settings.copy(renderDistance = it.toInt()) }
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text("그래픽 품질", color = TextMain, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                Spacer(modifier = Modifier.height(4.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("빠름" to 0, "기본" to 1, "화려" to 2).forEach { (label, value) ->
                        FilterChip(
                            selected = settings.graphicsMode == value,
                            onClick = { settings = settings.copy(graphicsMode = value) },
                            label = { Text(label, fontSize = 12.sp) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = Color(0xFF9C1060),
                                containerColor = BgDark,
                                selectedLabelColor = Color.White,
                                labelColor = TextSub
                            ),
                            border = FilterChipDefaults.filterChipBorder(
                                enabled = true,
                                selected = settings.graphicsMode == value,
                                borderColor = BgBorder,
                                selectedBorderColor = Pink
                            )
                        )
                    }
                }
            }

            // ── 마우스 ──────────────────────────────
            SettingsSection(title = "🖱 마우스", borderColor = Pink, bgColor = BgSurface, borderBg = BgBorder) {
                SliderSetting(
                    label = "감도",
                    value = settings.mouseSensitivity,
                    range = 0.5f..5f,
                    step = 0.5f,
                    unit = "x",
                    color = Pink,
                    textColor = TextMain,
                    subColor = TextSub,
                    onValueChange = { settings = settings.copy(mouseSensitivity = it) }
                )
            }

            // ── 커스텀 JVM 인자 ──────────────────────
            SettingsSection(title = "🔧 커스텀 JVM 인자", borderColor = Pink, bgColor = BgSurface, borderBg = BgBorder) {
                Text("한 줄에 하나씩 입력", color = TextSub, fontSize = 11.sp)
                Spacer(modifier = Modifier.height(8.dp))
                BasicTextField(
                    value = settings.extraJvmArgs,
                    onValueChange = { settings = settings.copy(extraJvmArgs = it) },
                    textStyle = TextStyle(
                        color = TextMain,
                        fontSize = 12.sp,
                        fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace
                    ),
                    cursorBrush = SolidColor(Pink),
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 100.dp, max = 200.dp)
                        .background(BgDark, RoundedCornerShape(8.dp))
                        .border(1.dp, BgBorder, RoundedCornerShape(8.dp))
                        .padding(12.dp),
                    decorationBox = { inner ->
                        if (settings.extraJvmArgs.isEmpty()) {
                            Text(
                                "-Dfoo=bar\n-Xverify:none",
                                color = TextSub.copy(alpha = 0.5f),
                                fontSize = 12.sp,
                                fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace
                            )
                        }
                        inner()
                    }
                )
            }

            // 현재 적용될 인자 미리보기
            SettingsSection(title = "📋 미리보기", borderColor = BgBorder, bgColor = BgDark, borderBg = BgBorder) {
                val preview = buildString {
                    appendLine("-Xmx${settings.maxHeapMb}M")
                    appendLine("-Xms${settings.minHeapMb}M")
                    if (settings.useG1GC) {
                        appendLine("-XX:+UseG1GC")
                        appendLine("-XX:MaxGCPauseMillis=${settings.gcPauseMillis}")
                        appendLine("-XX:G1HeapRegionSize=${settings.heapRegionSizeMb}m")
                    }
                    if (settings.disableClouds) appendLine("-Dminecraft.graphics.disableClouds=true")
                    if (settings.extraJvmArgs.isNotEmpty()) appendLine(settings.extraJvmArgs)
                }
                Text(
                    text = preview.trim(),
                    color = TextSub,
                    fontSize = 11.sp,
                    fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color(0xFF0D0A0F), RoundedCornerShape(6.dp))
                        .padding(10.dp)
                )
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

@Composable
fun SettingsSection(
    title: String,
    borderColor: Color,
    bgColor: Color,
    borderBg: Color,
    content: @Composable ColumnScope.() -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(bgColor, RoundedCornerShape(12.dp))
            .border(1.dp, borderBg, RoundedCornerShape(12.dp))
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(title, color = borderColor, fontSize = 13.sp, fontWeight = FontWeight.Bold)
        content()
    }
}

@Composable
fun SliderSetting(
    label: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    step: Float,
    unit: String,
    color: Color,
    textColor: Color,
    subColor: Color,
    onValueChange: (Float) -> Unit
) {
    val steps = ((range.endInclusive - range.start) / step).toInt() - 1
    Column {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(label, color = textColor, fontSize = 13.sp)
            Text(
                if (value == value.toInt().toFloat()) "${value.toInt()}$unit" else "${"%.1f".format(value)}$unit",
                color = color,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold
            )
        }
        Slider(
            value = value,
            onValueChange = onValueChange,
            valueRange = range,
            steps = steps,
            colors = SliderDefaults.colors(
                thumbColor = color,
                activeTrackColor = color,
                inactiveTrackColor = subColor.copy(alpha = 0.3f)
            ),
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@Composable
fun SwitchSetting(
    label: String,
    desc: String,
    checked: Boolean,
    color: Color,
    textColor: Color,
    subColor: Color,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(label, color = textColor, fontSize = 13.sp)
            if (desc.isNotEmpty()) Text(desc, color = subColor, fontSize = 11.sp)
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color.White,
                checkedTrackColor = color,
                uncheckedThumbColor = subColor,
                uncheckedTrackColor = subColor.copy(alpha = 0.3f)
            )
        )
    }
}