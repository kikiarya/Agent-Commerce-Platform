pluginManagement {
    repositories {
        // Prefer Google's Maven Central mirror — GitHub Actions shared IPs often get 403 from repo.maven.apache.org
        maven {
            name = "googleMavenCentral"
            url = uri("https://maven-central.storage-download.googleapis.com/maven2")
        }
        gradlePluginPortal()
        mavenCentral()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.PREFER_PROJECT)
    repositories {
        maven {
            name = "googleMavenCentral"
            url = uri("https://maven-central.storage-download.googleapis.com/maven2")
        }
        mavenCentral()
    }
}

rootProject.name = "distributed-ecommerce-platform"
include("common", "store", "bank", "delivery", "email")
