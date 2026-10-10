import com.android.build.api.dsl.LibraryExtension
import com.android.build.api.variant.LibraryAndroidComponentsExtension
import groovy.json.JsonSlurper
import java.io.File
import java.nio.file.FileSystems
import java.nio.file.Files
import java.util.concurrent.Callable
import javax.inject.Inject
import org.gradle.process.ExecOperations
import org.jetbrains.kotlin.gradle.dsl.KotlinAndroidProjectExtension

plugins {
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.kotlin.android) apply false
}

val compileSdkVer = libs.versions.compileSdk.get()
val minSdkVer = libs.versions.minSdk.get()
val javaVer = libs.versions.javaVersion.get()
val revengeApiDep = libs.revenge.api.get().toString()
val xposedApiDep = libs.xposed.api.get().toString()
val coroutinesDep = libs.kotlinx.coroutines.android.get().toString()
val androidLibraryPluginId = libs.plugins.android.library.get().pluginId

// Gradle 9 removed Project.exec from task actions
interface ExecOpsProvider {
    @get:Inject val execOps: ExecOperations
}

val rootExecOps = objects.newInstance<ExecOpsProvider>().execOps

tasks.register<Delete>("clean") {
    group = "build"
    description = "Deletes the root build directory."
    delete(layout.buildDirectory)
}

// `./gradlew clean packageAllPlugins` also runs every `:plugins:<name>:clean`,
// which would delete freshly built artifacts mid-build; the tasks below `mustRunAfter` these.
val cleanTasks = Callable { rootProject.allprojects.mapNotNull { it.tasks.findByName("clean") } }

// ---------------------------------------------------------------------------------------------
// Plugins declared under plugins/<name>/manifest.json
// ---------------------------------------------------------------------------------------------

@Suppress("UNCHECKED_CAST")
fun parseJson(file: File) = JsonSlurper().parse(file) as Map<String, Any?>

@Suppress("UNCHECKED_CAST")
fun Map<String, Any?>.obj(key: String) = this[key] as? Map<String, Any?>

fun Map<String, Any?>.str(key: String) = this[key] as? String

data class PluginDef(
    val dir: File,
    val id: String,
    val version: String,
    val jarName: String?,
    val scriptName: String?,
)

val pluginDefs = file("plugins").listFiles().orEmpty()
    .filter(File::isDirectory)
    .sortedBy(File::getName)
    .mapNotNull { dir ->
        val manifest = File(dir, "manifest.json").takeIf(File::isFile)?.let(::parseJson)
            ?: return@mapNotNull null
        val id = manifest.str("id") ?: return@mapNotNull null
        val version = manifest.str("version") ?: return@mapNotNull null
        val dist = manifest.obj("dist").orEmpty()
        PluginDef(dir, id, version, dist.obj("android")?.str("path"), dist.str("script"))
    }

// ---------------------------------------------------------------------------------------------
// JS toolchain discovery
// ---------------------------------------------------------------------------------------------

// Gradle's exec runs with a minimal, non-login PATH that usually omits user-level install dirs,
// so the well-known locations are probed explicitly.
data class JsTool(val kind: String, val exe: File) {
    val installCommand: List<String>
        get() = when (kind) {
            "bun" -> listOf(exe.absolutePath, "install", "--silent")
            "npm" -> listOf(exe.absolutePath, "install", "--no-audit", "--no-fund", "--silent")
            "deno" -> listOf(exe.absolutePath, "install", "--quiet")
            else -> error("Unknown JS tool: $kind")
        }

    val buildCommand: List<String>
        get() = when (kind) {
            "npm" -> listOf(exe.absolutePath, "run", "build")
            "bun" -> listOf(exe.absolutePath, "--bun", "run", "build")
            // Deno creates no node_modules/.bin shims, so `deno task build` cannot resolve the CLI,
            // so we run its bin file directly.
            "deno" -> listOf(
                exe.absolutePath, "run", "-A",
                "node_modules/@revenge-mod/plugin-cli/bin/revenge-plugin.js", "build",
            )
            else -> error("Unknown JS tool: $kind")
        }
}

