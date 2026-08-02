plugins {
    alias(libs.plugins.kotlinJvm)
    alias(libs.plugins.compose)
}

kotlin {
    jvmToolchain(17)
}

compose.desktop {
    application {
        mainClass = "com.huqi.delayedsub.desktop.MainKt"
        nativeDistributions {
            packageName = "DelayedSubPlayer"
            packageVersion = "0.1.0"
        }
    }
}

dependencies {
    // 共享字幕逻辑（解析 / 拆分 / 延迟 / 渲染，跨 Android + Windows 复用）
    implementation(project(":shared"))

    // Compose 桌面端 UI
    implementation(compose.desktop.currentOs)
    implementation(compose.material3)

    // 视频播放：VLCJ 封装 libVLC（用户需自行安装 VLC 播放器）
    implementation("uk.co.caprica:vlcj:4.8.3")
    // 静音 VLCJ 的 slf4j 日志
    implementation("org.slf4j:slf4j-nop:2.0.12")
}
