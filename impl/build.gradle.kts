plugins {
    alias(libs.plugins.android.library)
    `maven-publish`
}

android {
    namespace = "io.github.khoanguyenbk18.adbpairing.impl"
    compileSdk = 36

    defaultConfig {
        minSdk = 30
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        consumerProguardFiles("consumer-rules.pro")
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }

    testOptions {
        unitTests.isReturnDefaultValues = true
    }

    publishing {
        singleVariant("release") {
            withSourcesJar()
        }
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_11)
    }
}

dependencies {
    api(project(":adb-pairing:api"))
    implementation(libs.androidx.core.ktx)
    implementation(libs.conscrypt.android)
    implementation(libs.bcpkix.jdk15to18)
    implementation(libs.bcprov.jdk15to18)
    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
}

afterEvaluate {
    publishing {
        publications {
            create<MavenPublication>("release") {
                from(components["release"])
                artifactId = "impl"
                pom {
                    name.set("ADB Pairing Implementation")
                    description.set("Android implementation of wireless ADB pairing")
                    url.set("https://github.com/khoa-nguyen-bk18/adb-pairing")
                    scm {
                        url.set("https://github.com/khoa-nguyen-bk18/adb-pairing")
                        connection.set("scm:git:https://github.com/khoa-nguyen-bk18/adb-pairing.git")
                    }
                }
            }
        }
        repositories {
            maven {
                name = "GitHubPackages"
                url = uri("https://maven.pkg.github.com/khoa-nguyen-bk18/adb-pairing")
                credentials {
                    username = providers.gradleProperty("gpr.user").orNull
                        ?: System.getenv("GITHUB_ACTOR")
                    password = providers.gradleProperty("gpr.key").orNull
                        ?: System.getenv("GITHUB_TOKEN")
                }
            }
        }
    }
}
