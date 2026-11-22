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

rootProject.name = "ObdFacile"

// :obd-core  -> logica OBD/ELM327 in puro Kotlin (testabile su JVM, nessuna dipendenza Android)
// :app       -> app Android (Compose, trasporti Bluetooth/BLE, UI)
include(":app", ":obd-core")
