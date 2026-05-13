pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}
plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        // SUNMI distributes their printer SDK as an AAR via their developer
        // portal (developer.sunmi.com), not a public Maven endpoint. Drop
        // the AAR into app/libs/ — see app/libs/README.md for the link.
        flatDir { dirs("app/libs") }
    }
}

rootProject.name = "Gyaale POS"
include(":app")
