# LSPosed 模块：Hook 类不能混淆，保持类名/方法名
-keep class io.github.rootuser.no_otg_media_folders.** { *; }
-keepclassmembers class * extends de.robv.android.xposed.IXposedHookLoadPackage { *; }
-keepclassmembers class * extends de.robv.android.xposed.callbacks.XC_LoadPackage$LoadPackageParam { *; }
