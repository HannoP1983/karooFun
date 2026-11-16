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
        // karoo-ext: official Hammerhead source, built by JitPack (GitHub Packages would require a token)
        maven {
            url = uri("https://jitpack.io")
            content { includeGroup("com.github.hammerheadnav") }
        }
    }
}

rootProject.name = "karoo-fun"
include(":app")
