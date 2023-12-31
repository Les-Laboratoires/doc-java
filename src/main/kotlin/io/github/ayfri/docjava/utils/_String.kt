package io.github.ayfri.docjava.utils

fun String.cutToLength(length: Int) = when {
	this.length > length -> this.take(length - 3) + "..."
	else -> this
}
