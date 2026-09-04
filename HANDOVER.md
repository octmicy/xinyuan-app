# 新院助手 · 项目交接文档

> 交接日期：2026-09-04 ｜ 原负责人：时栖（octmicy）+ WorkBuddy 协作开发
> 接手人阅读顺序：先看 §1 概览，再跑通 §4 构建运行，然后精读 §3 接口协议（本项目最核心的资产），遇到问题查 §6 踩坑记录。

---

## 1. 项目概览

**新院助手**（新余学院校园助手），原生 Android App，包名 `cn.edu.xyc.campus`。无自建后端——App 直连学校各系统（门户 ehallmobile / 教务 zfjwxt / 学工 ssxt），登录态与数据全部实时拉取，本地仅做加密凭证持久化与进程内缓存。

### 当前功能（全部已真机验证）

| 模块 | 功能 | 数据来源 |
|---|---|---|
| 课表 | 一屏式周课表（7 列×12 节自适应）、HorizontalPager 跟手翻页切周、学年/学期切换、相邻周预加载 | 教务「课表查询(旧)」Y253510 |
| 成绩 | 学年+学期两级切换、总学分/绩点汇总卡片、缓存 | 教务「成绩查询」Y305005 |
| 应用 | 门户 26 个应用白名单 7 项宫格（二次元图标）、点击带 ticket 浏览器免密打开 | 门户 `/app/getApplication` |
| 请假 | WebView 内嵌学工系统，直达请假申请表单/请假记录 | 学工 ssxt `#/qingjia/qj_s_add` |
| 我的 | 学籍卡（姓名/学号/班级/专业/年级）、退出登录 | 课表 xsxx + 成绩 kkbmmc |
| 登录 | 门户加密登录 + 教务 SSO；冷启动静默自动登录（凭证加密持久化） | 门户 3DES 协议 |

### 视觉

全套二次元 Q 版 IP：封面图标 + 底部导航 5 图标 + 应用宫格 7 图标（银发双马尾小魔女，蓝白校色，AI 生成后人工裁切）。图标生成提示词存于 `docs/icon-gen/`。

---

## 2. 代码结构

```
campus-app/
├── app/src/main/java/cn/edu/xyc/campus/
│   ├── MainActivity.kt              # 入口：CredStore.init + 竖屏锁定
│   ├── data/
│   │   ├── local/
│   │   │   ├── CredStore.kt         # 凭证加密存储（Keystore+EncryptedSharedPreferences）
│   │   │   └── ScheduleCache.kt     # 进程内缓存（SnapshotStateMap：周次/每周课表/成绩/学籍/应用）+ inFlight 去重
│   │   ├── model/Models.kt          # Course/GradeItem/StudentInfo/ProfileCard/ThirdApp/WeekInfo/WapMenu
│   │   └── remote/
│   │       ├── CampusHttp.kt        # 全局 OkHttp（移动 UA 拦截器/CookieJar/syncToWebView/MOBILE_UA）
│   │       ├── PortalApi.kt         # 门户 3DES 登录 + 应用列表 getApplication
│   │       └── JwxtApi.kt           # 教务全部接口（SSO/wapLogin/课表/成绩/学籍）
│   └── ui/
│       ├── AppRoot.kt               # 冷启动静默登录编排（autoChecking→主界面/登录页）
│       ├── theme/                   # Material3 主题
│       └── screens/
│           ├── MainTabs.kt          # 5 Tab + 登录后预取（成绩/学籍卡）
│           ├── ScheduleScreen.kt    # 课表（HorizontalPager + BoxWithConstraints 自适应网格 + 学期选择）
│           ├── GradeScreen.kt       # 成绩（学年/学期 chips + 卡片列表）
│           ├── AppsScreen.kt        # 应用宫格（白名单映射本地图标 + ticket 打开）
│           ├── LeaveScreen.kt       # 请假（WebView 学工登录链 + hash 路由直达表单）
│           ├── ProfileScreen.kt     # 学籍卡 + 退出登录
│           └── LoginScreen.kt       # 登录（成功后 CredStore.save）
├── app/src/main/res/
│   ├── drawable-nodpi/              # nav_*.png（导航5图）app_*.png（应用7图）
│   ├── drawable-xxxhdpi/ic_launcher_foreground.png   # 桌面图标前景（贴纸）
│   ├── values/ic_launcher_background.xml             # #E8F1FF
│   └── mipmap-anydpi-v26/           # adaptive icon XML（引用上面的前景+背景色）
├── docs/
│   ├── 登录协议分析.md / 教务接口实测.md / 开发方案.md   # 协议逆向文档（已存资料库）
│   ├── probe/                       # 接口探测脚本 v*.py（登录/课表/成绩/考试/学工链路，排障利器）
│   └── icon-gen/                    # 图标生成提示词 + 裁切脚本 + 产物
└── HANDOVER.md                      # 本文档
```

