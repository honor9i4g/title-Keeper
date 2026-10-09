plugins {
    java
}


group = "me.titlekeeper"
version = "1.6.0"


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
