// APK/CI 分支默认使用官方仓库，避免第三方镜像的 5xx 让 Gradle 整个 repository 失效。
// 如国内本地环境明确需要阿里云镜像，可设置 YUNX_USE_MIRROR=true。
pluginManagement {
    repositories {
        if (System.getenv("YUNX_USE_MIRROR") == "true") {
            maven { url = uri("https://maven.aliyun.com/repository/gradle-plugin") }
            maven { url = uri("https://maven.aliyun.com/repository/google") }
            maven { url = uri("https://maven.aliyun.com/repository/public") }
        }
        gradlePluginPortal()
        google()
        mavenCentral()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        if (System.getenv("YUNX_USE_MIRROR") == "true") {
            maven { url = uri("https://maven.aliyun.com/repository/google") }
            maven { url = uri("https://maven.aliyun.com/repository/public") }
        }
        google()
        mavenCentral()
    }
}

rootProject.name = "YunX"

include(":app")
