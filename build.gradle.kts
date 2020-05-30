plugins {
    kotlin("jvm") version "1.3.72"
}

group = "ir.ac.yazd"
version = "1.0-SNAPSHOT"

repositories {
    google()
    jcenter()
    mavenCentral()
}

dependencies {
    implementation(kotlin("stdlib-jdk8"))
    implementation("com.github.junrar:junrar:4.0.0")
    implementation("org.apache.lucene:lucene-core:8.5.2")
    implementation("org.apache.lucene:lucene-queryparser:8.5.2")
}

tasks {
    compileKotlin {
        kotlinOptions.jvmTarget = "11"
    }
    compileTestKotlin {
        kotlinOptions.jvmTarget = "11"
    }
}
