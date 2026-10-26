allprojects {
    group = "io.github.khoanguyenbk18.adbpairing"
    version = providers.gradleProperty("VERSION_NAME").orElse("0.1.0").get()
}
