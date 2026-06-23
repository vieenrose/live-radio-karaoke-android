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
    }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        // sherpa-onnx prebuilt artifacts (used for the default `dev`/Play build flavor only;
        // the F-Droid flavor builds the native libs from source — see fdroid/metadata-template.yml)
        maven { url = uri("https://jitpack.io") }
    }
}

rootProject.name = "LiveRadioKaraoke"
include(":app")
