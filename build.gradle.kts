import com.github.spotbugs.snom.Confidence
import com.github.spotbugs.snom.Effort

plugins {
    `java-library`
    checkstyle
    jacoco
    id("com.github.spotbugs") version "6.0.26"
    id("me.champeau.jmh") version "0.7.2"
    `maven-publish`
}

// JitPack passes its own coordinates (jitpack.yml); local builds use these.
group = providers.gradleProperty("publishGroup").getOrElse("dev.trafficcontrol")
version = providers.gradleProperty("publishVersion").getOrElse("0.4.0-SNAPSHOT")

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(21))
    }
    withSourcesJar()
}

repositories {
    mavenCentral()
}

dependencies {
    testImplementation(platform("org.junit:junit-bom:5.11.4"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")

    jmh("org.openjdk.jmh:jmh-core:1.37")
    jmh("org.openjdk.jmh:jmh-generator-annprocess:1.37")
}

tasks.test {
    useJUnitPlatform()
    maxHeapSize = "1g"
    jvmArgs("-XX:+UseParallelGC")
}

// JitPack builds this repo on demand (see jitpack.yml) and serves it as
// com.github.JavidanAlizada:resilience-traffic-control:<commit or tag>.
publishing {
    publications {
        create<MavenPublication>("maven") {
            from(components["java"])
        }
    }
}

jacoco {
    toolVersion = "0.8.12"
}

tasks.jacocoTestReport {
    dependsOn(tasks.test)
    reports {
        xml.required.set(true)
        html.required.set(true)
    }
}

checkstyle {
    toolVersion = "10.20.2"
    configFile = file("config/checkstyle/checkstyle.xml")
    isIgnoreFailures = false
    maxWarnings = 0
}

spotbugs {
    toolVersion.set("4.8.6")
    effort.set(Effort.MAX)
    reportLevel.set(Confidence.MEDIUM)
}

tasks.spotbugsMain {
    reports.create("html") {
        required.set(true)
    }
}

tasks.spotbugsTest {
    reports.create("html") {
        required.set(true)
    }
}

jmh {
    // Fast "smoke" profile by default (used by the PR pipeline); the
    // nightly pipeline passes -Pjmh.warmupIterations / -Pjmh.iterations /
    // -Pjmh.fork to run the full matrix without a second config block.
    warmupIterations.set(intProjectProperty("jmh.warmupIterations", 1))
    iterations.set(intProjectProperty("jmh.iterations", 1))
    fork.set(intProjectProperty("jmh.fork", 1))
    threads.set(1)
    resultFormat.set("JSON")
    resultsFile.set(project.layout.buildDirectory.file("results/jmh/results.json").get().asFile)
}

fun intProjectProperty(name: String, default: Int): Int =
    (project.findProperty(name) as String?)?.toInt() ?: default

tasks.named("check") {
    dependsOn(tasks.jacocoTestReport)
}
