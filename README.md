# Revenge Plugin Template

This repository is a starter monorepo for external Revenge plugins. Each plugin becomes one ZIP
file. One repository can hold any number of plugins under `plugins/`.

A plugin has up to three parts. Its own `manifest.json` declares all of them:

- **`dist.android`** is native Kotlin code. The build compiles it to a DEXed JAR, and the plugin loader loads it with `DexClassLoader`.
  This code runs early, before the JS bundle.
- **`dist.script`** is the JavaScript bundle. The Revenge JS side runs it.

## Layout

Each folder under `plugins/` is one plugin. A plugin is **native** when it has a `src/main` folder.
A plugin is **JS-only** when it has only a JS entry file. A plugin can have both.

```
├── plugins/
│   ├── example-plugin/            # Native + JS
│   │   ├── manifest.json          # id, metadata, dist.* paths
│   │   ├── src/main/kotlin/com/example/plugin/MyPlugin.kt   # -> plugin.jar
│   │   └── js/index.ts            # -> index.js
│   ├── example-js-plugin/         # JS-only: no src/main, no dist.android
│   │   ├── manifest.json
│   │   └── js/index.ts
│   ├── example-library/           # Dependency example: the plugin others depend on
│   └── example-dependent/         # Dependency example: depends on com.example.library
```

The bundler looks for the JS entry in this order: `js/index.*`, then `src/index.*`, then `index.*` in the plugin folder.
Each step accepts `.ts`, `.tsx`, `.js` and `.jsx`.

To add a plugin, create `plugins/<name>/manifest.json` and add a `src/main` folder for native code, a JS entry file, or both.

## Prerequisites

- **JDK 25 or later** and the **Android SDK**, with `build-tools` and `platform 36`.
  Don't forget to set `sdk.dir` in `local.properties`, or set the `ANDROID_HOME` environment variable.
