buildscript {
    repositories {
        google()
        mavenCentral()
    }
    dependencies {
        classpath("org.jetbrains.kotlin:kotlin-gradle-plugin:1.8.21")
        classpath("com.android.tools.build:gradle:8.2.1")
        classpath("androidx.navigation:navigation-safe-args-gradle-plugin:2.7.6")
    }
}

allprojects {
    repositories {
        google()
        mavenCentral()
        maven { url = uri("https://jitpack.io")}
    }
}

tasks.register("clean", Delete::class) {
    delete(layout.buildDirectory)
}
