# 新院助手 xinyuan-app

新余学院校园服务安卓客户端（**非官方、开源**）。原生 Kotlin + Jetpack Compose，数据直连学校各系统，无自建服务端，凭证本机加密存储。

## 功能

- **课表**：一屏式周课表（7 列 × 12 节自适应任意分辨率）、跟手翻页切周、学期切换、点击课程卡片查看详情（教师 / 教室 / 时间区间 / 周次 / 学分）
- **学期课表**：整学期全部课程的网格总表，同一时段多门课堆叠显示
- **自定义课程**：课表上没有的课（社团 / 讲座 / 自习）手动添加，支持按节次或按具体时间、单双周重复
- **成绩**：学年 + 学期两级切换、总学分 / 绩点汇总卡片、彩色分数徽标
- **应用**：校园门户应用聚合，带 ticket 免密跳转
- **请假**：内嵌学工系统，直达请假申请 / 记录
- **课程小组件**：2×2 / 2×3 / 3×3 / 3×4 / 4×4 五种预设规格，可翻看任意日期，自由拖拽缩放
- **我的**：学籍卡、自定义头像、赞助与反馈

## 下载

到 [Releases](https://github.com/octmicy/xinyuan-app/releases) 页面下载最新的 APK 安装（需 Android 8.0+）。

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

## 问题反馈

- 到 [Issues](https://github.com/octmicy/xinyuan-app/issues) 提交（有现成模板，照着填即可）
- 或发邮件至 2335260621@qq.com（App「我的」页面可一键复制反馈模板）

## 免责声明

本项目为课程学习与技术交流用途，与学校官方无关；接口协议来自对公开前端页面的分析；请仅使用本人账号登录，勿作商业用途。使用本项目产生的一切后果由使用者自行承担。

## 赞助

如果这个项目对你有帮助，欢迎在 App「我的」页点击 **赞助开发者** 请作者喝杯奶茶 🧋，或到 GitHub 给个 [Star](https://github.com/octmicy/xinyuan-app)！
