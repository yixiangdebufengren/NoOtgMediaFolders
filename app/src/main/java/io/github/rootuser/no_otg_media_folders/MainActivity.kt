package io.github.rootuser.no_otg_media_folders

import android.app.Activity
import android.os.Bundle

/**
 * 空壳 Activity，仅用于让 LSPosed 正确识别本模块。
 * 打开后无实际 UI，Hook 逻辑在 MainHook 中通过 LSPosed 注入。
 */
class MainActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        finish()
    }
}