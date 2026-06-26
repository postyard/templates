plugins {
    `java-library`
    id("com.diffplug.spotless") version "8.7.0"
    id("com.vanniktech.maven.publish") version "0.37.0"
}

group = "eu.postyard"
version = providers.gradleProperty("VERSION_NAME").getOrElse("0.0.0")

repositories {
    mavenCentral()
}

spotless {
    java {
        target("src/**/*.java")
        removeUnusedImports()
        googleJavaFormat()
    }
}

dependencies {
    implementation(libs.handlebars)
    implementation(libs.jackson.databind)

    testImplementation(libs.junit.jupiter)
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(21)
    }
}

tasks.named<Test>("test") {
    useJUnitPlatform()
    val fixturesDir = rootProject.projectDir.parentFile.resolve("test-fixtures")
    systemProperty("postyard.fixtures.dir", fixturesDir.absolutePath)
}

mavenPublishing {
    publishToMavenCentral()
    signAllPublications()

    coordinates(group.toString(), "postyard-templates", version.toString())

    pom {
        name = "Postyard Templates"
        description =
            "Handlebars-based, multi-locale email & message templating with typed variables and per-locale translations."
        url = "https://github.com/postyard/templates"

        licenses {
            license {
                name = "MIT License"
                url = "https://opensource.org/licenses/MIT"
                distribution = "repo"
            }
        }

        developers {
            developer {
                id = "postyard"
                name = "Postyard"
                url = "https://github.com/postyard"
            }
        }

        scm {
            url = "https://github.com/postyard/templates"
            connection = "scm:git:git://github.com/postyard/templates.git"
            developerConnection = "scm:git:ssh://git@github.com/postyard/templates.git"
        }
    }
}
