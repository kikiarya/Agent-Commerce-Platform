plugins {
    id("org.springframework.boot")
    id("io.spring.dependency-management")
    java
}

dependencies {
    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.boot:spring-boot-starter-data-jpa")
    implementation("org.springframework.boot:spring-boot-starter-amqp")
    implementation(project(":common"))

    // gRPC Client - using 3.x for Spring Boot 3.x compatibility
    // Let grpc-spring-boot-starter manage gRPC versions automatically
    implementation("net.devh:grpc-client-spring-boot-starter:3.1.0.RELEASE")

    runtimeOnly("org.postgresql:postgresql:42.7.3")
    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

