

plugins {
    java
    `maven-publish`
}

group = "me.titlekeeper"
version = "1.7.1"

repositories {
    mavenCentral()
    maven("https://repo.papermc.io/repository/maven-public/")
}

dependencies {
    compileOnly("io.papermc.paper:paper-api:26.3.build.+")
    compileOnly("net.luckperms:api:5.5")
}

java {
    toolchain.languageVersion.set(JavaLanguageVersion.of(25))
}

tasks.withType<JavaCompile>().configureEach {
    options.release.set(25)
    options.encoding = "UTF-8"
}

tasks.register<Zip>("resourcePackZip") {
    archiveFileName.set("TitleKeeper-Badges-1.4.zip")
    destinationDirectory.set(layout.buildDirectory.dir("libs"))
    from("resource-pack")
}

tasks.named("build") {
    dependsOn("resourcePackZip")
}

publishing {
    publications {
        create<MavenPublication>("gpr") {
            from(components["java"])
            groupId = "me.titlekeeper"
            artifactId = "title-keeper"
            version = project.version.toString()
        }
    }

    repositories {
        maven {
            name = "GitHubPackages"
            url = uri(
                "https://maven.pkg.github.com/honor9i4g/title-Keeper"
            )

            credentials {
                username = providers.gradleProperty("gpr.user").orNull
                    ?: System.getenv("GITHUB_ACTOR")

                password = providers.gradleProperty("gpr.key").orNull
                    ?: System.getenv("GITHUB_TOKEN")
            }
        }
    }
}
