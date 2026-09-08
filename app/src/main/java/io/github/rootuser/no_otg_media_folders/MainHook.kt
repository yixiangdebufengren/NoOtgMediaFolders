package io.github.rootuser.no_otg_media_folders

import android.util.Log
import de.robv.android.xposed.IXposedHookLoadPackage
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedBridge
import de.robv.android.xposed.XposedHelpers
import de.robv.android.xposed.callbacks.XC_LoadPackage

/**
 * 主入口：阻止 MediaProvider 在 USB OTG / SD 卡（public volume）挂载时自动创建默认目录。
 *
 * 原理（基于 AOSP Android 16 main 分支源码）：
 *   MediaVolume.shouldSkipDefaultDirCreation() {
 *       return isExternallyManaged() || isUnreliablePublicVolume();
 *   }
 *   其中 isUnreliablePublicVolume() = isPublicVolume() && path.startsWith("/mnt/")
 *
 * 普通 USB OTG / SD 卡是 public volume，但路径是 /storage/XXXX-XXXX 而非 /mnt/...，
 * 因此默认会返回 false，导致默认目录仍被创建。
 *
 * 本模块 Hook 该方法：当 volume 是 public 且非 externallyManaged 时，强制返回 true，
 * 从而跳过默认目录创建。内部存储（primary）是非 public volume，不受影响。
 */
class MainHook : IXposedHookLoadPackage {

    companion object {
        private const val TAG = "NoOtgMediaFolders"
        private const val MEDIA_PROVIDER_PACKAGE = "com.android.providers.media"

        // Android 16 的类名
        private const val MEDIA_VOLUME_CLASS = "com.android.providers.media.MediaVolume"
    }

    override fun handleLoadPackage(lpparam: XC_LoadPackage.LoadPackageParam) {
        if (lpparam.packageName != MEDIA_PROVIDER_PACKAGE) return

        Log.i(TAG, "Hook loaded for $MEDIA_PROVIDER_PACKAGE")

        try {
            hookShouldSkipDefaultDirCreation(lpparam.classLoader)
        } catch (t: Throwable) {
            XposedBridge.log("[$TAG] Failed to hook MediaVolume.shouldSkipDefaultDirCreation: ${t}")
            Log.e(TAG, "Hook failure", t)
        }
    }

    private fun hookShouldSkipDefaultDirCreation(classLoader: ClassLoader) {
        val mediaVolumeClass = XposedHelpers.findClass(MEDIA_VOLUME_CLASS, classLoader)

        XposedHelpers.findAndHookMethod(
            mediaVolumeClass,
            "shouldSkipDefaultDirCreation",
            object : XC_MethodHook() {
                private var shouldSkip = false

                override fun beforeHookedMethod(param: MethodHookParam) {
                    val mediaVolume = param.thisObject ?: return
                    shouldSkip = false

                    try {
                        // 读取字段。字段名在 Android 16 是 mPublicVolume / mExternallyManaged
                        val isPublic = readBooleanField(mediaVolume, "mPublicVolume")
                        val isExternallyManaged = readBooleanField(mediaVolume, "mExternallyManaged")

                        if (isPublic && !isExternallyManaged) {
                            // USB OTG / 可移动 SD 卡 → 强制跳过默认目录创建
                            shouldSkip = true
                        }
                    } catch (t: Throwable) {
                        // 字段读取失败时不做干预，走原始逻辑，保证不破坏 MediaProvider
                        Log.w(TAG, "Failed to inspect volume fields, fallback to original: $t")
                    }
                }

                override fun afterHookedMethod(param: MethodHookParam) {
                    // 在原方法执行后强制覆盖返回值，确保 Hook 生效
                    if (shouldSkip) {
                        param.result = true
                        Log.d(TAG, "Skipping default dir creation for public volume")
                    }
                }
            }
        )

        Log.i(TAG, "Hooked MediaVolume.shouldSkipDefaultDirCreation")
    }

    /**
     * 读取 boolean 字段，带多字段名容错（兼容不同 Android 版本的字段命名差异）。
     */
    private fun readBooleanField(obj: Any, fieldName: String): Boolean {
        return try {
            XposedHelpers.getBooleanField(obj, fieldName)
        } catch (e: NoSuchFieldError) {
            // 尝试通过反射遍历同名/近名字段
            findBooleanFieldByReflection(obj, fieldName)
        }
    }

    private fun findBooleanFieldByReflection(obj: Any, canonicalName: String): Boolean {
        val cls: Class<*> = obj.javaClass
        var current: Class<*>? = cls
        while (current != null) {
            for (f in current.declaredFields) {
                if (f.type == java.lang.Boolean.TYPE || f.type == java.lang.Boolean::class.java
                ) {
                    // 匹配字段名（忽略 m 前缀差异）
                    val n = f.name
                    if (n.equals(canonicalName, ignoreCase = true) ||
                        n.equals(canonicalName.removePrefix("m"), ignoreCase = true)
                    ) {
                        f.isAccessible = true
                        return f.getBoolean(obj)
                    }
                }
            }
            current = current.superclass
        }
        throw NoSuchFieldException("Boolean field not found: $canonicalName in ${cls.name}")
    }
}
