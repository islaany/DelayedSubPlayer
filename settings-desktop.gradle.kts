// 仅用于构建 Windows 桌面版（避免配置 :app 需要 Android SDK）。
// CI 通过 `gradle -c settings-desktop.gradle.kts` 使用该配置。
pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "DelayedSubPlayerDesktop"
include(":shared")
include(":desktopApp")
