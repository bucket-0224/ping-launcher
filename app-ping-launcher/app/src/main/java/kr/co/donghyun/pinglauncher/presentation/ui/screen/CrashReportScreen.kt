package kr.co.donghyun.pinglauncher.presentation.ui.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kr.co.donghyun.pinglauncher.presentation.ModEntry
import kr.co.donghyun.pinglauncher.presentation.ui.theme.BgBorder
import kr.co.donghyun.pinglauncher.presentation.ui.theme.BgDark
import kr.co.donghyun.pinglauncher.presentation.ui.theme.BgSurface


@Composable
fun CrashReportScreen(
    mods: List<ModEntry>,
    crashSummary: String,
    isLoading: Boolean,
    onToggleMod: (ModEntry) -> Unit,
    onBack: () -> Unit,
    onRelaunch: () -> Unit
) {
    val Pink = Color(0xFFE91E8C)
    val TextMain = Color(0xFFFCE4EC)
    val TextSub = Color(0xFFBB86A0)
    val Red = Color(0xFFFF6B6B)

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
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            TextButton(onClick = onBack) {
                Text("← 뒤로", color = TextSub, fontSize = 14.sp)
            }
            Text(
                "모드 관리",
                color = TextMain,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold
            )
            Button(
                onClick = onRelaunch,
                colors = ButtonDefaults.buttonColors(containerColor = Pink),
                shape = RoundedCornerShape(8.dp),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
            ) {
                Text("▶ 재실행", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            }
        }

        // 크래시 요약
        if (crashSummary.isNotEmpty()) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFF2D0A0A))
                    .border(1.dp, Red.copy(alpha = 0.4f))
                    .padding(12.dp)
            ) {
                Text("⚠️ 크래시 발생", color = Red, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(4.dp))
                Text(crashSummary, color = Red.copy(alpha = 0.8f), fontSize = 11.sp, lineHeight = 16.sp)
            }
        }

        // 의심 모드 안내
        val suspectedCount = mods.count { it.isSuspected }
        if (suspectedCount > 0) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFF2D1A0A))
                    .padding(12.dp)
            ) {
                Text(
                    "🔍 크래시 원인으로 의심되는 모드 ${suspectedCount}개가 있습니다. 비활성화 후 재실행해보세요.",
                    color = Color(0xFFFFB74D),
                    fontSize = 12.sp
                )
            }
        }

        if (isLoading) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(color = Pink)
            }
        } else {
            LazyColumn(
                contentPadding = PaddingValues(12.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                items(mods, key = { it.fileName }) { mod ->
                    ModToggleItem(
                        mod = mod,
                        onToggle = { onToggleMod(mod) }
                    )
                }
            }
        }
    }
}

@Composable
fun ModToggleItem(mod: ModEntry, onToggle: () -> Unit) {
    val Pink = Color(0xFFE91E8C)
    val TextMain = Color(0xFFFCE4EC)
    val TextSub = Color(0xFFBB86A0)
    val Red = Color(0xFFFF6B6B)

    val borderColor = when {
        mod.isSuspected && mod.enabled -> Red.copy(alpha = 0.6f)
        mod.isSuspected -> Red.copy(alpha = 0.3f)
        else -> BgBorder
    }
    val bgColor = when {
        mod.isSuspected -> Color(0xFF2D0A0A).copy(alpha = 0.5f)
        !mod.enabled -> BgSurface.copy(alpha = 0.5f)
        else -> BgSurface
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(bgColor)
            .border(1.dp, borderColor, RoundedCornerShape(8.dp))
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (mod.isSuspected) {
                    Text("⚠️", fontSize = 12.sp)
                }
                Text(
                    text = mod.modId,
                    color = if (mod.enabled) TextMain else TextSub,
                    fontSize = 13.sp,
                    fontWeight = if (mod.isSuspected) FontWeight.Bold else FontWeight.Normal,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Text(
                text = mod.fileName,
                color = TextSub.copy(alpha = 0.6f),
                fontSize = 10.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        Switch(
            checked = mod.enabled,
            onCheckedChange = { onToggle() },
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color.White,
                checkedTrackColor = if (mod.isSuspected) Red else Pink,
                uncheckedThumbColor = TextSub,
                uncheckedTrackColor = BgBorder
            )
        )
    }
}