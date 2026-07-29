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
        maven { url = uri("https://cz-adaptech.github.io/gmaven") }
        maven { url = uri("https://jitpack.io") }
    }
}

rootProject.name = "imgtotext"
include(":app")
