plugins {
    id("com.android.application") version "8.0.2"
    kotlin("android") version "2.0.0"
}

@Suppress("UnstableApiUsage")
configurations {
    val jsDependencyScope by dependencyScope("jsDependencyScope")
    resolvable("jsConfiguration") { extendsFrom(jsDependencyScope) }
}

dependencies {
    add(
        "jsDependencyScope",
        project(
            mapOf(
                "path" to project(":js").path,
                "configuration" to "jsConfiguration",
            ),
        ),
    )
}

android {
    namespace = "io.github.revenge.plugin"
    compileSdk = 34

    defaultConfig {
        applicationId = "io.github.revenge.plugin"
        minSdk = 24
    }

    buildTypes {
        release {
            isMinifyEnabled = false
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }
    kotlinOptions {
        jvmTarget = "1.8"
    }
    @Suppress("UnstableApiUsage")
    buildFeatures {
        buildConfig = true
    }
    
    sourceSets {
        named("main") {
            resources {
                srcDir()
                srcDir(configurations["jsConfiguration"])
            }
        }
    }
}