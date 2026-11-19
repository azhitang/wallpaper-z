# 壁纸z：Cursor 交接说明

最后更新：2026-11-16。这个文件供 Cursor 在继续开发前读取；它不包含任何账号、令牌、签名密码或私钥。

## 当前状态

- GitHub 仓库：<https://github.com/azhitang/wallpaper-z>
- 当前分支：`main`
- 当前提交：`ced4ea4 Add per-theme video completion or interval playback setting`
- 最新发布：`v0.6.4`，`versionCode 15`，`versionName 0.6.4`
- 应用 ID：`com.wallpaperz.app`
- 已发布 APK：<https://github.com/azhitang/wallpaper-z/releases/latest>
- 工作区在生成此文件时没有未提交改动。

壁纸z是仅管理桌面动态壁纸的 Android 应用。第一期不做锁屏、账号、在线图库、服务器端壁纸内容或小米实况照片解析。

## 已实现的产品行为

- 本地照片和普通视频混播；素材从小米相册选择后复制进应用私有目录。
- 主题分组：首页分类卡片横向露出两张半，进入主题后为三列网格。多个勾选主题按整轮规则混播。
- 每主题独立保存顺序／随机／偏爱新图、定时／亮屏／解锁、间隔与视频时长策略。
- 全局整轮无重复：全部已选主题的素材都播放一次后才开始下一轮；主题间轮流取下一张；跨轮尽量避免相邻重复。
- 视频策略：定时模式下可选“播完切换”或“跟随间隔”。前者播放一遍后换图，后者短视频循环、长视频在轮播间隔到时切换。
- 长按缩略图多选，可批量删除或移动主题；详情页预览可左右滑动，视频静音循环。
- 真实桌面为动态壁纸，照片与视频切换约 650ms 淡入淡出；需用户第一次在系统预览页设置为主屏幕动态壁纸。
- 1×1 透明桌面小组件，只显示右侧彩色旋涡图标；点击直接切换下一张。
- 自动轮播使用 `specialUse` 前台服务及“下一张／暂停”通知；没有精确闹钟或唤醒锁，系统或厂商可能延后或回收服务。
- App 内更新：每天首次回到前台静默检查；设置页可手动检查、下载、校验 SHA-256/包名/版本/固定签名，随后调用系统安装器。

产品约束：导入入口必须是小米相册 `com.miui.gallery`，不回退到 Android Photo Picker、文件管理器或全媒体库权限。小米 14 Pro / 澎湃 OS 3.0.308.0 的省电与后台回收仍需真机持续验证。

## 代码地图

| 位置 | 作用 |
| --- | --- |
| `app/src/main/java/com/wallpaperz/app/MainActivity.kt` | Compose 入口、页面导航、前台更新检查生命周期入口 |
| `WallpaperScreens.kt`、`ZDesign.kt`、`ThemeDialogs.kt` | iOS 风格的首页、主题网格、预览、设置及统一自定义控件 |
| `CarouselState.kt` | 可序列化的数据模型；`VideoTiming`、主题设置与全局轮播状态 |
| `RotationLogic.kt` | 纯轮播规则：整轮去重、主题轮转、随机与偏爱新图 |
| `CarouselRepository.kt`、`RotationController.kt` | DataStore 持久化与所有切换入口的统一控制 |
| `RotationService.kt` | 前台服务、定时／亮屏／解锁调度和通知操作 |
| `LiveWallpaperService.kt` | OpenGL 动态壁纸、视频播放及淡入淡出 |
| `WallpaperMedia.kt` | 导入副本、照片处理、视频封面和素材校验 |
| `XiaomiGalleryPicker.kt` | 强制调用小米相册并接收返回的 URI |
| `NextWidget.kt` | 1×1 桌面小组件 |
| `AppUpdates.kt`、`UpdateRelease.kt`、`UpdateUi.kt` | GitHub 更新清单、下载验证和设置页 UI |
| `app/src/test` | 轮播与更新元数据单元测试 |
| `app/src/androidTest` | 模拟器端到端测试；视频测试素材只在测试 APK 中 |
| `.github/workflows/release.yml` | 仅在 `v*` tag 或手动触发时构建、签名、创建 GitHub Release |
| `scripts/build_release.py` | 使用仓库外固定签名构建正式 APK |
| `scripts/prepare_release.py` | 从正式 APK 生成 GitHub 更新清单 `update.json` |
| `signing/README.md` | 固定签名、密钥迁移和 GitHub Actions Secrets 的操作说明 |

