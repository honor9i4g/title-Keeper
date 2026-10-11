plugins {
    java
}


group = "me.titlekeeper"
version = "1.7.0"


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
