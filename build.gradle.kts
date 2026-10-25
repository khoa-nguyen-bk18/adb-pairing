plugins {
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.jvm) apply false
}

allprojects {
    group = "io.github.khoanguyenbk18.adbpairing"
    version = providers.gradleProperty("VERSION_NAME").get()
}
