import com.vanniktech.maven.publish.SonatypeHost

plugins {
    kotlin("jvm") version "2.0.21"
    kotlin("plugin.serialization") version "2.0.21"
    `maven-publish`
    signing
    id("com.vanniktech.maven.publish") version "0.29.0"
}

group = "pro.branta"
version = "3.2.0"

kotlin {
    jvmToolchain(11)
}

repositories {
    mavenCentral()
}

dependencies {
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.8.1")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3")

    testImplementation("org.junit.jupiter:junit-jupiter:5.10.3")
    testImplementation("io.mockk:mockk:1.13.12")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.8.1")
    testImplementation("org.jetbrains.kotlin:kotlin-test")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.test {
    useJUnitPlatform()
}

mavenPublishing {
    publishToMavenCentral(SonatypeHost.CENTRAL_PORTAL)
    signAllPublications()

    coordinates("pro.branta", "branta", version.toString())

    pom {
        name.set("Branta Kotlin SDK")
        description.set("Kotlin SDK for the Branta V2 API — payment destination lookup and registration with zero-knowledge encryption support")
        url.set("https://branta.pro")
        licenses {
            license {
                name.set("MIT License")
                url.set("https://opensource.org/licenses/MIT")
            }
        }
        developers {
            developer {
                id.set("branta")
                name.set("Branta")
                email.set("support@branta.pro")
            }
        }
        scm {
            url.set("https://github.com/BrantaOps/branta-kotlin")
            connection.set("scm:git:git://github.com/BrantaOps/branta-kotlin.git")
            developerConnection.set("scm:git:ssh://git@github.com/BrantaOps/branta-kotlin.git")
        }
    }
}