fun findJsTool(): JsTool? {
    val kindOverride = findProperty("jsTool") as String?
    val pathOverride = (findProperty("jsToolPath") as String?) ?: System.getenv("BUN")
    if (!pathOverride.isNullOrBlank()) {
        val exe = File(pathOverride).takeIf(File::isFile) ?: return null
        return JsTool(kindOverride ?: exe.name.substringBefore('.'), exe)
    }

    val homes = buildSet {
        System.getProperty("user.home")?.let(::add)
        System.getenv("HOME")?.let(::add)
        System.getProperty("user.name")?.let { user ->
            add("/home/$user")
            add("/var/home/$user") // atomic/ostree distros: /home -> /var/home
        }
    }
    val pathDirs = System.getenv("PATH").orEmpty().split(File.pathSeparator).filter(String::isNotEmpty)

    fun candidatesFor(kind: String): List<File> = buildList {
        pathDirs.forEach { add(File(it, kind)) }
        when (kind) {
            "bun" -> homes.forEach { add(File(it, ".bun/bin/bun")) }
            // nvm keeps npm next to node, under versions/node/<ver>/bin.
            "npm" -> homes.forEach { home ->
                File(home, ".nvm/versions/node").listFiles()
                    ?.sortedDescending()
                    ?.forEach { add(File(it, "bin/npm")) }
            }
            "deno" -> homes.forEach { add(File(it, ".deno/bin/deno")) }
        }
        add(File("/usr/local/bin/$kind"))
        add(File("/opt/homebrew/bin/$kind"))
    }

    val kinds = kindOverride?.let(::listOf) ?: listOf("bun", "npm", "deno")
    return kinds.firstNotNullOfOrNull { kind ->
        candidatesFor(kind).firstOrNull(File::isFile)?.let { JsTool(kind, it) }
    }
}

// ---------------------------------------------------------------------------------------------
// Native plugin modules (:plugins:<name>) are configured entirely from here,
// so a native plugin needs nothing but a src/main folder and a manifest.json.
// ---------------------------------------------------------------------------------------------

configure(subprojects.filter { it.path.startsWith(":plugins:") }) {
    // AGP 9 has built-in Kotlin support, so org.jetbrains.kotlin.android isn't applied.
    pluginManager.apply(androidLibraryPluginId)

    extensions.configure<LibraryExtension> {
        namespace = "revenge.plugin." + name.replace(Regex("[^A-Za-z0-9]"), "_")
        compileSdk = compileSdkVer.toInt()
        defaultConfig { minSdk = minSdkVer.toInt() }
        compileOptions {
            sourceCompatibility = JavaVersion.toVersion(javaVer)
            targetCompatibility = JavaVersion.toVersion(javaVer)
        }
        sourceSets {
            named("main") { kotlin.directories += "src/main/kotlin" }
        }
    }

    extensions.configure<KotlinAndroidProjectExtension> {
        jvmToolchain(javaVer.toInt())
    }

    // AGP's lint tasks require the module's build.gradle as an input, which these script-less modules don't have.
    // There is nothing to lint here anyway.
    tasks.configureEach {
        if (name.contains("Lint") || name.startsWith("lint")) enabled = false
    }

    dependencies {
        // Provided at runtime by the host, so never bundled
        add("compileOnly", revengeApiDep)
        add("compileOnly", xposedApiDep)
        add("compileOnly", coroutinesDep)
    }

    val androidExt = extensions.getByType<LibraryExtension>()
    val sdkDirProvider = extensions.getByType<LibraryAndroidComponentsExtension>().sdkComponents.sdkDirectory
    val execOps = objects.newInstance<ExecOpsProvider>().execOps

    tasks.register("dexJar") {
        group = "revenge"
        description = "Compiles this plugin into a DEX-containing JAR loadable by DexClassLoader."
        dependsOn("compileReleaseKotlin", "compileReleaseJavaWithJavac")
        mustRunAfter(cleanTasks)

        // Only this module's own classes are DEXed. Dependencies compileOnly.
        val classDirs = files(
            // AGP 9 built-in Kotlin output, then the pre-AGP 9 location
            layout.buildDirectory.dir("intermediates/built_in_kotlinc/release/compileReleaseKotlin/classes"),
            layout.buildDirectory.dir("tmp/kotlin-classes/release"),
            layout.buildDirectory.dir("intermediates/javac/release/classes"),
        )
        inputs.files(classDirs).withPropertyName("classDirs").optional()

        // Java resources are packaged as-is, so the plugin can read them through its class loader.
        val resources = fileTree("src/main/resources")
        inputs.files(resources).withPropertyName("resources").optional()

        val outJar = layout.buildDirectory.file("outputs/plugin/plugin.jar")
        outputs.file(outJar).withPropertyName("dexJar")

        val buildToolsVersion = androidExt.buildToolsVersion

        doLast {
            val classFiles = classDirs.asFileTree.matching { include("**/*.class") }.files
            if (classFiles.isEmpty()) error("No compiled classes found to dex in $path.")

            val sdkDir = sdkDirProvider.get().asFile
            // Use newest d8 as the AGP-default build-tools could ship one too old for class files from a newer toolchain
            val d8 = File(sdkDir, "build-tools").listFiles()
                ?.sortedByDescending(File::getName)
                ?.map { File(it, "d8") }
                ?.firstOrNull(File::isFile)
                ?: file("$sdkDir/build-tools/$buildToolsVersion/d8")
            val androidJar = file("$sdkDir/platforms/android-$compileSdkVer/android.jar")
            require(d8.exists()) { "d8 not found at $d8" }
            require(androidJar.exists()) { "android.jar not found at $androidJar" }

            val out = outJar.get().asFile
            out.parentFile.mkdirs()
            execOps.exec {
                commandLine(
                    d8.absolutePath,
                    "--min-api", minSdkVer,
                    "--lib", androidJar.absolutePath,
                    "--output", out.absolutePath,
                    *classFiles.map { it.absolutePath }.toTypedArray(),
                )
            }

            if (!resources.isEmpty) {
                FileSystems.newFileSystem(out.toPath()).use { jar ->
                    resources.visit {
                        val target = jar.getPath(relativePath.pathString)
                        if (isDirectory) Files.createDirectories(target)
                        else Files.copy(file.toPath(), target)
                    }
                }
            }

            logger.lifecycle("Dexed $path -> ${out.relativeTo(rootProject.projectDir)}")
        }
    }
}

