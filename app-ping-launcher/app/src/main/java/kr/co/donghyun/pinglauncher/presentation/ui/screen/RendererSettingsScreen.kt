package kr.co.donghyun.pinglauncher.presentation.ui.screen


import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kr.co.donghyun.pinglauncher.data.renderer.Renderer
import kr.co.donghyun.pinglauncher.data.renderer.RendererManager
import kr.co.donghyun.pinglauncher.presentation.ui.theme.*

private val Pink = Color(0xFFE91E8C)
private val TextMain = Color(0xFFFCE4EC)
private val TextSub = Color(0xFFBB86A0)

@Composable
fun RendererSettingsScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    var selected by remember { mutableStateOf(RendererManager.load(context)) }
    var saved by remember { mutableStateOf(false) }

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
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            TextButton(onClick = onBack) {
                Text("← 뒤로", color = TextSub, fontSize = 14.sp)
            }
            Text("🎨 렌더러 설정", color = TextMain, fontSize = 16.sp, fontWeight = FontWeight.Bold)
            Button(
                onClick = {
                    RendererManager.save(context, selected)
                    saved = true
                },
                colors = ButtonDefaults.buttonColors(containerColor = Pink),
                shape = RoundedCornerShape(8.dp),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
            ) {
                Text("저장", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold)
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
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                "마인크래프트를 그릴 그래픽 백엔드를 고르세요. " +
                        "기기 GPU·버전·모드에 따라 적합한 렌더러가 달라요.",
                color = TextSub,
                fontSize = 12.sp,
                lineHeight = 18.sp
            )
            Spacer(modifier = Modifier.height(4.dp))

            Renderer.entries.forEach { renderer ->
                RendererCard(
                    renderer = renderer,
                    isSelected = selected == renderer,
                    onClick = {
                        selected = renderer
                        saved = false
                    }
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // 디버그용 env 미리보기
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(BgSurface, RoundedCornerShape(8.dp))
                    .border(1.dp, BgBorder, RoundedCornerShape(8.dp))
                    .padding(12.dp)
            ) {
                Text("📋 환경변수 미리보기 (${selected.displayName})", color = Pink, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(6.dp))
                val env = selected.buildEnv(
                    cacheDir = context.cacheDir.absolutePath,
                    nativeDir = context.applicationInfo.nativeLibraryDir
                )
                env.forEach { (k, v) ->
                    Text("$k=$v", color = TextSub, fontSize = 10.sp)
                }
            }
        }
    }
}

@Composable
private fun RendererCard(
    renderer: Renderer,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(if (isSelected) Color(0xFF2D0A20) else BgSurface)
            .border(
                width = if (isSelected) 2.dp else 1.dp,
                color = if (isSelected) Pink else BgBorder,
                shape = RoundedCornerShape(12.dp)
            )
            .clickable(onClick = onClick)
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(renderer.emoji, fontSize = 22.sp)
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    renderer.displayName,
                    color = if (isSelected) Pink else TextMain,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold
                )
            }
            if (isSelected) {
                Text("✓", color = Pink, fontSize = 18.sp, fontWeight = FontWeight.Bold)
            }
        }
        Text(renderer.description, color = TextSub, fontSize = 12.sp, lineHeight = 16.sp)
    }
}