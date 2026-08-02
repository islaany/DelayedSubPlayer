pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        // ffmpeg-kit 不在 Maven Central，发布在 Arthenica 自有仓库（用于字幕轨抽取）
        maven { url = uri("https://maven.arthenica.com/repository/release") }
    }
}

rootProject.name = "DelayedSubPlayer"
include(":app")
include(":shared")
