plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
    `maven-publish`
}

android {
    namespace = "com.devindie.zola.adb.pairing.impl"
    compileSdk = 36

    defaultConfig {
        minSdk = 28
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        consumerProguardFiles("consumer-rules.pro")
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    kotlinOptions {
        jvmTarget = "11"
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

dependencies {
    api(project(":api"))
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.livedata.core)
    implementation(libs.conscrypt.android)
    implementation(libs.bcpkix.jdk18on)
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
