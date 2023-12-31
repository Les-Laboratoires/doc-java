plugins {
	application
	alias(libs.plugins.kotlin)
}

group = "io.github.ayfri"
version = "1.0-SNAPSHOT"

repositories {
	mavenCentral()
}

dependencies {
	implementation(libs.kord.extensions)
	implementation(libs.logback)
	implementation(libs.skrapeit) {
		val logback = libs.logback.get()
		exclude(group = logback.group, module = logback.module.name)
	}
	implementation(libs.skrapeit.async.fetcher)
}

kotlin {
	jvmToolchain(21)
}

application {
	mainClass = "io.github.ayfri.docjava.MainKt"
}

configurations.all {
	exclude(group = "org.slf4j", module = "log4j-over-slf4j")
}
