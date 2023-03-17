import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
	id("org.springframework.boot") version "3.0.4"
	id("io.spring.dependency-management") version "1.1.0"
	kotlin("jvm") version "1.7.22"
	kotlin("plugin.spring") version "1.7.22"
  id("org.flywaydb.flyway") version "9.8.1"
}

group = "com.accumulustakehome"
version = "0.0.1-SNAPSHOT"
java.sourceCompatibility = JavaVersion.VERSION_17

repositories {
	mavenCentral()
}

dependencies {
	implementation("org.springframework.boot:spring-boot-starter-web")
	implementation("org.jetbrains.kotlin:kotlin-reflect")
  implementation("org.flywaydb:flyway-core:9.8.1")
	implementation("org.springframework.boot:spring-boot-starter-data-jdbc")
	testImplementation("org.springframework.boot:spring-boot-starter-test")
	testImplementation("org.springframework.boot:spring-boot-starter-webflux")
	testImplementation("com.fasterxml.jackson.module:jackson-module-kotlin")
  runtimeOnly("org.postgresql:postgresql")
}

tasks.withType<KotlinCompile> {
	kotlinOptions {
		freeCompilerArgs = listOf("-Xjsr305=strict")
		jvmTarget = "17"
	}
}

tasks.withType<Test> {
	useJUnitPlatform()
}

flyway {
    url = "jdbc:postgresql:postgres"
    user = "postgres"
    password = "postgres"
    schemas = arrayOf("pizza")
		cleanDisabled = false
}