## 技术环境和构建

- Kotlin 2.2.21、Jetpack Compose、Material 3、DataStore、Coil 3、Media3。
- Gradle 8.13、AGP 8.13.2、JDK 17、compile/target SDK 36、min SDK 34。
- Windows 开发机可载入环境：`. 'E:\Android\android-env.ps1'`。

常用开发验证：

```powershell
. 'E:\Android\android-env.ps1'
.\gradlew.bat assembleDebug testDebugUnitTest lintDebug assembleDebugAndroidTest
adb -s emulator-5554 install --no-streaming -r app\build\outputs\apk\debug\app-debug.apk
adb -s emulator-5554 install --no-streaming -r app\build\outputs\apk\androidTest\debug\app-debug-androidTest.apk
adb -s emulator-5554 shell am instrument -w -r com.wallpaperz.app.test/androidx.test.runner.AndroidJUnitRunner
```

正式发布验证使用：

```powershell
. 'E:\Android\android-env.ps1'
python scripts/build_release.py `
  '-Dorg.gradle.jvmargs=-Xmx1024m -XX:ActiveProcessorCount=2 -XX:TieredStopAtLevel=1 -XX:HeapBaseMinAddress=8g -Dfile.encoding=UTF-8' `
  '-Pkotlin.compiler.execution.strategy=in-process' `
  testReleaseUnitTest lintRelease assembleDebugAndroidTest
```

开发调试签名和正式签名不能互相覆盖。更新或签名迁移测试必须使用正式签名 APK；不要删除用户的应用数据、私有素材或仓库外的签名目录。

## 发布与在线更新

1. 在 `app/build.gradle` 递增 `versionCode` 和 `versionName`。
2. 更新 `RELEASE_NOTES.md` 与设置页显示的版本文字。
3. 提交并推送 `main`，再创建并推送匹配的 `v<versionName>` 标签。
4. GitHub Actions 从 Secrets 读取正式签名，在 Release 附加 APK、`.sha256` 和 `update.json`。

更新源当前固定为：

```text
https://github.com/azhitang/wallpaper-z/releases/latest/download/update.json
```

下载慢是已知体验问题。Gitee 可以作为国内镜像，但尚未接入；如果实现，需同步发布 APK 与更新清单，并保持 App 对 SHA-256 和固定正式签名的校验。不要把 GitHub 或 Gitee 访问令牌写进源码或 APK。

GitHub Actions 的机密变量名称为 `ANDROID_KEYSTORE_BASE64` 和 `ANDROID_SIGNING_CONFIG_BASE64`。其值及本机密钥位置绝不能写入 Git、日志或本文件。

## 测试基线

`v0.6.4` 已完成：

- 37 个单元测试通过。
- Release Lint：0 error，9 个已有 warning。
- 9 个视频策略模拟器测试通过，包括视频播放完成、跟随 1 分钟间隔、运行中切换策略、屏幕事件不受影响。
- GitHub Release CI 成功：`42783066076`。
- 已确认正式签名覆盖升级保留既有图片和 DataStore 数据。

最新详细记录：`docs/verification-v0.6.4.md`。在线升级流程记录：`docs/verification-v0.6.3.md`。

## 继续开发前的规则

- 先阅读 `README.md`、本文件、相关实现和已有测试，再改动代码。
- 遵守仓库要求：只做当前需求的最小改动；不要为假设需求增加兼容路径、胶水代码或过度设计。
- 轮播入口必须继续共用 `RotationController`，页面、小组件、通知和自动事件不得各自维护游标。
- 修改轮播规则时优先补纯单元测试；涉及动态壁纸／视频／服务时，再补针对性的模拟器测试。
- 更新模块的完整性和签名验证不得弱化。系统安装确认是 Android 必需流程，不能静默安装。
- 不要将 APK、密钥、签名配置、`deliverables/` 或 `.tools/` 目录提交到 Git。

## 适合 Cursor 的首条指令

在 Cursor Chat 粘贴：

```text
请先阅读 HANDOFF.md、README.md、RELEASE_NOTES.md 和 git log。当前项目是壁纸z Android App，必须保留现有正式签名升级兼容性和轮播整轮无重复规则。请在改动前说明你将修改的文件，并在改动后运行与变更匹配的测试。不要提交密钥、APK 或构建产物。
```
