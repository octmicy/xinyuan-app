package cn.edu.xyc.campus.ui.screens

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import cn.edu.xyc.campus.R
import cn.edu.xyc.campus.data.local.ScheduleCache
import cn.edu.xyc.campus.data.model.ProfileCard
import cn.edu.xyc.campus.data.remote.JwxtApi
import cn.edu.xyc.campus.data.remote.JwxtResult
import cn.edu.xyc.campus.data.remote.TermUtils
import cn.edu.xyc.campus.ui.components.ThemeImage

private data class Tab(val key: String, val iconRes: Int, val label: String)

// 5 tab：首页居中（第 3 位）；成绩已移入「应用」宫格，不再占底部导航
private val TABS = listOf(
    Tab("nav_schedule", R.drawable.nav_schedule, "课表"),
    Tab("nav_apps", R.drawable.nav_apps, "应用"),
    Tab("nav_home", R.drawable.nav_home, "首页"),
    Tab("nav_leave", R.drawable.nav_leave, "请假"),
    Tab("nav_profile", R.drawable.nav_profile, "我的"),
)

@Composable
fun MainTabs(onLogout: () -> Unit) {
    // 默认落在首页（索引 2，居中位）
    var selected by rememberSaveable { mutableStateOf(2) }

    // 懒加载标记：未访问过的 tab 不初始化（避免启动即创建 WebView/发请求）
    // 默认已访问首页（索引 2）；进程内有效即可
    val visited = remember { mutableStateOf(setOf(2)) }

    LaunchedEffect(selected) {
        // 标记已访问：内容立即初始化渲染并常驻（切换只做透明度过渡，不销毁重建）
        if (selected !in visited.value) visited.value = visited.value + selected
    }

    // 登录成功后后台预取：成绩 + 学籍卡，切 Tab 零等待
    LaunchedEffect(Unit) {
        val term = TermUtils.current()
        // 1) 当前学期成绩
        val gKey = ScheduleCache.gradeKey(term.xnm, term.xqm)
        if (!ScheduleCache.gradeData.containsKey(gKey) && ScheduleCache.tryMark(gKey)) {
            try {
                when (val r = JwxtApi.getGrades(term)) {
                    is JwxtResult.Ok -> ScheduleCache.gradeData[gKey] = r.data
                    else -> Unit
                }
            } finally {
                ScheduleCache.unmark(gKey)
            }
        }
        // 2) 学籍卡（优先复用课表预载的 xsxx）
        if (!ScheduleCache.profileData.containsKey("PROFILE")) {
            val xsxx = ScheduleCache.weekData.values.firstOrNull()?.second
            if (xsxx != null) {
                // 学院取自学籍 xsxx（真实院系，为空则 UI 隐藏该行），不用成绩开课院系冒充
                ScheduleCache.profileData["PROFILE"] = ProfileCard(xsxx, xsxx.college)
            } else if (ScheduleCache.tryMark("PROFILE")) {
                try {
                    when (val r = JwxtApi.getProfile()) {
                        is JwxtResult.Ok -> ScheduleCache.profileData["PROFILE"] = r.data
                        else -> Unit
                    }
                } finally {
                    ScheduleCache.unmark("PROFILE")
                }
            }
        }
    }

    Scaffold(
        bottomBar = {
            NavigationBar {
                TABS.forEachIndexed { index, tab ->
                    NavigationBarItem(
                        selected = selected == index,
                        onClick = { selected = index },
                        icon = {
                            ThemeImage(
                                key = tab.key,
                                resId = tab.iconRes,
                                contentDescription = tab.label,
                                modifier = Modifier
                                    .size(30.dp)
                                    .padding(top = 2.dp)
                                    .alpha(if (selected == index) 1f else 0.4f),
                            )
                        },
                        label = { Text(tab.label) },
                    )
                }
            }
        },
    ) { padding ->
        Box(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize(),
        ) {
            // 页面叠放 + 交叉淡入淡出（替代 Pager 滑动：滑动时页面内容持续重排重绘，
            // 实测 50th 帧 25ms / Janky 18%，改为只变透明度的过渡后绘制成本极低）
            TABS.indices.forEach { page ->
                if (page in visited.value) {
                    TabPageHost(isActive = page == selected) {
                        when (page) {
                            0 -> ScheduleScreen()
                            1 -> AppsScreen()
                            2 -> HomeScreen(onOpenSchedule = { selected = 0 })
                            3 -> LeaveScreen()
                            else -> ProfileScreen(onLogout = onLogout)
                        }
                    }
                }
            }
        }
    }
}

/**
 * 单个 tab 页宿主：交叉淡入淡出过渡（220ms）+ 触摸隔离。
 * - 常驻页面：不销毁重建，remember 状态与滚动位置保留
 * - 非当前页消费全部触摸事件，避免透明度为 0 的页面被误触
 */
@Composable
private fun TabPageHost(isActive: Boolean, content: @Composable () -> Unit) {
    val alpha by animateFloatAsState(
        targetValue = if (isActive) 1f else 0f,
        // 220ms 交叉淡入淡出：旧页淡出的同时新页淡入，动画清晰可见（实测 Janky 3.25%）
        animationSpec = tween(durationMillis = 220),
        label = "tabAlpha",
    )
    Box(
        modifier = Modifier
            .fillMaxSize()
            .zIndex(if (isActive) 1f else 0f) // 当前页在上层，接收触摸并叠加绘制
            .graphicsLayer { this.alpha = alpha }
            .then(
                if (isActive) {
                    Modifier
                } else {
                    Modifier.pointerInput(Unit) {
                        awaitPointerEventScope {
                            while (true) {
                                awaitPointerEvent().changes.forEach { it.consume() }
                            }
                        }
                    }
                },
            ),
    ) {
        content()
    }
}
