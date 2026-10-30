pluginManagement {
    repositories { google(); mavenCentral(); gradlePluginPortal() }
    resolutionStrategy { eachPlugin {
        if (requested.id.id.startsWith("org.jetbrains.kotlin")) useModule("org.jetbrains.kotlin:kotlin-gradle-plugin:${requested.version}")
    } }
}
dependencyResolutionManagement { repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS); repositories { google(); mavenCentral() } }
rootProject.name = "RiftArtillery"
include(":core", ":app", ":desktop-core")
