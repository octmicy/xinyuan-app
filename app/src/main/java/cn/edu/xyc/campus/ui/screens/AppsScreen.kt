package cn.edu.xyc.campus.ui.screens

import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import cn.edu.xyc.campus.R
import cn.edu.xyc.campus.data.local.AppGridPrefs
import cn.edu.xyc.campus.data.local.ScheduleCache
import cn.edu.xyc.campus.data.model.ThirdApp
import cn.edu.xyc.campus.data.remote.PortalApi
import cn.edu.xyc.campus.data.remote.SessionStore
import cn.edu.xyc.campus.data.remote.ticketUrl
import cn.edu.xyc.campus.ui.theme.isAppDarkTheme
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyGridState

/** 应用白名单：门户名称 → 展示名 / 本地图标 /（可选）SPA 落地路由 */
private data class AppEntry(
    val portalName: String,   // 与门户 /app/getApplication 返回的 name 匹配，也作为排序/显隐的持久化 key
    val label: String = portalName,
    val iconRes: Int,
    val finalHash: String? = null, // 应用内打开后 SPA 跳转的目标路由
    val native: Boolean = false,   // 原生页面入口，不走门户/WebView
    val comingSoon: Boolean = false, // 敬请期待占位：点击仅提示制作中
)

private val ALLOWED = listOf(
    AppEntry("综测计算", "综测计算", R.drawable.app_zongce, native = true),
    AppEntry("今天吃什么", "今天吃什么", R.drawable.app_food, comingSoon = true),
    AppEntry("教务系统", iconRes = R.drawable.app_jwxt),
    AppEntry("我的图书馆", "图书馆电子证", R.drawable.app_library, finalHash = "/credential?from=Home"),
    AppEntry("就业系统", iconRes = R.drawable.app_career),
    AppEntry("毕业生离校系统", iconRes = R.drawable.app_graduate),
    AppEntry("学工系统", iconRes = R.drawable.app_xg),
    AppEntry("学生缴费", iconRes = R.drawable.app_pay),
    AppEntry("网络教学系统", iconRes = R.drawable.app_online),
)

private data class OpenTarget(val name: String, val url: String, val finalHash: String?)

