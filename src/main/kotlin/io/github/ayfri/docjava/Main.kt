package io.github.ayfri.docjava

import com.kotlindiscord.kord.extensions.ExtensibleBot
import com.kotlindiscord.kord.extensions.utils.env
import dev.kord.common.entity.PresenceStatus
import dev.kord.gateway.ALL
import dev.kord.gateway.Intents
import dev.kord.gateway.PrivilegedIntent
import io.github.oshai.kotlinlogging.KotlinLogging
import java.util.*

val logger = KotlinLogging.logger("main")

val debug = env("ENVIRONMENT") == "development"

lateinit var bot: ExtensibleBot

@OptIn(PrivilegedIntent::class)
suspend fun main() {
	TimeZone.setDefault(TimeZone.getTimeZone("Europe/Paris"))

	bot = ExtensibleBot(env("TOKEN")) {
		extensions {
		}

		hooks {
			extensionAdded {
				if (debug) logger.info { "Loaded extension: ${it.name} with ${it.slashCommands.size} slash commands, ${it.chatCommands.size} chat commands and ${it.eventHandlers.size} events" }
			}
		}

		intents { +Intents.ALL }

		presence {
			status = PresenceStatus.Invisible
		}
	}

	if (debug) logger.info { "Debug mode enabled" }
	bot.start()
}
