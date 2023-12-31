package io.github.ayfri.docjava.extensions

import com.kotlindiscord.kord.extensions.commands.Arguments
import com.kotlindiscord.kord.extensions.commands.converters.impl.optionalInt
import com.kotlindiscord.kord.extensions.commands.converters.impl.string
import com.kotlindiscord.kord.extensions.extensions.Extension
import com.kotlindiscord.kord.extensions.extensions.publicSlashCommand
import io.github.ayfri.docjava.logger
import io.github.ayfri.docjava.utils.JavadocExtensionUtils
import io.github.ayfri.docjava.utils.MAX_JAVADOC_VERSION
import io.github.ayfri.docjava.utils.MIN_JAVADOC_VERSION
import io.github.ayfri.docjava.utils.edit

class JavadocExtension : Extension() {
	override val name = "javadoc"

	class JavadocArguments : Arguments() {
		val packageArg by string {
			name = "package"
			description = "Le package à rechercher."
			minLength = 1
			maxLength = 100
		}

		val versionArg by optionalInt {
			name = "version"
			description = "La version du JDK à utiliser."
			minValue = MIN_JAVADOC_VERSION
			maxValue = MAX_JAVADOC_VERSION
		}
	}

	override suspend fun setup() {
		publicSlashCommand(::JavadocArguments) {
			name = "javadoc"
			description = "Recherche une classe dans la documentation Java."

			action {
				val packageArg = arguments.packageArg
				val versionArg = arguments.versionArg
				var version = versionArg ?: -1

				if (versionArg == null) {
					version = JavadocExtensionUtils.searchVersion(packageArg, this)
					if (version == -1) {
						edit("La classe n'a pas été trouvé.")
						return@action
					}
					logger.debug { "Version found : $version" }
				}

				when (val builtEmbed = JavadocExtensionUtils.searchPackage(packageArg, version)) {
					null -> edit("La classe n'a pas été trouvé.")
					else -> edit {
						content = ""
						logger.info {
							builtEmbed.fields.forEach {
								logger.debug { "${it.name} : ${it.value}" }
							}
						}
						embeds = mutableListOf(builtEmbed)
					}
				}
			}
		}
	}
}
