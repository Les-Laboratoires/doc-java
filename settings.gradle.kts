rootProject.name = "doctor-java"

pluginManagement {
	repositories {
		gradlePluginPortal()
		mavenCentral()
	}
}

@Suppress("UnstableApiUsage")
dependencyResolutionManagement {
	repositories {
		gradlePluginPortal()
		mavenCentral()

		maven {
			name = "Kotlin Discord"
			url = uri("https://maven.kotlindiscord.com/repository/maven-public/")
		}
	}
}