---

## 3. 核心接口协议（最重要，逆向成果，勿丢）

### 3.1 门户登录（ehallmobile.xyc.edu.cn）

```
POST https://ehallmobile.xyc.edu.cn/api/v4/api/login
UA 必须是浏览器 UA（WAF 校验，OkHttp 默认 UA 会被拦）
payload:
  userDevice = <学号> + <毫秒时间戳>   （建议固定值避免风控，见 §6.7）
  loginName  = 3DES_ECB(随机24字节rk, pad8).hex(学号)
  key        = 3DES_ECB(MASTER_KEY,   pad8).hex(rk)
  passWord   = 3DES_ECB(rk, pad8).hex(密码)
  loginType  = "2"
MASTER_KEY = "dc651e062a92599aa1230153"（24字节，硬编码在门户前端 JS，非密）
响应: {"code":"200","data":{"CASTGC": token, ...}}
```
token 即 `CASTGC`，作为门户会话与所有 SSO 的 ticket。

### 3.2 教务 SSO（zfjwxt.xyc.edu.cn）

```
GET https://zfjwxt.xyc.edu.cn/sso/xyoauthlogin?ticket=<token>   （跟随重定向）
落地 index_initMenu.html（200）→ 教务会话建立，页面 HTML 内联全部功能菜单。
菜单正则: clickMenu('procode','type','Y码','uid','role','key','ts')...title="功能名"
关键功能码: Y253510=课表(旧)  Y305005=成绩  Y253511=课表(新)  Y357005=考试(已废弃)
进入功能: GET /jwglxt/xtgl/login_wapLogin.html?procode=..&type=..&choice=Y码&uid=..&role=..&key=..&time=..
```
**铁律**：ticket 有效期内可多次使用，但 SSO 跳转必须互斥（`JwxtApi.ensureSession` 的 Mutex），且必须跑在 IO 线程。

### 3.3 教务各功能接口（登录后 POST，均需 X-Requested-With + 落地页 Referer）

| 功能 | 接口 | 参数 | 说明 |
|---|---|---|---|
| 周次列表 | `POST /kbcx/xskbcxMobile_cxZc.html` | xnm, xqm | 返回 `[{zs:周次, rq:该周日期, ...}]` |
| 周课表 | `POST /kbcx/xskbcxMobile_cxXsKb.html` | xnm, xqm, **zs**, doType=app, kblx=空 | 服务器按 zs 过滤，返回该周 kbList；**星期字段是 `xqj`（1-7），`day` 是响应日星期勿用**；连堂看 `jcor`（"1-4"） |
| 成绩 | `POST /cjcx/cjcxMobile_cxXsgrcj.html?doType=app`（Y305005 链路） | xnm, xqm | 数组，字段 cj/jd/xf/kcmc 等；学期为空返回 len=9 非 JSON |
| xqm 编码 | 第1学期="3" 第2学期="12" 第3学期="16" | — | TermUtils.of() 已封装 |

### 3.4 学工系统（ssxt.xyc.edu.cn，请假）

