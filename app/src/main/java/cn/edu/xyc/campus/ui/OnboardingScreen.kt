package cn.edu.xyc.campus.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.Leaderboard
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Widgets
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cn.edu.xyc.campus.R
import cn.edu.xyc.campus.data.local.IntroStore
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.launch

/** 首次打开的三页新手引导：欢迎 → 功能亮点 → 数据说明 */
@Composable
fun OnboardingScreen(onDone: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var page by rememberSaveable { mutableStateOf(0) }
    val pagerState = rememberPagerState(initialPage = 0) { 3 }
    page = pagerState.currentPage

    Column(
        Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(listOf(Color(0xFFDCE9FF), Color(0xFFF3F8FF), Color(0xFFFFFFFF))),
            )
            .statusBarsPadding()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Row(Modifier.fillMaxWidth()) {
            Spacer(Modifier.weight(1f))
            TextButton(onClick = {
                IntroStore.setDone(context)
                onDone()
            }) { Text("跳过") }
        }

        HorizontalPager(
            state = pagerState,
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
        ) { p ->
            when (p) {
                0 -> Column(
                    Modifier.fillMaxSize(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    cn.edu.xyc.campus.ui.components.ThemeImage(
                        key = "login_logo",
                        resId = R.drawable.ic_launcher_foreground,
                        modifier = Modifier.size(150.dp),
                    )
                    Spacer(Modifier.height(18.dp))
                    Text("新院助手", fontSize = 30.sp, fontWeight = FontWeight.Bold, color = Color(0xFF173A6B))
                    Text(
                        "新余学院校园服务",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(10.dp))
                    Text(
                        "课表 · 成绩 · 应用 · 请假 · 小组件\n一站式校园助手",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                    )
                }
                1 -> Column(
                    Modifier.fillMaxSize(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    FeatureRow(Icons.Rounded.CalendarMonth, "课表随行", "按周翻页查看，桌面小组件直接看今天和明天")
                    Spacer(Modifier.height(20.dp))
                    FeatureRow(Icons.Rounded.Leaderboard, "成绩速览", "学分绩点一目了然，彩色分数徽标重点突出")
                    Spacer(Modifier.height(20.dp))
                    FeatureRow(Icons.Rounded.Widgets, "自由定制", "自定义课程、五种小组件规格，随你搭配")
                }
                else -> Column(
                    Modifier.fillMaxSize(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    Box(
                        Modifier
                            .size(84.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primaryContainer),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            Icons.Rounded.Lock,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(36.dp),
                        )
                    }
                    Spacer(Modifier.height(18.dp))
                    Text("数据只属于你", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = Color(0xFF173A6B))
                    Spacer(Modifier.height(12.dp))
                    Text(
                        "使用学校门户账号登录\n账号密码仅加密保存在本机\n所有数据只与学校官方服务器通信\n开源项目，欢迎在 GitHub 关注我们",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        lineHeight = 22.sp,
                    )
                }
            }
        }

        // 页码圆点
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            repeat(3) { i ->
                Box(
                    Modifier
                        .size(if (page == i) 10.dp else 8.dp)
                        .clip(CircleShape)
                        .background(
                            if (page == i) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f),
                        ),
                )
            }
        }
        Spacer(Modifier.height(14.dp))
        Button(
            onClick = {
                if (page < 2) {
                    scope.launch { pagerState.animateScrollToPage(page + 1) }
                } else {
                    IntroStore.setDone(context)
                    onDone()
                }
            },
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier
                .fillMaxWidth()
                .height(50.dp),
        ) {
            Text(if (page < 2) "下一步" else "开始使用", fontSize = 16.sp)
        }
    }
}

@Composable
private fun FeatureRow(icon: androidx.compose.ui.graphics.vector.ImageVector, title: String, desc: String) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        Box(
            Modifier
                .size(56.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primaryContainer),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(28.dp))
        }
        Spacer(Modifier.width(16.dp))
        Column {
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Text(
                desc,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
