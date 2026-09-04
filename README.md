# 新院助手 xinxue-app

新余学院校园服务安卓客户端（**非官方、开源**）。原生 Kotlin + Jetpack Compose，数据直连学校各系统，无自建服务端，凭证本机加密存储。

## 功能

- **课表**：一屏式周课表（7 列 × 12 节自适应任意分辨率）、跟手翻页切周、学年学期切换、点击课程卡片查看详情（教师 / 教室 / 周次 / 学分）
- **成绩**：学年 + 学期两级切换、总学分 / 绩点汇总卡片
- **应用**：校园门户应用聚合，带 ticket 免密跳转
- **请假**：内嵌学工系统，直达请假申请 / 记录
- **今日课程小组件**：Glance 实现，小(2×2) / 标准(4×2) / 大(4×3) 三种预设规格，支持自由拖拽缩放、按实际尺寸自适应排版，App 不在后台也能显示
- **我的**：学籍卡、自定义头像（相册选图，默认内置二次元形象）、GitHub 入口与赞助

## 构建

Android Studio 直接打开即可。命令行（需 JDK 17 + Android SDK 34）：

```bash
./gradlew :app:assembleDebug      # 产物 app/build/outputs/apk/debug/
./gradlew :app:assembleRelease    # 需要 release 签名，见下
```

依赖仓库已配置阿里云镜像优先（见 `settings.gradle.kts`）。

### Release 签名（可选）

项目根目录放置 `keystore.properties`（已被 .gitignore 排除，不会入库）：

```properties
storeFile=xyc-release.jks
storePassword=你的密码
keyAlias=你的别名
keyPassword=你的密码
```

缺少该文件时 release 构建不签名，debug 构建不受影响。

## 目录速览

```
app/src/main/java/cn/edu/xyc/campus/
├── data/
│   ├── local/     # CredStore 凭证加密 / ScheduleCache 进程内缓存 / TodayStore 小组件落盘 / AvatarStore 头像
│   ├── model/     # 数据模型
│   └── remote/    # CampusHttp(全局 OkHttp+UA+CookieJar) / PortalApi(门户) / JwxtApi(教务) / CryptoUtil(3DES)
├── ui/
│   ├── AppRoot.kt         # 冷启动静默登录编排
│   └── screens/           # 课表 / 成绩 / 应用 / 请假 / 我的 / 登录
└── widget/        # 今日课程 Glance 小组件（Exact 尺寸模式）
docs/
├── 登录协议分析.md / 教务接口实测.md / 开发方案.md   # 接口逆向文档
├── probe/         # 接口探测脚本（*.py，命令行传参，无内置凭据）
└── icon-gen/      # 二次元图标生成提示词 + 裁切脚本
HANDOVER.md        # 项目交接文档（协议要点、踩坑记录，改代码前先看）
```

## 免责声明

本项目为课程学习与技术交流用途，与学校官方无关；接口协议来自对公开前端页面的分析；请仅使用本人账号登录，勿作商业用途。使用本项目产生的一切后果由使用者自行承担。

## 赞助

如果这个项目对你有帮助，欢迎在 App「我的」页点击 **赞助开发者** 请作者喝杯奶茶 🧋，或到 GitHub 给个 [Star](https://github.com/octmicy/xinxue-app)！
