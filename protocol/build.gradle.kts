import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    // The Kotlin Gradle plugin is already on the build classpath (root build.gradle.kts pins the
    // version), so the multiplatform plugin must be requested without a version here.
    kotlin("multiplatform")
}

kotlin {
    jvmToolchain(21)

    compilerOptions {
        // `expect object Aes` is the design the card asks for; the flag only silences the Beta warning.
        freeCompilerArgs.add("-Xexpect-actual-classes")
    }

    jvm {
        // Bytecode level matches the Android app module.
        compilerOptions { jvmTarget.set(JvmTarget.JVM_17) }
    }
    iosArm64()
    iosSimulatorArm64()

    sourceSets {
        commonMain.dependencies {
            implementation(libs.coroutines.core)
            implementation(libs.kotlinx.datetime)
        }
        commonTest.dependencies {
            implementation(kotlin("test"))
            implementation(libs.coroutines.test)
        }
        jvmTest.dependencies {
            implementation(libs.junit)
            // Only used to cross-check our B-163 implementation in tests; not shipped.
            implementation(libs.bouncycastle)
        }
    }
}
