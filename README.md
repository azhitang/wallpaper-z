# 壁纸z · 0.6.1

本地桌面壁纸轮播 Android App，支持主题分组、普通照片和视频混播、顺序／随机／偏爱新图、定时／亮屏／解锁切换、1×1 图标小组件以及真实桌面淡入淡出。只处理主屏幕，不包含小米实况照片解析、在线图库或账号。

## 使用

1. 安装 [GitHub Releases 中的最新 APK](https://github.com/azhitang/wallpaper-z/releases/latest)。正式版本携带旧调试签名到固定正式签名的轮换证明，支持覆盖此前同一旧签名版本，保留已导入的素材和设置。不要卸载旧版或清除数据。
2. 首页按主题显示分类卡片，每组横向显示两张完整竖图和半张第三张，向左滑动可看更多；不足三项时按实际数量显示。点击分类标题或缩略图进入该主题的三列网格详情，再点右上角“＋”打开小米系统相册，选择照片／视频并确认后复制到此主题。支持单选和多选，取消选择不会改动素材。此版不使用应用内相册、Android Photo Picker 或文件管理器，也不申请整个媒体库的读取权限。素材复制到应用私有目录，删除相册原文件不影响播放。设备必须安装并启用小米相册；无法打开时会显示提示，不会转到其他选择器。
3. 点“启用动态壁纸”，在系统预览中点“设置壁纸”，选择“主屏幕／Home screen”。这是 Android 系统动态壁纸，不需要向小米申请 SDK。
4. 进入主题后轻点素材打开全屏预览，左右滑动按这个主题的排列顺序浏览照片和视频，右上角显示当前位置；首尾不循环，单个素材也可正常打开。滑动只改变预览，点击“设为桌面壁纸”才应用当前看到的素材。视频预览静音循环，滑出或离开应用时暂停。首页“此刻的桌面”也可预览其所属主题，即使当前正在编辑其他主题。首页右侧“下一张”图标和桌面 1×1 小组件使用轮播规则切换真实桌面，过渡约 650 毫秒。小组件使用彩色旋涡图案，背景透明、图案靠右，无底板或角标。旧版已添加的 2×1 小组件请移除后重新添加，桌面占位大小由启动器保存。
5. 在主题详情内长按缩略图或点击顶部“选择”进入多选，网格排版不变；继续点击其他素材勾选，底部提供全选、移组、批量删除和退出。删除只移除应用内副本。单选时点击底部左右箭头可调整顺序；返回键或“完成”退出多选，再次返回回到分类页。
6. 设置页选择顺序、随机或偏爱新图，以及定时、亮屏或解锁，再开启自动轮播。所有播放方式共用一份整轮进度：勾选主题的全部素材都播过一次后，才开始下一轮。偏爱新图按导入时间加权决定本轮先后顺序：刚导入最高 4 倍权重，7 天内线性回落至 1 倍；每个素材在一轮中仍只出现一次。排序、切换播放方式、导入、移组均不清空已播记录。跨轮避免相邻重复，只有一个参与素材时会重复该素材。预览中手动指定某张图片属于主动选择，允许再次应用，并计入本轮记录。
7. 定时模式的图片停留时间支持 1、5、15、30、60 分钟以及 3、6、12、24 小时。视频静音播放一遍，在播放器确认结束后切换，不按图片间隔截断。每次亮屏／解锁模式仍按所选屏幕事件切换，视频在等待期间静音循环。已移除 3 秒档位，原先保存为 3 秒的主题在升级时改为 1 分钟；其他档位保持原值。
8. 暂停自动轮播后保留当前素材，手动切换仍可用，当前视频继续循环。打开其他应用或熄屏后不再因为桌面不可见而暂停视频。

## 主题分组

- 首页右上角“＋”打开主题管理，可新建、重命名、删除或勾选混播主题。新建后进入空主题详情，便于导入。原有素材、设置与进度保留，无需重新导入。
- 可勾选多个主题混播，按勾选顺序在尚未播完的主题之间轮流取下一项；素材较少的主题播完后等待其他主题。每组独立保存素材顺序、播放策略、触发方式和间隔，整轮已播记录统一持久化，空主题自动跳过。
- 下一次触发仍由当前显示壁纸所属主题决定，目标是下一个还有未播素材的勾选主题。例如“自然”每分钟切换、“动漫”每次解锁切换：显示自然时，按正常时间满一分钟后轮到动漫；显示动漫后，等下一次解锁再从下一个仍有未播素材的主题取一项；若自然已播完本轮，会先跳过自然。
- 点击分类卡片进入要编辑的主题，不会立即替换正在显示的壁纸；分类页的纵向滚动位置在返回时保留。详情顶部齿轮打开此主题的轮播设置，返回后仍在该主题。应用内“换一张”、小组件和通知均使用同一套混播规则；预览中应用某张素材可直接显示它。
- 从相册导入的素材归入当前主题。网格长按多选后，可点“移组”放入其他主题，移动只调整分组，不重复复制文件。
- 自动轮播开关控制整套混播。新建主题默认勾选，导入后即可加入混播；所有勾选主题均为空，或取消全部主题后会暂停自动轮播。
- 删除主题会移除组内素材副本，相册原文件保留。至少保留一个主题。

未启用壁纸引擎时，小组件会引导打开应用完成设置。视频编码须由手机系统解码器支持，常见 MP4/H.264 可用，不支持的素材会提示导入失败。动态 GIF 等请先导出为普通视频；实况照片只按小米相册提供的静态照片导入，不提取其中片段。

小米手机若点“启用动态壁纸”没有响应，请在壁纸z的应用信息 → 权限管理／其他权限中允许“动态壁纸服务”，再回应用重试。用户已在小米 14 Pro 上确认此方法可用。可见相册与私密素材是否可选，由小米相册决定；此入口不绕过其隐私限制。小米相册的实际打开、多选、视频返回及隐藏相册范围，仍需在小米 14 Pro／澎湃 OS 3.0.308.0 上确认。模拟器只验证选择结果回传后的处理，不代表已验证小米相册本体。

## 存储

- 照片副本／视频封面：`/data/user/0/com.wallpaperz.app/files/<UUID>.image`。
- 视频副本：同目录 `<UUID>.video`。
- 列表、进度和设置：`files/datastore/carousel.json`。
- 应用私有文件，普通文件管理器不可直接访问。清除应用数据或卸载会删除导入素材，相册原文件保留。联网仅用于检查版本和下载更新，不上传图库或轮播设置。

## 后台行为

自动轮播使用带“下一张／暂停”操作的前台服务；桌面图像由系统绑定的动态壁纸服务绘制。前台服务不保证永远存活。拒绝通知权限仍可开启轮播，通知栏可能不显示运行通知。

自动轮播不再以桌面是否可见作为暂停条件。打开全屏应用或熄屏后，图片仍按正常时间计算间隔，视频继续播放并在结束后切换；亮屏／解锁模式收到对应系统事件就切换。手动成功切换会重新计时。桌面不可见时不绘制桌面过渡动画；视频帧继续被消费，防止后台播放堵塞。壁纸窗口表面被系统销毁时，使用 1×1 离屏表面保持视频播放器与 GL 环境，窗口返回时重新连接。

不使用精确闹钟或唤醒锁；深度休眠、进程回收等系统行为仍可能延迟切换。进程重建后按已保存的当前素材和整轮记录继续，不因重启轮播队列而提前重复。视频播放器如随进程被销毁会从头播放。

开机和正常服务重建后按保存的自动开关恢复；有解锁密码时，首次解锁后才能访问数据。系统强行停止后需重新打开应用。小米真机仍需验证澎湃 OS 的自启动、省电策略和后台回收，可允许后台自启动并将省电策略设为“无限制”。

## 构建与测试

Kotlin + Jetpack Compose + Material 3、DataStore、Coil、Media3。JDK 17、Gradle Wrapper 8.13、AGP 8.13.2、compile/target 36、Build-Tools 36.0.0，最低 Android 14。

本机 PowerShell：

```powershell
. 'E:\Android\android-env.ps1'
.\gradlew.bat assembleDebug testDebugUnitTest lintDebug assembleDebugAndroidTest
adb -s emulator-5554 install -r app/build/outputs/apk/debug/app-debug.apk
adb -s emulator-5554 install -r app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk
# 先在模拟器系统预览中将壁纸z设为主屏幕动态壁纸
adb -s emulator-5554 shell am instrument -w -r com.wallpaperz.app.test/androidx.test.runner.AndroidJUnitRunner
```

其他电脑需配置 Android SDK 的 `local.properties`、`JAVA_HOME`（JDK 17）和 `ANDROID_HOME`。首次构建需下载依赖，壁纸功能可离线使用，检查和下载更新需要网络。以上命令用于开发测试；正式发布执行 `python scripts/build_release.py`，使用固定正式签名并携带轮换证明。密钥保存在源码目录外，备份、换机和 GitHub Secrets 说明见 [固定签名说明](signing/README.md)。已迁移正式签名的安装请继续使用正式包升级。

## App 内更新与 GitHub 发布

源码仓库：[azhitang/wallpaper-z](https://github.com/azhitang/wallpaper-z)。安装包位于 [Releases](https://github.com/azhitang/wallpaper-z/releases/latest)，不提交到 Git 历史。

- 每天首次打开 App 时自动检查；没有新版本或连接失败时不会弹窗打断使用。设置 → 版本更新可随时手动检查。
- 发现新版本后显示说明与大小，点击下载。下载时请保持应用打开；中断后可重新下载。
- 下载完成会检查大小、SHA-256、包名、版本号和固定正式签名。首次安装更新需允许“安装未知应用”，返回后由系统弹出安装确认，不能静默安装。
- 首次需手动覆盖安装含更新模块的 0.6.1，之后新版本可从 App 内更新。直接覆盖安装，勿先卸载或清除数据。
- 更新清单固定地址：`https://github.com/azhitang/wallpaper-z/releases/latest/download/update.json`。APK 使用对应版本的固定下载地址，避免检查和下载之间指向不同版本。

后续发布：递增 `app/build.gradle` 的 `versionCode` 和 `versionName`，同步设置页版本文字与 `RELEASE_NOTES.md`，提交后推送对应 `v<版本号>` 标签。GitHub Actions 会运行正式构建、单元测试、Lint、固定签名及发布，自动生成 APK 校验值和 `update.json`。也可在 Actions 中手动运行同一流程；已发布的版本不会覆盖。

签名通过 `ANDROID_KEYSTORE_BASE64`、`ANDROID_SIGNING_CONFIG_BASE64` 两个 Actions Secrets 提供。明文私钥和密码不属于源码或 Release 附件，说明见 [固定签名说明](signing/README.md)。

## 实现

- `ZDesign / WallpaperScreens`：参考 iOS 现代设计的大标题、半透明悬浮导航、分组列表和统一底部面板；自绘线性图标、圆形勾选、绿色开关和滑动分段选择，按钮有按压反馈，跟随系统明暗主题。预览为沉浸式全屏；应用内不再使用默认 Material 按钮、Chip、开关、下拉菜单或 AlertDialog。界面使用系统字体，不包含 Apple 字体或图标资源。

- `CarouselState / RotationLogic`：统一整轮已播记录、跳过已完成主题、顺序／随机／新图优先的无放回抽取；旧的主题随机队列在读取时删除，不保留并行抽取路径。首次升级以当前桌面素材作为新一轮的已播项，其余素材依次参与本轮。
- `WallpaperGroup / ThemeDialogs`：主题内的素材、设置与进度，以及主题切换和移组；轮播进度跨主题统一保存。
- `CarouselRepository / RotationController`：DataStore 和统一控制入口，重复切换合并，损坏或不可解码的素材不会推进游标。
- `WallpaperMedia`：后台复制、验证、照片采样与视频封面生成。
- `XiaomiGalleryPicker`：使用标准 `ACTION_GET_CONTENT`，限定目标包 `com.miui.gallery`，请求照片／视频多选；接收单个 URI 或 ClipData 并去重，取消时返回空列表。通过 ContentResolver 读取所选 URI，不转换成文件绝对路径。已删除原应用内相册与媒体库权限路径。
- `LiveWallpaperService`：OpenGL 统一绘制照片／视频和淡入淡出，Media3 静音播放，定时模式以实际播放结束事件切换视频，不可见时继续播放并消费视频帧，窗口生命周期与视频生命周期分开处理。
- `RotationService / BootReceiver`：调度、屏幕事件、通知与恢复。
- `MainActivity / NextWidget`：Compose 界面和桌面小组件。

未保留静态 setBitmap、锁屏、SharedPreferences、WorkManager 或小米私有 SDK 路径。

应用与桌面小组件共用彩色旋涡图标，参考用户提供的旋涡图案，由内置 imagegen 生成。原图具有真实透明通道，见 `design/app-icon-swirl.png`，最终提示词和生成方式见 `design/app-icon-prompt.txt`。App 使用白底自适应图标，小组件直接使用透明原图并靠右显示；设置页也使用同一图案。未包含参考截图的草地背景或快捷方式箭头。

界面参考 [Apple 材质设计指南](https://developer.apple.com/design/human-interface-guidelines/materials)、[底部面板指南](https://developer.apple.com/design/human-interface-guidelines/sheets) 和 [按钮指南](https://developer.apple.com/design/human-interface-guidelines/buttons)，通过 Compose 实现适合 Android 的视觉与交互；半透明材质采用渐变、透明叠层和高光边界，并非 Apple 系统 Liquid Glass 的折射实现。测试视频由 FFmpeg testsrc2 生成，仅在 Android 测试 APK 中打包，主 App 不预置素材。

相册调用和多选使用 [Android 官方通用 Intent 说明](https://developer.android.com/guide/components/intents-common#Storage)。读取相册返回的内容 URI 遵循 [小米相册安全分享适配文档](https://dev.mi.com/xiaomihyperos/documentation/detail?pId=1605) 中的 ContentResolver 方式。
