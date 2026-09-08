# No OTG Media Folders

阻止 Android MediaProvider 在 USB OTG / SD 卡（可移动存储）挂载时自动创建默认公共目录（`DCIM`、`Download`、`Documents`、`Pictures`、`Movies`、`Music`、`Podcasts`、`Ringtones`、`Notifications`、`Alarms`、`Audiobooks`、`Recordings`）。

内部存储（primary volume）**不受影响**，保持 Android 原始行为。

## 适用环境

- **系统**：Android 16（仅针对此版本开发与测试）
- **Root**：APatch / KernelSU / Magisk（需要 Zygisk）
- **框架**：ZygiskNext + LSPosed（支持 LSP API / Xposed API 82+）

## 原理

AOSP（Android 16 main 分支）源码：

```java
// MediaProvider.java
private void ensureDefaultFolders(MediaVolume volume, SQLiteDatabase db) {
    if (volume.shouldSkipDefaultDirCreation()) {   // ← 判断点
        return;
    }
    // ... 遍历 DEFAULT_FOLDER_NAMES 创建目录
}

// MediaVolume.java
public boolean shouldSkipDefaultDirCreation() {
    return isExternallyManaged() || isUnreliablePublicVolume();
}

private boolean isUnreliablePublicVolume() {
    return isPublicVolume() && getPath() != null
            && getPath().getAbsolutePath().startsWith("/mnt/");
}
```

普通 USB OTG / SD 卡是 **public volume**（`StorageVolume.isPrimary() == false`），但挂载路径是 `/storage/XXXX-XXXX` 而非 `/mnt/...`，因此 `isUnreliablePublicVolume()` 返回 `false`，默认目录仍会被创建。

本模块 Hook `MediaVolume.shouldSkipDefaultDirCreation()`：当判定为 **public volume 且非 externallyManaged** 时，强制返回 `true`，从而跳过默认目录创建。

- ✅ USB OTG → 跳过
- ✅ 可移动 SD 卡 → 跳过
- ✅ 内部存储（primary）→ 保持原样

## 编译

### GitHub Actions（推荐，无需本地环境）

1. 将本仓库推到 GitHub
2. 点击 **Actions** 标签 → 手动运行 `Build APK` workflow
3. 下载构建产物 `NoOtgMediaFolders`（artifact 中的 APK）

### 本地编译

需要 Android Studio + JDK 17 + Android SDK 35。

```bash
gradle :app:assembleRelease
```

产物位于 `app/build/outputs/apk/release/`。

## 安装

1. 安装 LSPosed（基于 ZygiskNext，适用于 APatch / KernelSU / Magisk）
2. 安装本模块 APK
3. 在 LSPosed 中启用本模块，**作用域勾选 `com.android.providers.media`（媒体存储）**
4. 重启手机（或重启 MediaProvider）

## 验证是否生效

插入空 U 盘，观察根目录：

- ❌ **失效**：自动出现 `DCIM/`、`Download/`、`Pictures/` 等
- ✅ **生效**：根目录保持为空

查看日志：

```bash
adb shell logcat -s NoOtgMediaFolders
# 应看到 "Hooked MediaVolume.shouldSkipDefaultDirCreation" 等输出
```

对比验证：在 LSPosed 中禁用本模块并重启后，插入 U 盘目录又出现 → 说明 Hook 确实在起作用。

## 如果失效：定位目录的真正创建者

`Download/` 等目录有时并非 MediaProvider 创建（可能是 DownloadProvider / DocumentsUI）。用 inotify 监控：

```bash
adb shell
inotifywait -m -e create /storage/XXXX-XXXX/
```

同时抓日志：

```bash
adb shell logcat | grep -iE "mkdir|Download|MediaProvider|DownloadProvider|ExternalStorageProvider"
```

若发现是 `com.android.providers.downloads` 等其他进程创建，需在 Hook 中补充对应包的作用域与 Hook 点。

## 卸载

在 LSPosed 中禁用/卸载本模块，卸载 APK，重启后系统恢复原始行为。本模块**不修改任何用户文件，不删除任何目录**。

## 安全说明

- 不修改、不删除用户 U 盘/SD 卡上已有的任何文件与目录
- 只影响「MediaProvider 自动创建不存在的默认目录」这一行为
- 不影响 MediaStore、相册、文件管理器、MTP、USB OTG 读写