- **A JS runtime** for the JS build: [Node](https://nodejs.org/) 22.18 or later,
  [Deno](https://deno.com/) 2, or [Bun](https://bun.com/).
- **The Revenge plugin API in your local Maven repository.** Run this in the `revenge-xposed` repository:

  ```sh
  ./gradlew :api:publishToMavenLocal
  ```

  The task publishes `io.github.revenge:api`. `gradle/libs.versions.toml` pins the version.

## Build

Build and package every plugin:

```sh
./gradlew packageAllPlugins
```

The task writes one `build/dist/<id>.zip` per plugin. Each ZIP holds `manifest.json`, the dexed JAR
of a native plugin, and the JS bundle of a plugin that has one.

Build one plugin, or only one part of it:

```sh
./gradlew packageExamplePlugin              # one plugin -> build/dist/<id>.zip
./gradlew :plugins:example-plugin:dexJar    # native only -> plugins/example-plugin/build/outputs/plugin/plugin.jar
# replace bun with npm if you use node
bun install                                 # install the dependencies
bun run build                               # every JS bundle -> plugins/<name>/build/js/index.js
bun run build example-plugin                # the JS bundle of one plugin
```

Gradle derives each package task name from the folder name, example: `plugins/example-plugin/` gives `packageExamplePlugin`.

## `manifest.json`

```jsonc
{
  "format": 1,                      // manifest format version. Required. Always 1 today.
  "id": "com.example.plugin",       // also the folder name on disk
  "name": "Example Plugin",
  "description": "...",
  "author": "Your Name",
  "version": "1.0.0",               // the version of this plugin. Required.
  "dependencies": {                 // keyed by plugin id
    "revenge.api": { "version": ">=1" },
    "discord": { "version": "*" }
  },
  "dist": {
    "script": "index.js",           // relative to the plugin folder
    "android": {
      "path": "plugin.jar",         // relative to the plugin folder
      "class": "com.example.plugin.MyPlugin"  // the class that exposes the `plugin {}` val
    }
  }
}
```

### `version`

Revenge uses its own version scheme. A version is one or more integer segments.
One lowercase alphanumeric prerelease label can follow. `1.0.0`, `2026.7` and `1.2.0-beta2` are all valid.

Two rules control the order:

- A short version compares as right-padded. `1.2` equals `1.2.0`.
- A labeled version always sorts before its bare version. `1.2.0-rc` is lower than `1.2.0`.

This scheme looks like SemVer, but it is not SemVer. A CalVer-shaped version works equally well.

### `dependencies`

`dependencies` is a map, and each key is a plugin id:

```jsonc
"dependencies": {
  "com.example.library": { "version": ">=1.0 <2", "optional": false }
}
```

Every field inside the value is optional. `{}` means `{ "version": "*" }`, which accepts any version.
The key itself must still exist. The host never assumes a dependency that you do not declare.

A version range uses explicit bounds only: `<`, `<=`, `=`, `>=` and `>`, separated by spaces.
The range syntax has no `^` and no `~`. The `"*"` wildcard accepts every version.

Ranges are checked at install time, at every boot, and when the user enables the plugin.
Plugins don't load when required dependencies fail or don't satisfy the version requirements.

Dependencies are resolved **by ID** against the repositories that the user enabled.
When a dependency lives in another repository, the user must add that repository before installing the plugin.

The `example-library` and `example-dependent` pair shows this. The dependent declares `"com.example.library": { "version": ">=1" }`.
An install therefore also installs the library. The library always loads and starts first.

If the library is missing or out of range, the dependent never loads.

### Optional dependencies

`"optional": true` marks a dependency that never blocks your plugin.
Your plugin still loads when that dependency is missing, out of range, or broken.

When the dependency is present, it loads before your plugin, and its code are linked and made available to your plugin.

To detect the dependency, probe for one of its classes:

```kotlin
val themesAvailable = runCatching {
    Class.forName("com.example.themes.ThemeApi", false, javaClass.classLoader)
}.isSuccess
```

Keep all code that touches the optional API in a separate adapter class. Reference that class only after the probe succeeds.
A reference to a missing class stays safe until a code path runs it.

In JS, check if your plugin API is decorated:

```ts
start({ themes }) {
    const themesAvailable = !!themes
}
```

### Reserved IDs

Two dependency IDs are reserved.

- **`revenge.api`** resolves to the Revenge release version, which is the plugin API version.
  This dependency is **mandatory**. Constrain it to the API versions you tested, for example `">=1 <2"`.
- **`discord`** resolves to the Discord app version, for example `>=355.0`.

## Native plugins

A native plugin is a **top-level `val`** that you build with the `plugin {}` DSL. You implement no
interface, and you subclass nothing. The host reads the class that `dist.android.class` names, and
takes the first `PluginBuilder` value it exposes. You import and use a Ktor plugin value the same
way.

```kotlin
@file:JvmName("MyPlugin") // makes dist.android.class read as com.example.plugin.MyPlugin

package com.example.plugin

import io.github.revenge.plugins.plugin
import io.github.revenge.xposed.api.registerMethod

val myPlugin = plugin {
    start {
        log.i("Loaded ${manifest.id} in ${appInfo.packageName}")
        registerMethod("${manifest.id}.ping") { "pong" }
    }
    stop {
        log.i("Unloaded ${manifest.id}")
    }
}
```

A Kotlin top-level `val` compiles into a file-facade class. `MyPlugin.kt` becomes `MyPluginKt`.
The `@file:JvmName("MyPlugin")` annotation renames that facade. `dist.android.class` can then use the clean name `com.example.plugin.MyPlugin`.

If you omit the annotation, point `dist.android.class` at `...MyPluginKt`.
Declare exactly one `plugin {}` val in the file that the manifest names.

The host provides the Revenge API, the Xposed API, coroutines and the Kotlin standard library. The
build marks them `compileOnly`. The host class loader supplies them at runtime, so the JAR must not
contain them.

> **Note:** `d8` can print a `malformed kotlin.Metadata` warning. This warning is not fatal.
> The SDK metadata library is older than the Kotlin compiler. `d8` still writes a correct DEX, and the DEX loads.
> Only the rewrite of Kotlin reflection metadata stops.

## Distribution

This template is also a **plugin repository**. A repository is a static host that serves `index.json`
describing every published plugin channels, versions, absolute artifact URLs, and SHA-256 digests.

A user can add the repository URL in Revenge. Browsing, dependency resolution and updates all run on the client.

### Channels

A **channel is a named pointer into the published versions of one plugin**. In `index.json` each plugin carries both maps:

```jsonc
"channels": { "latest": "1.2.0", "testing": "1.3.0-beta" },
"versions": { "1.2.0": { /* … */ }, "1.3.0-beta": { /* … */ } }
```

`versions` holds the artifact data. `channels` only states which published version an audience gets.

The client picks a channel at install time, and it follows that pointer for update checks.
A stable user never sees a beta, because the `latest` pointer never points at one.

**Automatic pointers**:

- `latest` is the newest version with **no label**. `1.2.0` qualifies. `1.3.0-beta` never does.
- `beta` is the newest version **overall**. The generator emits it only when it differs from `latest`.
  When your newest release is stable, no `beta` pointer exists.

**Manual overrides**: Use the `channels` key in `repo.config.json`, keyed by plugin ID:

```jsonc
{
    "name": "My Plugin Repository",
    "channels": {
        "com.example.plugin": {
            "latest": "1.1.4", // keep latest on 1.1.4, for example when 1.2.0 shipped broken
            "lts": "1.0.9" // or add a channel of your own
        }
    }
}
```

These rules apply:

- The generator computes `latest` and `beta` first. It then applies your overrides.
- An override must point at a published version of that plugin. Otherwise the generator fails.
- A channel name carries no version semantics. An `lts` version is the same artifact as its plain version.
  You only point at it for longer. To promote `beta` to `latest`, edit the pointer. No rebuilds or republishes.
- **A dependency never references a channel.** A dependency constrains versions only, so a mixed-channel install can resolve.

### Serve a repository on your machine

You can test the full repository flow against your own builds: add the repository, browse it, install, and update.  
Build the ZIPs first, then start the dev server. The server regenerates the index and serves it beside the artifacts:

```sh
./gradlew packageAllPlugins   # or one package task
bun run serve                 # http://<your-lan-ip>:8080
```

Add the URL on the device as a repository. If the device cannot reach your IP, or if it blocks cleartext traffic, use loopback through ADB:

```sh
bun run serve -- --base-url http://127.0.0.1:8080
adb reverse tcp:8080 tcp:8080
```

The server rescans the dist folder on every index request.
Bump a manifest version, rebuild that plugin, and check for updates on the device. The new version will appear.
