package io.github.ayfri.docjava

import ch.qos.logback.classic.LoggerContext
import com.kotlindiscord.kord.extensions.ExtensibleBot
import com.kotlindiscord.kord.extensions.utils.env
import dev.kord.common.entity.PresenceStatus
import dev.kord.common.entity.Snowflake
import dev.kord.gateway.ALL
import dev.kord.gateway.Intents
import dev.kord.gateway.PrivilegedIntent
import io.github.ayfri.docjava.extensions.JavadocExtension
import io.github.oshai.kotlinlogging.KotlinLogging
import org.slf4j.LoggerFactory
import java.util.*

val logger = KotlinLogging.logger("main").also {
	// Weird hack to disable other logback console appenders (because some libraries define their own)
	val loggerFactory = LoggerFactory.getILoggerFactory() as LoggerContext
	loggerFactory.loggerList[0].iteratorForAppenders().forEach {
		if (it.name != "CONSOLE") it.stop()
	}
}

val debug = env("ENVIRONMENT") == "development"

lateinit var bot: ExtensibleBot

@OptIn(PrivilegedIntent::class)
suspend fun main() {


	TimeZone.setDefault(TimeZone.getTimeZone("Europe/Paris"))

	bot = ExtensibleBot(env("TOKEN")) {
		extensions {
			add(::JavadocExtension)
		}

		applicationCommands {
			defaultGuild = Snowflake(env("JAVA_LABS_ID"))
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
