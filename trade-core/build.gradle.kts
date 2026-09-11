plugins {
    java
    id("org.springframework.boot") version "3.5.6"
    id("io.spring.dependency-management") version "1.1.7"
    id("com.google.protobuf") version "0.9.4" apply false
}

group = "com.comp5348"
version = "0.0.1-SNAPSHOT"

// 根项目不是 Spring Boot 应用，禁用 bootJar 和 bootRun
tasks.named<org.springframework.boot.gradle.tasks.bundling.BootJar>("bootJar") {
    enabled = false
}
tasks.named<org.springframework.boot.gradle.tasks.run.BootRun>("bootRun") {
    enabled = false
}

allprojects {
    repositories {
        maven {
            name = "googleMavenCentral"
            url = uri("https://maven-central.storage-download.googleapis.com/maven2")
        }
        mavenCentral()
    }
}

subprojects {
    apply(plugin = "io.spring.dependency-management")

    // Unify Java version and test platform
    tasks.withType<JavaCompile> {
        sourceCompatibility = "17"
        targetCompatibility = "17"
        options.encoding = "UTF-8"
    }
    tasks.withType<Test> {
        useJUnitPlatform()
    }
}