@Composable
fun AppsScreen() {
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    var loading by rememberSaveable { mutableStateOf(true) }
    var error by rememberSaveable { mutableStateOf<String?>(null) }
    var reloadKey by rememberSaveable { mutableStateOf(0) }
    var openTarget by remember { mutableStateOf<OpenTarget?>(null) }
    var showZongce by remember { mutableStateOf(false) }

    // 宫格偏好：顺序 + 隐藏集合（SharedPreferences 持久化，杀后台后重建进程即恢复）
    var order by remember { mutableStateOf(AppGridPrefs.getOrder(context)) }
    var hidden by remember { mutableStateOf(AppGridPrefs.getHidden(context)) }
    var showVisibility by remember { mutableStateOf(false) }

    LaunchedEffect(reloadKey) {
        ScheduleCache.applications["APPS"]?.let {
            loading = false
            return@LaunchedEffect
        }
        if (!ScheduleCache.tryMark("APPS")) return@LaunchedEffect
        loading = true
        error = null
        PortalApi.getApplications()
            .onSuccess { ScheduleCache.applications["APPS"] = it }
            .onFailure { error = "加载失败: ${it.message}" }
        ScheduleCache.unmark("APPS")
        loading = false
    }

    val all = ScheduleCache.applications["APPS"].orEmpty()
    // 白名单过滤 + 同名去重（教务系统两个入口优先 xyoauthlogin）+ 关联本地图标
    // 综测为原生入口，不依赖门户数据，始终显示且排在最前
    val apps = remember(all) {
        ALLOWED.mapNotNull { entry ->
            if (entry.native || entry.comingSoon) return@mapNotNull entry to null
            all.filter { it.name == entry.portalName && it.href.isNotBlank() }.let { candidates ->
                candidates.firstOrNull { it.href.contains("xyoauthlogin") }
                    ?: candidates.firstOrNull()
            }?.let { app -> entry to app }
        }
    }

    // 显示列表：过滤隐藏 → 按持久化顺序排（未记录顺序的新应用追加尾部，stable sort 保持相对次序）
    var displayApps by remember(apps, order, hidden) {
        mutableStateOf(
            apps.filter { (e, _) -> e.portalName !in hidden }
                .sortedBy { (e, _) -> order?.indexOf(e.portalName)?.takeIf { it >= 0 } ?: Int.MAX_VALUE },
        )
    }

    // Launcher 式拖拽重排（sh.calvin.reorderable）：拖拽跟手插值 + 其他项 animateItem 让位动画 + 边缘自动滚动
    val lazyGridState = rememberLazyGridState()
    val reorderableLazyGridState = rememberReorderableLazyGridState(lazyGridState) { from, to ->
        displayApps = displayApps.toMutableList().apply { add(to.index, removeAt(from.index)) }
    }

    Column(Modifier.fillMaxSize()) {
        // 标题 + 右上角显隐设置
        Row(
            Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, end = 4.dp, top = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                "校园应用",
                style = MaterialTheme.typography.titleLarge,
            )
            Spacer(Modifier.weight(1f))
            IconButton(onClick = { showVisibility = true }) {
                Icon(
                    Icons.Rounded.Tune,
                    contentDescription = "应用显隐设置",
                    tint = MaterialTheme.colorScheme.primary,
                )
            }
        }
        Text(
            "长按应用可拖动排序 · 点右上角可隐藏应用",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 16.dp),
        )
        when {
            loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            else -> {
                // 门户加载失败时仅提示，不影响原生入口与已匹配应用展示
                if (error != null) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 4.dp),
                    ) {
                        Text(
                            error!!,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.error,
                            modifier = Modifier.weight(1f),
                        )
                        TextButton(onClick = { reloadKey++ }) { Text("重试") }
                    }
                }
                if (displayApps.isEmpty()) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(
                            "应用都已隐藏，点右上角设置恢复",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                } else {
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(3),
                        state = lazyGridState,
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(16.dp),
                        horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(12.dp),
                        verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(16.dp),
                    ) {
                        itemsIndexed(displayApps, key = { _, item -> item.first.portalName }) { _, item ->
                            ReorderableItem(reorderableLazyGridState, key = item.first.portalName) { isDragging ->
                                AppCell(
                                    entry = item.first,
                                    isDragging = isDragging,
                                    onClick = {
                                        val (entry, app) = item
                                        when {
                                            entry.comingSoon -> {
                                                Toast.makeText(
                                                    context,
                                                    "该功能还在制作中，敬请期待 🍚",
                                                    Toast.LENGTH_SHORT,
                                                ).show()
                                            }
                                            entry.native -> showZongce = true
                                            else -> app?.let {
                                                openTarget = OpenTarget(entry.label, it.ticketUrl(), entry.finalHash)
                                            }
                                        }
                                    },
                                    // 长按启动拖拽；松手时把最终顺序落盘（随进程重建恢复）
                                    modifier = Modifier.longPressDraggableHandle(
                                        onDragStarted = {
                                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                        },
                                        onDragStopped = {
                                            AppGridPrefs.setOrder(
                                                context,
                                                displayApps.map { it.first.portalName },
                                            )
                                            order = displayApps.map { it.first.portalName }
                                        },
                                    ),
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    openTarget?.let { target ->
        AppWebViewDialog(
            name = target.name,
            url = target.url,
            finalHash = target.finalHash,
            onDismiss = { openTarget = null },
        )
    }

    if (showZongce) {
        ZongceScreen(onDismiss = { showZongce = false })
    }

    if (showVisibility) {
        AppVisibilityDialog(
            entries = apps.map { it.first },
            initiallyHidden = hidden,
            onConfirm = { h ->
                hidden = h
                AppGridPrefs.setHidden(context, h)
                showVisibility = false
            },
            onDismiss = { showVisibility = false },
        )
    }
}

@Composable
private fun AppCell(
    entry: AppEntry,
    isDragging: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier
            .fillMaxWidth()
            .zIndex(if (isDragging) 1f else 0f)
            .graphicsLayer {
                // 拖拽中轻微放大提层，配合库的跟手插值
                val s = if (isDragging) 1.06f else 1f
                scaleX = s
                scaleY = s
            }
            .clip(RoundedCornerShape(18.dp))
            .clickable(onClick = onClick)
            .padding(4.dp),
    ) {
        // 图标卡片托底：浅色白底 / 深色深灰黑底（贴纸四边透明，底色即观感背景）
        Box(
            Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
                .padding(horizontal = 4.dp)
                .shadow(if (isDragging) 6.dp else 3.dp, RoundedCornerShape(20.dp))
                .clip(RoundedCornerShape(20.dp))
                .background(if (isAppDarkTheme()) Color(0xFF17181A) else Color.White)
                .padding(12.dp),
            contentAlignment = Alignment.Center,
        ) {
            Image(
                painter = androidx.compose.ui.res.painterResource(entry.iconRes),
                contentDescription = entry.label,
                modifier = Modifier.fillMaxSize(),
            )
        }
        Spacer(Modifier.height(6.dp))
        Text(
            entry.label,
            fontSize = 12.sp,
            lineHeight = 14.sp,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurface,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

/** 应用显隐设置弹窗：勾选 = 显示，取消 = 隐藏；确认后由调用方持久化 */
@Composable
private fun AppVisibilityDialog(
    entries: List<AppEntry>,
    initiallyHidden: Set<String>,
    onConfirm: (Set<String>) -> Unit,
    onDismiss: () -> Unit,
) {
    var tempHidden by remember { mutableStateOf(initiallyHidden) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("显示哪些应用") },
        text = {
            Column(
                Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
            ) {
                Text(
                    "取消勾选的应用将从宫格隐藏，随时可以再打开。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(8.dp))
                entries.forEach { e ->
                    val shown = e.portalName !in tempHidden
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .clickable {
                                tempHidden = if (shown) tempHidden + e.portalName
                                else tempHidden - e.portalName
                            }
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Checkbox(
                            checked = shown,
                            onCheckedChange = { v ->
                                tempHidden = if (v) tempHidden - e.portalName
                                else tempHidden + e.portalName
                            },
                        )
                        Text(e.label, style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = { onConfirm(tempHidden) }) { Text("完成") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    )
}
