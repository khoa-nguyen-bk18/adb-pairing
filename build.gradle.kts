plugins {
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.kotlin.android) apply false
}

allprojects {
    group = "io.github.khoanguyenbk18.adbpairing"
    version = providers.gradleProperty("VERSION_NAME").orElse("0.1.0").get()
}
