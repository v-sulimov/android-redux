plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.maven.publish)
}

android {
    namespace = "com.vsulimov.redux"
    compileSdk = 35

    defaultConfig {
        minSdk = 24
        lint.targetSdk = 35
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }

    kotlinOptions {
        jvmTarget = "11"
    }

    publishing {
        singleVariant("release") {
            withSourcesJar()
            withJavadocJar()
        }
    }
}

dependencies {
}

publishing {
    publications {
        afterEvaluate {
            create<MavenPublication>("release") {
                groupId = "com.vsulimov"
                artifactId = "redux"
                version = "1.0.0"

                from(components["release"])

                pom {
                    packaging = "aar"
                    name = "Redux"
                    description = "A predictable state container for Android apps."
                    licenses {
                        license {
                            name = "The Apache License, Version 2.0"
                            url = "http://www.apache.org/licenses/LICENSE-2.0.txt"
                        }
                    }
                    developers {
                        developer {
                            name = "Vitaly Sulimov"
                            email = "v.sulimov.dev@imap.cc"
                        }
                    }
                    scm {
                        url = "https://git.vsulimov.com/android-redux.git"
                    }
                }
            }
        }
    }
}
