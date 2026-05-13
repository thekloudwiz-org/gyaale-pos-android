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
        // SUNMI printer SDK (mirror of their public Maven repo)
        maven { url = uri("https://maven.sunmi.com/repository/maven-public/") }
    }
}

rootProject.name = "Gyaale POS"
include(":app")