```
登录链: GET http://ssxt.xyc.edu.cn/wiseduIndex.jsp?ticket=<门户token>
  → 302 casLogin.jsp?ticket=... → 302 /webApp/xuegong/index.html#/index/home（种会话）
请假路由（SPA hash，从 app.js 逆向）:
  #/qingjia/qj_s_add   请假申请表单
  #/qingjia/qj_s_index 请假记录
请假接口族: /syt/qjgl/xssq/（saveSqxj 提交等）、/syt/mobile/leave/
```
App 内实现方式：WebView 走**完整登录链**后 hash 跳转（见 `LeaveScreen.kt`）。**严禁直接 loadUrl SPA 页面**——那是伪登录（§6.5）。

### 3.5 门户应用聚合

```
POST /app/getApplication  buildingId=0&apikey=门户token → 26 个应用
点击语义（照抄门户 openThirdPage.js）: hrefType==5 → 直接开 href；其他 → href + (含?则&否则?) + "ticket=<token>"
白名单 7 项见 AppsScreen.kt 的 ALLOWED；教务系统同名双入口优先 href 含 "xyoauthlogin" 的。
```

---

## 4. 构建与运行（Windows）

```bash
# 环境：JDK17 / Android SDK 均在本机 D:\Android\（见下方路径），无全局污染
export JAVA_HOME=/d/Android/jdk17
export GRADLE_USER_HOME=/d/Android/.gradle
cd /d/workdoc/app/campus-app
/d/Android/gradle-8.9/bin/gradle :app:assembleDebug

# 安装（注意：adb 必须用 Windows 反斜杠路径，/d/... 形式会 stat 失败）
/d/Android/Sdk/platform-tools/adb.exe install -r 'D:\workdoc\app\campus-app\app\build\outputs\apk\debug\app-debug.apk'

# 真机调试（测试机: 小米/红米 720×1600，序列号 QWZPCE5HPVKFYX7P）
# 排障利器: adb logcat -s XycApp:*   （SSO/课表请求有打点）
```
版本号/签名在 `app/build.gradle.kts`（当前仅 debug 签名，**无 release 配置**）。

---

## 5. 关键设计决策

1. **数据全走旧版移动端接口**（xskbcxMobile/cjcxMobile 系）：服务器按周过滤、字段简单、PC/移动页面地址不同（用户要求移动优先，全局 UA 见 `CampusHttp.MOBILE_UA`）。
2. **课表渲染**：HorizontalPager 一周一页（跟手翻页）；BoxWithConstraints 让 7 列 weight 均分、12 节均分高度——任意分辨率一屏全显、无滚动；同槽多课并排。
3. **缓存体系**（`ScheduleCache`）：必须用 `mutableStateMapOf`（SnapshotStateMap）——普通 Map 的 put 不触发 Compose 重组（§6.3 血泪）。预加载策略：进入课表预取 N±1 周；登录后预取成绩+学籍卡。
4. **并发去重**：所有缓存 put 前走 `ScheduleCache.tryMark/unmark`，防止多 Tab 同时触发重复请求。
5. **凭证**：`CredStore`（Keystore 主密钥）存账号密码，冷启动 `AppRoot` 静默重登；「退出登录」会清凭证+缓存+WebView Cookie。

---

## 6. 踩坑记录（每条都真摔过，改代码前先看）

