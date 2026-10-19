import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.plugin.mpp.apple.XCFramework

plugins {
    // The Kotlin Gradle plugin is already on the build classpath (root build.gradle.kts pins the
    // version), so the multiplatform plugin must be requested without a version here.
    kotlin("multiplatform")
    // The Zepp code parses JSON with kotlinx-serialization-json; the plugin is the same Kotlin
    // version as the compiler (pinned in libs.versions.toml).
    alias(libs.plugins.kotlin.serialization)
    // Room's KMP compiler runs through KSP: one schema, one database per target.
    alias(libs.plugins.ksp)
    alias(libs.plugins.room)
}

// Room writes the exported schema here; the JVM test opens the database from these entities.
room {
    schemaDirectory("$projectDir/schemas")
}

kotlin {
    jvmToolchain(21)

    jvm {
        // Bytecode level matches the Android app module.
        compilerOptions { jvmTarget.set(JvmTarget.JVM_17) }
    }
    iosArm64()
    iosSimulatorArm64()

    // One static XCFramework containing the device and simulator slices.
    val xcframework = XCFramework("OpenStrapShared")
    listOf(iosArm64(), iosSimulatorArm64()).forEach { target ->
        target.binaries.framework {
            baseName = "OpenStrapShared"
            isStatic = true
            xcframework.add(this)
        }
    }

    sourceSets {
        commonMain.dependencies {
            api(project(":protocol"))
            implementation(libs.coroutines.core)
            implementation(libs.kotlinx.datetime)
            implementation(libs.kotlinx.serialization.json)
            // Room 2.8 is multiplatform; the bundled SQLite build supplies the native driver.
            api(libs.room.runtime)
            implementation(libs.sqlite.bundled)
        }
        commonTest.dependencies {
            implementation(kotlin("test"))
            implementation(libs.coroutines.test)
        }
        jvmTest.dependencies {
            implementation(libs.junit)
            implementation(libs.coroutines.test)
            // Room's JVM artifacts come with the multiplatform runtime; JUnit is the runner.
        }
    }
}

dependencies {
    // KSP2 supports Kotlin/Native; this exact triple is the one verified on this server.
    add("kspJvm", libs.room.compiler)
    add("kspIosArm64", libs.room.compiler)
    add("kspIosSimulatorArm64", libs.room.compiler)
}
