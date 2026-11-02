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
    }
}
rootProject.name = "qrst-signer"
include(":app")
// The QRST core and relay layer, shared with Cosign: the submodule, at the commit recorded here.
includeBuild("qr-secret-transfer/kotlin")
