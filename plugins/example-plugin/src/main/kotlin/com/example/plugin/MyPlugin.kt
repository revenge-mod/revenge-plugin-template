@file:JvmName("MyPlugin")

package com.example.plugin

import android.app.AlertDialog
import io.github.revenge.bridge.asDelegate
import io.github.revenge.plugins.plugin
import io.github.revenge.xposed.api.registerNativeMethod
import java.io.File

/**
 * Example native plugin entry point.
 *
 * The plugin is a top-level `val` built with the [plugin] DSL.
 * If there are multiple fields, the plugin loader resolves the first [io.github.revenge.plugins.PluginBuilder] value
 * exposed by the class specified by `dist.android.class` in `manifest.json`.
 *
 * `@file:JvmName("MyPlugin")` names the compiled file-facade class so `dist.android.class` can read
 * as `com.example.plugin.MyPlugin` instead of the implicit `...MyPluginKt`.
 */
@Suppress("UNUSED")
val myPlugin = plugin {
    // Runs before the JS bundle is executed. Register bridge methods and install hooks here.
    // [this] is a PluginScope: log, manifest, appInfo, classLoader, bridge, etc.
    start {
        log.i("Loaded ${manifest.name} (${manifest.id}) in ${appInfo.packageName}")
        
        val file = File(pluginDir, "assets/test.txt")
        log.i("Reading test.txt from plugin storage: ${file.readText()}")

        // Expose a native method callable from the plugin's JS side via the Revenge bridge.
        registerNativeMethod("${manifest.id}.ping") { args ->
            log.i("ping($args)")
            "pong"
        }

        withAppActivity { activity ->
            registerNativeMethod("${manifest.id}.alert") { rawArgs ->
                val args = rawArgs.asDelegate()
                val title: String by args.string()
                val message: String by args.string()
                val negativeButton: String? by args.stringOrNull()

                AlertDialog.Builder(activity)
                    .setTitle(title)
                    .setMessage(message)
                    .setPositiveButton(android.R.string.ok) { d, _ -> d.dismiss() }
                    .apply {
                        if (negativeButton != null) {
                            setNegativeButton(negativeButton) { d, _ -> d.dismiss() }
                        }
                    }
                    .show()

                // The last expression is the return value. You must return a value serializable by React Native.
                Unit
            }
        }
    }

    // Runs when the plugin is torn down.
    stop {
        log.i("Unloaded ${manifest.id}")
    }
}

