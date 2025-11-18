pluginManagement {
    repositories {
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
        mavenCentral()
        gradlePluginPortal()
        maven (url = "https://jitpack.io")
        maven ("https://storage.zego.im/maven") // <- Add this line.
    }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        maven (url = "https://jitpack.io")
        maven ("https://storage.zego.im/maven") // <- Add this line.
    }
}

rootProject.name = "Associate"
include(":app")
 