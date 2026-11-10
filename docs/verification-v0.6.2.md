# 壁纸z v0.6.2 验证记录

日期：2026-11-10。应用 ID：`com.wallpaperz.app`，versionCode：13。

## GitHub 与发布

- 用户授权将仓库改为公开：[源码仓库](https://github.com/azhitang/wallpaper-z)。
- [Release 自动构建成功](https://github.com/azhitang/wallpaper-z/actions/runs/42775526847)：安装 SDK、恢复临时签名材料、构建、单元测试、Lint、固定签名、生成更新清单和发布全部通过。
- [v0.6.2 Release](https://github.com/azhitang/wallpaper-z/releases/tag/v0.6.2) 包含正式签名 APK、SHA-256 文件与 `update.json`；源码由 GitHub 提供版本归档。
- 更新清单可匿名读取，不需要 App 保存 GitHub Token。
- 私钥与密码以两个 Actions Secrets 保存，未提交 Git、未加入源码归档或 Release 附件。构建完成后清理 runner 的临时签名目录。
- 准备过程中出现的上下文引用、过期 SDK 工具问题已修正；v0.6.0、v0.6.1 未发布 Release，首次正式发布为 v0.6.2。

## 自动化检查

- 本地 34 项单元测试通过：20 项轮播逻辑、14 项更新元数据和完整性/签名策略检查。
- 本地 Android Lint：0 错误、9 警告。GitHub Actions 同样通过单元测试与 Lint。
- API 37 / Android 17 模拟器的 7 项更新仪器测试全部通过：版本比较、网络错误提示、损坏文件拒绝、非 APK 拒绝、真实 GitHub 清单读取、设置页手动检查、签名包通过 Content URI 打开系统安装器。
- 升级前后分别执行只读签名/数据快照测试，确认当前正式证书、旧签名历史与实际安装版本。

## 真实在线覆盖更新

模拟器安装本地 v0.6.0 后，直接使用 App 的正式更新入口：

1. 读取 GitHub 的最新清单，发现 v0.6.2，展示版本说明与 29.2 MB 大小。
2. 从版本固定的 GitHub Releases 地址下载完整 APK，显示进度。
3. App 校验大小、SHA-256、包名、版本号及正式证书，通过后显示“安装更新”。
4. 首次引导允许“安装未知应用”；返回 App 后进入真实系统安装器，显示“Update this app?”。
5. 点击系统 Update 按钮完成覆盖安装，PackageManager 确认 v0.6.2 / versionCode 13。
6. 升级前后 6 个壁纸媒体与配置文件 SHA-256 完全一致，主题、素材顺序、当前壁纸及整轮进度 JSON 完全一致。没有卸载、清除数据或回灌备份。
7. 升级后设置页手动检查显示“已是最新版本”。

电脑已匿名读取更新清单并下载发布附件。模拟器首次请求遇到域名解析失败，确认电脑使用本机代理而模拟器未接入后，仅为开发模拟器配置代理并完成上述验证。App 未内置代理，也未降低 TLS 或签名校验要求。

## 发布包标识

- APK：`wallpaperz-v0.6.2.apk`，29,244,845 字节。
- APK SHA-256：`29c4cd4327fa06de985fe79135b9f6a8db87cb2edba4875342d16eb45c1aa35d`。
- 正式签名证书 SHA-256：`5b7268a0347aa5b4ceb5d212d7f2ce32fe7ec73ca7074f18ba5b6a64b35e1490`。

## 使用与待验证项

已有旧版用户需先手动覆盖安装 v0.6.2 一次，后续发布新版即可在 App 内更新。每次新版本发布仍需递增版本号、提交代码并推送对应标签，用户安装仍需系统确认。

小米 14 Pro / 澎湃 OS 3.0.308.0 的安装权限界面、动态壁纸权限保留及后台限制仍待真机验证。手机自身需能访问 GitHub 下载地址；下载失败可手动重试。