// ---------------------------------------------------------------------------------------------
// JS bundles (all plugins at once) and per-plugin packaging into build/dist/<id>@<version>.zip
// ---------------------------------------------------------------------------------------------

val buildJs = tasks.register("buildJs") {
    group = "revenge"
    description = "Builds every plugin's JS bundle via the revenge-plugin CLI."
    mustRunAfter(cleanTasks)

    // The bundler is @revenge-mod/plugin-cli, so the lockfiles (which pin its revision) are the inputs.
    inputs.files(
        "package.json", "tsconfig.json", "biome.json",
        "bun.lock", "bun.lockb", "package-lock.json", "deno.lock",
    ).withPropertyName("config")
    // Everything a plugin's bundle can be built from.
    inputs.files(fileTree("plugins") { exclude("*/build/**", "*/src/**") }).withPropertyName("sources")

    pluginDefs.filter { it.scriptName != null }.forEach {
        outputs.dir(File(it.dir, "build/js")).withPropertyName("bundle-${it.dir.name}")
    }

    doLast {
        val tool = findJsTool()
        if (tool == null) {
            logger.warn(
                "No JS toolchain found (bun, npm, deno). Install Node >= 22.18, Bun or Deno 2, " +
                    "or pass -PjsTool=/-PjsToolPath=. Skipping JS build.",
            )
            return@doLast
        }

        logger.lifecycle("Building JS bundles with ${tool.kind} (${tool.exe})")
        // Put the tool's own dir on PATH so nested calls (npm -> node, script -> bin shims) resolve.
        val path = tool.exe.parentFile.absolutePath + File.pathSeparator + System.getenv("PATH").orEmpty()
        listOf(tool.installCommand, tool.buildCommand).forEach { cmd ->
            rootExecOps.exec {
                workingDir = rootDir
                environment("PATH", path)
                commandLine(cmd)
            }
        }
    }
}

// Packaging always starts from an empty dist directory. Archive names only contain the
// plugin id and version, so retaining a same-name archive would keep its old digest in the index.
val cleanDist = tasks.register<Delete>("cleanDist") {
    group = "revenge"
    description = "Deletes packaged plugin archives so their digests are regenerated."
    delete(layout.buildDirectory.dir("dist"))
    outputs.upToDateWhen { false }
}

val packageAllPlugins = tasks.register("packageAllPlugins") {
    group = "revenge"
    description = "Builds and packages every plugin into build/dist/<id>@<version>.zip."
}

fun taskSuffix(dirName: String): String =
    dirName.split(Regex("[^A-Za-z0-9]")).filter(String::isNotEmpty)
        .joinToString("") { it.replaceFirstChar(Char::uppercase) }

pluginDefs.forEach { (dir, id, version, jarName, scriptName) ->
    val pkg = tasks.register<Zip>("package${taskSuffix(dir.name)}") {
        group = "revenge"
        description = "Packages '$id' into a distributable ZIP."
        dependsOn(buildJs, cleanDist)
        mustRunAfter(cleanTasks)

        from(File(dir, "manifest.json"))

        if (jarName != null && File(dir, "src/main").isDirectory) {
            val nativePath = ":plugins:${dir.name}"
            dependsOn("$nativePath:dexJar")
            from(provider { project(nativePath).tasks.named("dexJar").get().outputs.files.singleFile }) {
                rename { jarName }
            }
        }

        if (scriptName != null) {
            from(File(dir, "build/js")) {
                include("index.js")
                rename { scriptName }
            }
        }

        // Extra files the plugin reads at runtime (fonts, images), kept under `assets/` in the ZIP
        val assets = File(dir, "assets")
        if (assets.isDirectory) from(assets) { into("assets") }

        archiveFileName.set("$id@$version.zip")
        destinationDirectory.set(layout.buildDirectory.dir("dist"))
        doLast { logger.lifecycle("Packaged $id -> ${archiveFile.get().asFile.relativeTo(rootDir)}") }
    }

    packageAllPlugins.configure { dependsOn(pkg) }
}
