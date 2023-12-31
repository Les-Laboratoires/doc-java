package io.github.ayfri.docjava.utils

import it.skrape.selects.DomTreeElement

fun DomTreeElement.findFirstOrNull(selector: String) = runCatching { findFirst(selector) }.getOrNull()

fun DomTreeElement.findAllOrNull(selector: String) = runCatching { findAll(selector) }.getOrNull()
