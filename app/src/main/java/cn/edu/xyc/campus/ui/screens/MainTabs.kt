package cn.edu.xyc.campus.ui.screens

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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.unit.dp
import cn.edu.xyc.campus.R
import cn.edu.xyc.campus.data.local.ScheduleCache
import cn.edu.xyc.campus.data.model.ProfileCard
import cn.edu.xyc.campus.data.remote.JwxtApi
import cn.edu.xyc.campus.data.remote.JwxtResult
import cn.edu.xyc.campus.data.remote.TermUtils
import cn.edu.xyc.campus.ui.components.ThemeImage

private data class Tab(val key: String, val iconRes: Int, val label: String)

private val TABS = listOf(
    Tab("nav_schedule", R.drawable.nav_schedule, "课表"),
    Tab("nav_grades", R.drawable.nav_grades, "成绩"),
    Tab("nav_apps", R.drawable.nav_apps, "应用"),
    Tab("nav_leave", R.drawable.nav_leave, "请假"),
    Tab("nav_profile", R.drawable.nav_profile, "我的"),
)

@Composable
fun MainTabs(onLogout: () -> Unit) {
    var selected by rememberSaveable { mutableStateOf(0) }

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
            val college = ScheduleCache.gradeData[gKey]?.firstOrNull()?.school.orEmpty()
            if (xsxx != null) {
                ScheduleCache.profileData["PROFILE"] = ProfileCard(xsxx, college)
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
            when (selected) {
                0 -> ScheduleScreen()
                1 -> GradeScreen()
                2 -> AppsScreen()
                3 -> LeaveScreen()
                else -> ProfileScreen(onLogout = onLogout)
            }
        }
    }
}
