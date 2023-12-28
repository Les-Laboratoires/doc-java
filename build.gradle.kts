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
	implementation(libs.dotenv)
}

kotlin {
	jvmToolchain(21)
}

application {
	mainClass = "io.github.ayfri.docjava.MainKt"
}
