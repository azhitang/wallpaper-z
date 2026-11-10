# 壁纸z固定签名

正式版本使用同一把 RSA 3072 位密钥，证书有效期约 50 年。密钥已生成，日常打包不会生成或替换密钥。

正式证书 SHA-256：

```text
5b7268a0347aa5b4ceb5d212d7f2ce32fe7ec73ca7074f18ba5b6a64b35e1490
```

## 本机保存位置

私密文件在源码目录外：`C:\Users\Administrator\.wallpaperz-signing`。目录访问权限限定为当前 Windows 用户和 SYSTEM。

- `wallpaperz-release.jks`：正式签名私钥。
- `signing-config.json`：密钥别名、密钥库密码和私钥密码；密码为随机生成，不在聊天或构建日志中输出。
- `legacy-debug.keystore`：此前交付版本使用的旧调试密钥备份，仅供恢复，不参与今后的正式构建。
- `wallpaperz.lineage`、`wallpaperz-release.pem`：轮换证明和公开证书的备份。

请将整个私密目录另行备份到可信的加密离线存储。目录中包含密码，不能公开上传或附在 GitHub Release 中。更换电脑时恢复这个目录，或设置 `WALLPAPERZ_SIGNING_DIR` 指向它；不能重新生成一把密钥代替原密钥。

源码中的本目录只包含公开证书、轮换证明和说明，可以提交 GitHub；不包含私钥或密码。`.gitignore` 已排除密钥与本地签名配置。

## 正式打包

设置 JDK 17 和 Android SDK（Build-Tools 36.0.0）。本机执行：

```powershell
. 'E:\Android\android-env.ps1'
python scripts/build_release.py
```

脚本先运行 `assembleRelease`，然后用固定密钥和轮换证明签名、验证，输出 `deliverables/wallpaperz-v<版本>.apk` 和对应 SHA-256 文件。`assembleRelease` 单独运行只生成中间的未签名 APK，不要发布它。脚本拒绝给可调试 APK 加正式签名。

可把 Gradle 参数追加在命令末尾，例如 `testReleaseUnitTest lintRelease`。每次发布需递增 `app/build.gradle` 中的 `versionCode`，并更新版本号。

## 从旧调试版本升级

旧版本证书 SHA-256：

```text
1c7ff5ca284d684218c8a7e056ac16775c6309c32460818b6f201c5706b61036
```

`wallpaperz.lineage` 由该旧密钥授权正式密钥生成，后续版本必须继续携带。应用最低 Android 14，高于签名轮换要求。正式构建仅使用新私钥，不保留旧签名构建路径，也不修改应用数据结构。

不要卸载旧版或清除数据后再升级。覆盖升级后不要尝试安装旧调试包；回到旧签名不属于本次支持的升级方向。其他密钥签名的安装不在此轮换证明内。

## GitHub 自动构建

将密钥与配置通过仓库的 Actions Secrets 保存，不提交 Git 文件。已配置两个 Secret：`ANDROID_KEYSTORE_BASE64`（密钥文件的 Base64）和 `ANDROID_SIGNING_CONFIG_BASE64`（配置文件的 Base64）。Base64 只是编码，安全性来自 Secrets 的保护。

`.github/workflows/release.yml` 将它们还原到 runner 的临时签名目录，设置 `WALLPAPERZ_SIGNING_DIR`，执行同一个构建脚本。只发布签名后的 APK、校验值和公开版本信息。不要发布临时签名目录。

仓库为 [azhitang/wallpaper-z](https://github.com/azhitang/wallpaper-z)。推送与 APK 版本一致的 `v*` 标签，或手动执行 Release 工作流，即可构建、校验并发布 APK 与 `update.json`。App 使用公开清单检查更新，下载后仍验证这把固定正式密钥。

参考：[Android 签名](https://developer.android.com/studio/publish/app-signing)、[apksigner 与签名轮换](https://developer.android.com/tools/apksigner)、[GitHub Actions Secrets](https://docs.github.com/en/actions/how-tos/write-workflows/choose-what-workflows-do/use-secrets)。