| # | 坑 | 症状 | 规则 |
|---|---|---|---|
| 1 | 教务返回 `day` 字段是**响应日星期**（全条相同），课程星期是 **`xqj`** | 课表全部挤在一列 | 解析课表永远用 xqj |
| 2 | 网络请求跑主线程 | `NetworkOnMainThreadException` 被 catch 吞掉 → 报"会话失效" | 所有网络必须 `withContext(Dispatchers.IO)`；改代码时别把 IO 包裹弄丢 |
| 3 | 缓存用 `mutableMapOf` | 数据写入但 UI 永远转圈 | 必须 `mutableStateMapOf` |
| 4 | `pagerState.scrollToPage` 在 Pager 未组合时调用 | 挂起死锁，全屏 spinner 永转 | 先解除 loading 再 scrollToPage |
| 5 | WebView 直接 loadUrl 学工 SPA | 伪登录（页面渲染但无会话） | 必须走完整 `wiseduIndex.jsp?ticket` 登录链 |
| 6 | 学工/缴费是 http 明文 | `ERR_CLEARTEXT_NOT_PERMITTED` | Manifest 已开 `usesCleartextTraffic`，别删 |
| 7 | 登录 userDevice 每次都用新时间戳 | 疑似触发门户新设备风控（1111 短信验证） | 若遇到登录要求短信验证：改为固定 userDevice 并实现短信登录分支 |
| 8 | 并发 SSO（登录后多 Tab 同时触发） | ticket 互踩、会话失效 | ensureSession 已加 Mutex，勿移除 |
| 9 | OkHttp 默认 UA 被学校 WAF 拦 | SSO/接口 403/异常 | UA 拦截器已全局加，勿移除 |
| 10 | 资料库/长耗时写操作返回 524/超时 | 看起来失败但可能已成功 | 先查状态再重试，避免重复提交 |
| 11 | adb install 用 Git Bash 的 `/d/...` 路径 | `failed to stat: No such file` | 必须传 Windows 路径 `'D:\...'` |
| 12 | 接口偶发网络超时（校园网丢包） | 单次请求失败 | 重试按钮已有；不要把超时阈值调低于 20s |

---

## 7. 待办事项（M4 Roadmap，按建议优先级）

1. **请假全流程实测**：提交一笔请假 + 销假（WebView 链路已通，业务流未实测）。
2. **7 个应用 ticket 跳转逐个验证**：门户列表里每个应用的 href/hrefType 处理是照抄门户 JS 的，个别系统可能不认（记录失败应用名定向修）。
3. **release 签名配置**：生成 keystore、配置 signingConfig、出 release 包（当前仅 debug）。
4. **桌面"今日课程"小组件**：Glance 实现，数据源复用 ScheduleCache（需把缓存落到磁盘以供小组件进程读取——目前是进程内存）。
5. **成绩发布提醒**：WorkManager 定时拉成绩比对 + Notification。
6. **可选增强**：校历精确开学日（当前用 cxZc 的 rq 推算，已够准）、考试功能（已删，接口留档在 git 历史）、首页聚合页、深色主题。
7. **资料库**：三份技术文档在 WorkBuddy 资料库"我的文档"（登录协议分析/教务接口实测/开发方案——前两份是空壳待补内容或直接看本仓库 docs/ 下的 md 源文件），另有 4 个空壳节点待手动删除。

---

## 8. 测试与排障

- **测试账号**：找原负责人要（不写入本文档）；或用你自己的新余学院账号。
- **接口探测脚本**：`docs/probe/v*.py`（v5 多学期课表+成绩、v10-v13 各功能链路、v14 学工、v12 登录原始响应诊断）。跑法：
  ```bash
  /c/Users/octmicy/.workbuddy/binaries/python/envs/default/Scripts/python.exe docs/probe/v12.py <学号> <密码>
  ```
  依赖：requests、pycryptodome（venv 已装）。**脚本与 App 行为不一致时以脚本实测为准**。
- **日志**：`adb logcat -s XycApp:*`（SSO 结果、每次课表请求、静默登录结果）。
- **截图存档**：`docs/probe/` 各阶段真机截图。

## 9. 联系与知识源

- 协议原始文档：本仓库 `docs/登录协议分析.md`、`docs/教务接口实测.md`
- 门户前端 JS（加密逻辑出处）：`https://ehallmobile.xyc.edu.cn` 页面源码 + `md5.js`/`login chunk`
- 学工 SPA：`https://ssxt.xyc.edu.cn/webApp/xuegong/js/app.*.js`（路由/接口可全文检索）
- WorkBuddy 资料库：搜"新院助手"
