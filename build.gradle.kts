plugins {
    kotlin("jvm") version "1.3.11"
    application
}

repositories {
    jcenter()
}

dependencies {
    implementation("org.jetbrains.kotlin:kotlin-stdlib:1.3.0")
    implementation("org.jetbrains.kotlin:kotlin-reflect:1.3.0")
    implementation("org.jgrapht:jgrapht-core:1.3.0")
    implementation("org.jgrapht:jgrapht-guava:1.3.0")
    implementation("org.jgrapht:jgrapht-io:1.3.0")
    implementation("com.google.guava:guava:21.0")
}

application {
    mainClassName = "pw.kitl.test.MainKt"
}
