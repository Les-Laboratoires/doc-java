package io.github.ayfri.docjava.utils

import com.kotlindiscord.kord.extensions.types.InteractionContext

suspend fun InteractionContext<*, *, *, *>.reply(content: String) = respond {
	this.content = content
}

suspend fun InteractionContext<*, *, *, *>.edit(content: String) = edit {
	this.content = content
}
