import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.plugin.mpp.apple.XCFramework

plugins {
    // The Kotlin Gradle plugin is already on the build classpath (root build.gradle.kts pins the
    // version), so the multiplatform plugin must be requested without a version here.
    kotlin("multiplatform")
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
        }
        commonTest.dependencies {
            implementation(kotlin("test"))
            implementation(libs.coroutines.test)
        }
        jvmTest.dependencies {
            implementation(libs.junit)
        }
    }
}
