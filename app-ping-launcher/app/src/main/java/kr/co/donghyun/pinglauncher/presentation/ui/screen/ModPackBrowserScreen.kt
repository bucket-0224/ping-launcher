package kr.co.donghyun.pinglauncher.presentation.ui.screen

import android.util.Log
import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kr.co.donghyun.pinglauncher.data.curseforge.CurseForgeMod
import kr.co.donghyun.pinglauncher.data.mojang.DownloadPhase
import kr.co.donghyun.pinglauncher.data.mojang.DownloadProgress
import kr.co.donghyun.pinglauncher.presentation.ModPackDetailActivity
import kr.co.donghyun.pinglauncher.presentation.ui.theme.*
import kotlin.text.isEmpty

private val Pink     = Color(0xFFE91E8C)
private val TextMain = Color(0xFFFCE4EC)
private val TextSub  = Color(0xFFBB86A0)

@Composable
fun ModPackBrowserScreen(
    modpacks: List<CurseForgeMod>,
    progress: DownloadProgress,
    isLoading: Boolean,
    isInstalling: Boolean,
    installingModId: Int?,
    statusMessage: String,
    selectedVersion: String,
    installedIds: Set<Int>,
    onSearch: (String, String) -> Unit,
    onVersionFilter: (String) -> Unit,
    onLoadMore: () -> Unit,
    hasMore: Boolean,        // ← 추가
    onInstall: (CurseForgeMod) -> Unit,
    onLaunch: (CurseForgeMod) -> Unit
) {
    // ModPackBrowserScreen 상단에
    val listState = rememberLazyListState()


    LaunchedEffect(listState) {
        snapshotFlow { listState.layoutInfo }
            .collect { layoutInfo ->
                val lastVisible = layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
                val totalItems = layoutInfo.totalItemsCount
                android.util.Log.d("PING_LAUNCHER", "scroll: last=$lastVisible total=$totalItems hasMore=$hasMore isLoading=$isLoading")
                if (lastVisible >= totalItems - 3 && totalItems > 0 && hasMore && !isLoading) {
                    onLoadMore()
                }
            }
    }

    var searchQuery by remember { mutableStateOf("") }
    var searchJob by remember { mutableStateOf<Job?>(null) }
    val scope = rememberCoroutineScope()
    val ctx = LocalContext.current
    val supportedVersions = listOf("", "1.21.4", "1.21.1", "1.20.1", "1.19.4", "1.18.2", "1.16.5", "1.12.2")

    Column(
        modifier = Modifier.fillMaxSize().background(BgDark)
    ) {
        // 헤더
        Box(modifier = Modifier.height(24.dp))
        Box(
            modifier = Modifier.fillMaxWidth()
                .background(Color(0xFF1A0A14))
                .padding(horizontal = 20.dp, vertical = 16.dp)
        ) {
            Column {
                Text("📦 CurseForge 모드팩", color = Color(0xFFFF6BB5), fontSize = 20.sp, fontWeight = FontWeight.Bold)
                Text("모드팩을 검색하고 설치하세요", color = TextSub, fontSize = 12.sp)
            }
        }

        // 검색창
        Row(
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            BasicTextField(
                value = searchQuery,
                onValueChange = { q ->
                    searchQuery = q
                    searchJob?.cancel()
                    searchJob = scope.launch {
                        delay(400)
                        onSearch(q, selectedVersion)
                    }
                },
                textStyle = TextStyle(color = TextMain, fontSize = 14.sp),
                cursorBrush = SolidColor(Pink),
                decorationBox = { inner ->
                    Box(
                        modifier = Modifier.fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(BgSurface)
                            .border(1.dp, BgBorder, RoundedCornerShape(8.dp))
                            .padding(horizontal = 12.dp, vertical = 10.dp)
                    ) {
                        if (searchQuery.isEmpty()) Text("모드팩 검색...", color = TextSub, fontSize = 14.sp)
                        inner()
                    }
                },
                modifier = Modifier.weight(1f)
            )
        }

        // 버전 필터
        LazyRow(
            modifier = Modifier.padding(horizontal = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            items(supportedVersions) { version ->
                val isSelected = selectedVersion == version
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(16.dp))
                        .background(if (isSelected) PinkDark else BgSurface)
                        .border(1.dp, if (isSelected) Pink else BgBorder, RoundedCornerShape(16.dp))
                        .clickable { onVersionFilter(version); onSearch(searchQuery, version) }
                        .padding(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    Text(
                        text = if (version.isEmpty()) "전체" else version,
                        color = if (isSelected) Color.White else TextSub,
                        fontSize = 12.sp,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // 설치 진행률
        if (isInstalling) {
            Column(
                modifier = Modifier.fillMaxWidth().background(BgSurface).padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = when (progress.phase) {
                            DownloadPhase.FETCHING_MANIFEST -> "모드팩 정보 가져오는 중..."
                            DownloadPhase.DOWNLOADING_CLIENT -> "모드팩 다운로드 중..."
                            DownloadPhase.DOWNLOADING_LIBRARIES -> "파일 추출 중..."
                            DownloadPhase.DOWNLOADING_ASSETS -> "모드 다운로드 중... (${progress.current}/${progress.total})"
                            else -> statusMessage.ifEmpty { "설치 중..." }
                        },
                        color = TextSub, fontSize = 12.sp,
                        modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis
                    )
                    Text("${progress.percent}%", color = Pink, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
                LinearProgressIndicator(
                    progress = { progress.fraction },
                    modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)),
                    color = Pink, trackColor = BgBorder
                )
            }
        }

        if (statusMessage.isNotEmpty() && !isInstalling) {
            Text(
                text = statusMessage,
                color = if (statusMessage.startsWith("❌")) Color(0xFFFF6B6B) else Color(0xFF69DB7C),
                fontSize = 12.sp,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
            )
        }

        // 모드팩 목록
        Box(modifier = Modifier.weight(1f)) {
            if (isLoading) {
                Box(modifier = Modifier.fillMaxSize()) {
                    CircularProgressIndicator(color = Pink, modifier = Modifier.align(Alignment.Center))
                }
            } else {
                LazyColumn(
                    state = listState,  // ← 추가
                    contentPadding = PaddingValues(12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(modpacks) { mod ->
                        val isThisInstalling = isInstalling && installingModId == mod.id
                        val isAlreadyInstalled = installedIds.contains(mod.id)
                        ModPackItem(
                            mod = mod,
                            isInstalling = isThisInstalling,
                            isInstalled = isAlreadyInstalled,
                            onInstall = { onInstall(mod) },
                            onLaunch = { onLaunch(mod) },
                            onDetail = { mod ->
                                ModPackDetailActivity.start(
                                    ctx,
                                    mod.id, mod.name, mod.summary,
                                    mod.logo?.thumbnailUrl, mod.downloadCount
                                )
                            }
                        )
                    }

                    if (hasMore || isLoading) {
                        item {
                            Box(
                                modifier = Modifier.fillMaxWidth().padding(16.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                CircularProgressIndicator(
                                    color = Pink,
                                    modifier = Modifier.size(24.dp),
                                    strokeWidth = 2.dp
                                )
                            }
                        }
                    }

                    item {
                        Box(modifier = Modifier.height(64.dp))
                    }

                    if (isLoading && modpacks.isEmpty()) {
                        item {
                            Box(
                                modifier = Modifier.fillMaxSize(),
                                contentAlignment = Alignment.Center
                            ) {
                                CircularProgressIndicator(color = Pink)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun ModPackItem(
    mod: CurseForgeMod,
    isInstalling: Boolean,
    isInstalled: Boolean,
    onInstall: () -> Unit,
    onLaunch: () -> Unit,
    onDetail: (CurseForgeMod) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(BgSurface)
            .border(
                width = if (isInstalled) 1.5.dp else 1.dp,
                color = if (isInstalled) Pink.copy(alpha = 0.5f) else BgBorder,
                shape = RoundedCornerShape(10.dp)
            )
            .padding(12.dp)
            .clickable { onDetail(mod) },
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // 기존 Box 교체
        Box(
            modifier = Modifier
                .size(52.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(Color(0xFF2D0A20)),
            contentAlignment = Alignment.Center
        ) {
            val thumbnailUrl = mod.logo?.thumbnailUrl
            if (thumbnailUrl != null) {
                AsyncImage(
                    model = thumbnailUrl,
                    contentDescription = mod.name,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
            } else {
                Text(if (isInstalled) "✅" else "📦", fontSize = 24.sp)
            }
        }

        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                text = mod.name, color = TextMain, fontSize = 14.sp, fontWeight = FontWeight.SemiBold,
                maxLines = 1, overflow = TextOverflow.Ellipsis
            )
            Text(
                text = mod.summary, color = TextSub, fontSize = 11.sp,
                maxLines = 2, overflow = TextOverflow.Ellipsis
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("⬇ ${formatDownloads(mod.downloadCount)}", color = TextSub, fontSize = 10.sp)
                if (isInstalled) {
                    Text("설치됨", color = Pink, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                }
                mod.latestFilesIndexes.take(2).forEach { idx ->
                    Box(
                        modifier = Modifier.clip(RoundedCornerShape(4.dp))
                            .background(PinkDark.copy(alpha = 0.4f))
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text(idx.gameVersion, color = Color(0xFFFF6BB5), fontSize = 9.sp)
                    }
                }
            }
        }

        // 설치/열기 버튼
        if (isInstalling) {
            CircularProgressIndicator(
                color = Pink,
                modifier = Modifier.size(28.dp),
                strokeWidth = 2.dp
            )
        } else if (isInstalled) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp), horizontalAlignment = Alignment.End) {
                Button(
                    onClick = onLaunch,
                    colors = ButtonDefaults.buttonColors(containerColor = Pink),
                    shape = RoundedCornerShape(8.dp),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    Text("▶ 열기", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
                TextButton(
                    onClick = onInstall,
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                ) {
                    Text("재설치", color = TextSub, fontSize = 10.sp)
                }
            }
        } else {
            Button(
                onClick = onInstall,
                colors = ButtonDefaults.buttonColors(containerColor = Pink),
                shape = RoundedCornerShape(8.dp),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp)
            ) {
                Text("설치", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

private fun formatDownloads(count: Long): String {
    return when {
        count >= 1_000_000 -> "${count / 1_000_000}M"
        count >= 1_000 -> "${count / 1_000}K"
        else -> count.toString()
    }
}