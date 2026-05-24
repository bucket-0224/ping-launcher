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
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import coil.compose.AsyncImage
import kr.co.donghyun.pinglauncher.presentation.ModDetail
import kr.co.donghyun.pinglauncher.presentation.ui.theme.BgBorder
import kr.co.donghyun.pinglauncher.presentation.ui.theme.BgDark
import kr.co.donghyun.pinglauncher.presentation.ui.theme.BgSurface

@Composable
fun ModPackDetailScreen(
    modId: Int,
    modName: String,
    modSummary: String,
    modLogo: String?,
    modDownloads: Long,
    detail: ModDetail?,
    isLoading: Boolean,
    isInstalled: Boolean,
    onBack: () -> Unit,
    onInstall: () -> Unit,
    onLaunch: () -> Unit,
    onImageClick: (Int) -> Unit
) {
    val Pink = Color(0xFFE91E8C)
    val TextMain = Color(0xFFFCE4EC)
    val TextSub = Color(0xFFBB86A0)

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
                Text("뒤로", color = TextSub, fontSize = 14.sp)
            }
            Text(
                text = modName,
                color = TextMain,
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f).padding(horizontal = 8.dp)
            )
            if (isInstalled) {
                Button(
                    onClick = onLaunch,
                    colors = ButtonDefaults.buttonColors(containerColor = Pink),
                    shape = RoundedCornerShape(8.dp),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    Text("▶ 열기", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
            } else {
                Button(
                    onClick = onInstall,
                    colors = ButtonDefaults.buttonColors(containerColor = Pink),
                    shape = RoundedCornerShape(8.dp),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    Text("설치", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
            }
        }

        LazyColumn(modifier = Modifier.fillMaxSize()) {
            // 헤더
            item {
                Box(
                    modifier = Modifier.fillMaxWidth().height(200.dp).background(Color(0xFF1A0A14))
                ) {
                    if (modLogo != null) {
                        AsyncImage(
                            model = "https://images.hdqwalls.com/download/minecraft-live-2025-game-c6-1024x768.jpg",
                            contentDescription = null,
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop,
                            alpha = 0.35f
                        )
                    }
                    Row(
                        modifier = Modifier.align(Alignment.BottomStart).padding(16.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (modLogo != null) {
                            AsyncImage(
                                model = modLogo,
                                contentDescription = null,
                                modifier = Modifier.size(72.dp).clip(RoundedCornerShape(12.dp)),
                                contentScale = ContentScale.Crop
                            )
                        }
                        Column {
                            Text(modName, color = TextMain, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                            Text("⬇ ${formatCount(modDownloads)}", color = TextSub, fontSize = 12.sp)
                            if (isInstalled) {
                                Text("✅ 설치됨", color = Pink, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }

            // 요약
            item {
                Text(
                    text = modSummary,
                    color = TextSub,
                    fontSize = 13.sp,
                    modifier = Modifier.padding(16.dp)
                )
            }

            if (isLoading) {
                item {
                    Box(
                        modifier = Modifier.fillMaxWidth().padding(48.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(color = Pink)
                    }
                }
            } else {
                // 스크린샷
                val screenshots = detail?.screenshots ?: emptyList()
                if (screenshots.isNotEmpty()) {
                    item {
                        Text(
                            "스크린샷",
                            color = TextMain,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                        )
                    }
                    item {
                        LazyRow(
                            contentPadding = PaddingValues(horizontal = 16.dp),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            itemsIndexed(screenshots) { page, screenshot ->
                                Box(
                                    modifier = Modifier
                                        .width(240.dp)
                                        .height(135.dp)
                                        .clip(RoundedCornerShape(10.dp))
                                        .background(BgSurface)
                                        .clickable { onImageClick(page) }
                                ) {
                                    // 썸네일 (저용량)
                                    AsyncImage(
                                        model = screenshot.thumbnailUrl,
                                        contentDescription = null,
                                        modifier = Modifier.fillMaxSize(),
                                        contentScale = ContentScale.Crop
                                    )
                                    // 확대 힌트
                                    Box(
                                        modifier = Modifier
                                            .align(Alignment.BottomEnd)
                                            .padding(6.dp)
                                            .clip(RoundedCornerShape(4.dp))
                                            .background(Color.Black.copy(alpha = 0.6f))
                                            .padding(horizontal = 6.dp, vertical = 2.dp)
                                    ) {
                                        Text("🔍", fontSize = 12.sp)
                                    }
                                }
                            }
                        }
                        Spacer(modifier = Modifier.height(16.dp))
                    }
                }

                // 설명 부분을 WebView로 교체
                item {
                    Text(
                        "설명",
                        color = TextMain,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                    )

                    var webViewHeight by remember { mutableIntStateOf(1000) }

                    AndroidView(
                        factory = { ctx ->
                            android.webkit.WebView(ctx).apply {
                                settings.javaScriptEnabled = true
                                settings.loadWithOverviewMode = true
                                settings.useWideViewPort = true
                                settings.setSupportZoom(false)
                                setBackgroundColor(android.graphics.Color.TRANSPARENT)
                                isScrollContainer = false
                                isHorizontalScrollBarEnabled = false
                                isVerticalScrollBarEnabled = false

                                webViewClient = object : android.webkit.WebViewClient() {
                                    override fun onPageFinished(view: android.webkit.WebView?, url: String?) {
                                        // 페이지 로드 완료 후 실제 높이 측정
                                        view?.evaluateJavascript(
                                            "(function() { return document.body.scrollHeight; })();"
                                        ) { height ->
                                            val h = height?.toFloatOrNull()?.toInt() ?: 1000
                                            webViewHeight = h
                                        }
                                    }
                                }
                            }
                        },
                        update = { webView ->
                            val styledHtml = """
                                <html>
                                <head>
                                <meta name="viewport" content="width=device-width, initial-scale=1.0, maximum-scale=1.0, user-scalable=no">
                                <style>
                                    * { box-sizing: border-box; }
                                    body {
                                        background: transparent;
                                        color: #BB86A0;
                                        font-size: 14px;
                                        font-family: sans-serif;
                                        padding: 0 16px;
                                        margin: 0;
                                        max-width: 100%;
                                        overflow-x: hidden;
                                        word-break: break-word;
                                    }
                                    img {
                                        max-width: 100% !important;
                                        height: auto !important;
                                        border-radius: 8px;
                                        margin: 8px 0;
                                        display: block;
                                    }
                                    a { color: #E91E8C; }
                                    h1,h2,h3,h4 { color: #FCE4EC; }
                                    p { margin: 8px 0; line-height: 1.6; }
                                    ul, ol { padding-left: 24px; }
                                    table { max-width: 100%; overflow-x: auto; display: block; }
                                </style>
                                </head>
                                <body>${detail?.rawHtml ?: ""}</body>
                                </html>
                            """.trimIndent()
                            webView.loadDataWithBaseURL(null, styledHtml, "text/html", "UTF-8", null)
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(webViewHeight.dp)  // 동적 높이
                            .padding(horizontal = 8.dp)
                    )
                    Spacer(modifier = Modifier.height(40.dp))
                }
            }
        }
    }
}

private fun formatCount(count: Long): String = when {
    count >= 1_000_000 -> "${count / 1_000_000}M"
    count >= 1_000 -> "${count / 1_000}K"
    else -> count.toString()
}