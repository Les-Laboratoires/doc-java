package io.github.ayfri.docjava.utils

import com.kotlindiscord.kord.extensions.types.InteractionContext
import dev.kord.rest.builder.message.EmbedBuilder
import io.github.ayfri.docjava.logger
import it.skrape.core.htmlDocument
import it.skrape.fetcher.*
import it.skrape.selects.DocElement
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.FileNotFoundException
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URI
import java.util.*

private const val NEW_JAVADOC_LINK = "https://docs.oracle.com/en/java/javase/"
private const val OLD_JAVADOC_LINK = "https://docs.oracle.com/javase/"

const val MAX_JAVADOC_VERSION = 22
const val MIN_JAVADOC_VERSION = 6

data object JavadocExtensionUtils {
	private var version = 0
	private var currentPath = ""

	suspend fun searchPackage(name: String, version: Int) = try {
		this.version = version
		this.currentPath = name
		parse(getLinkFromPackage(name, version), name, version)
	} catch (e: Exception) {
		if (e.message?.contains("HTTP error fetching URL. Status=404") == false) e.printStackTrace()
		null
	}

	suspend fun searchVersion(name: String, interactionContext: InteractionContext<*, *, *, *>): Int {
		var url: String
		var version = MAX_JAVADOC_VERSION
		while (version >= MIN_JAVADOC_VERSION) {
			try {
				url = getLinkFromPackage(name, version)
				interactionContext.edit("Recherche dans le JDK $version...")
				logger.debug { "Searching in : $url" }

				withContext(Dispatchers.IO) {
					val connection = URI(url).toURL().openConnection() as HttpURLConnection
					val response = connection.responseCode
					if (response > 300) throw FileNotFoundException("HTTP error fetching URL. Status=$response")
				}
				break
			} catch (e: FileNotFoundException) {
				if (e.message?.contains("HTTP error fetching URL.") == false) e.printStackTrace()
			}
			version--
		}

		return if (version >= MIN_JAVADOC_VERSION) version else -1
	}

	private fun getLinkFromPackage(packageName: String, version: Int): String {
		val jdkNextBasePackage = listOf("io", "lang", "math", "net", "nio", "security", "text", "time", "util")
		val newJavaDocJDKVersion = 10
		val link = if (version > newJavaDocJDKVersion) NEW_JAVADOC_LINK else OLD_JAVADOC_LINK
		val packages = packageName.split(Regex("\\.")).dropLastWhile(String::isEmpty)
		var url = "$link$version/docs/api/"
		var index = 2

		require(packages.size >= 2) { "Package name must be at least 2 parts long." }

		url += if (version > newJavaDocJDKVersion) "java." else "${packages[0]}/"
		url += packages[1]

		if (version > newJavaDocJDKVersion) {
			// For 'javax' classes
			if ("javax" == packages[0]) url += "javax/${packages[1]}"

			// For 'jdk' classes
			if ("com" == packages[0]) {
				url = "$link$version/docs/api/jdk.${packages[1]}"
				index = 0
			}

			if (jdkNextBasePackage.contains(packages[1])) url = "$link$version/docs/api/java.base/java/${packages[1]}"
		}

		if (index > packages.size - 1) index = 0

		do {
			url += "/${packages[index]}"
			index++
		} while (index < packages.size)

		return "$url.html"
	}

	private suspend fun scrap(url: String) = skrape(AsyncFetcher) {
		request {
			this.url = url
			timeout = 10_000
		}

		response {
			htmlDocument { this }
		}
	}

	@Throws(IOException::class)
	suspend fun parse(link: String, packageLink: String, version: Int): EmbedBuilder? {
		val embed = EmbedBuilder()

		val document = try {
			scrap(link)
		} catch (e: Exception) {
			logger.error(e) { "Error while parsing $link" }
			return null
		}

		val classInformation = document.findFirst(
			when {
				version > 16 -> ".class-description"
				version > 12 -> ".description"
				else -> ".description > .blockList"
			}
		)

		// type :
		val typeText = document.findFirst(".header .title") { text }
		val type = typeText.split(" ").first().lowercase()
		val isAbstract =
			classInformation.findFirst(if (version > 15) ".type-signature .modifiers" else "li > pre") { text }.contains("abstract")

		val typeRepresentation = if (type == "class") "la classe" else "l'$type"
		val abstractRepresentation = if (isAbstract) " abstraite " else " "

		embed.author {
			name = "Informations sur $typeRepresentation$abstractRepresentation: $packageLink"
			url = link
		}

		embed.title = "Informations du JDK $version."

		// description :
		val description = classInformation.findFirst(".block") { html }
		embed.description = parseTextContainingCode(description).cutToLength(2048)

		embed.footer {
			text = "TIP : Vous pouvez cliquer sur le titre pour aller sur la documentation !"
		}

		// inheritance :
		val inheritance = document.findFirstOrNull(
			when {
				version > 10 -> ".inheritance[title]"
				else -> ".contentContainer .inheritance"
			}
		)?.findAll("a[title*=class]")

		if (!inheritance.isNullOrEmpty()) {
			embed.field("Héritage :", false) { formatToInheritance(inheritance, packageLink) }
		}

		// deprecated :
		val deprecated = classInformation.findFirstOrNull(".deprecationBlock .deprecationComment")?.text
		if (deprecated != null) {
			embed.field("Dépréciée :", true) { deprecated }
		}

		// fields :
		classInformation.findAll("dl").forEach { section ->
			val sectionTitle = section.findFirst("dt") { text }
			val foundLinks = section.findAllOrNull(if (version > 8) "dd > code > a" else "dd > a")
			val packageList = when {
				foundLinks != null -> transformAnchorElementsToPackageList(foundLinks).cutToLength(1024)
				else -> ""
			}

			if (sectionTitle.contains("Known Subclasses")) {
				embed.field("Classes parentes :", true) { packageList }
			}

			if (sectionTitle.contains("Known Subinterfaces")) {
				embed.field("Interfaces enfants :", true) { packageList }
			}

			if (sectionTitle.contains("All Superinterfaces")) {
				embed.field("Interfaces parentes :", true) { formatToInheritance(foundLinks!!, packageLink).cutToLength(1024) }
			}

			if (sectionTitle.contains("Implemented Interfaces")) {
				embed.field("Interfaces implémentées :", true) { packageList }
			}

			if (sectionTitle.contains("Implementing Classes")) {
				embed.field("Classes implémentant cette interface :", true) { packageList }
			}

			val titles = section.findAll("dt").mapIndexed { index, it ->
				logger.debug { "It : ${it.text}, index : $index" }
				it.text to it.parent.findByIndex(index, "dd")
			}.toMap()

			logger.debug { "Inside section : $sectionTitle" }
			logger.debug { "Titles : $titles" }


			titles.forEach { (key, value) ->
				if (key.contains("Since")) {
					val since = value.text
					if (since.matches(Regex("(JDK)?\\s*\\d+\\.?\\d?"))) embed.field("Existe depuis le JDK :", true) {
						since.replace("JDK", "")
					}
				}

				if (key.contains("See Also")) {
					embed.field("Voir aussi :", true) {
						when {
							version > 16 -> transformAnchorElementsToPackageList(value.findAll("ul > li > a"))
							else -> packageList
						}
					}
				}
			}
		}

		return embed
	}

	private fun isValidPackage(packageName: String) = packageName.matches(Regex("[a-z]+(\\.[a-z]+)+", RegexOption.IGNORE_CASE))

	private fun formatToInheritance(elements: List<DocElement>, packageLink: String) = elements.foldIndexed("") { i, acc, element ->
		val packageValue = when {
			isValidPackage(element.text) -> element.text
			else -> getPackageFromAElement(element)
		}

		val indentation = when {
			i > 0 -> "${"<:blank:1189942956719865938>".repeat(i)}↳ "
			else -> ""
		}

		"${acc}$indentation${getLinkFromPackage(element.text, packageValue)}\n"
	} + "${"<:blank:1189942956719865938>".repeat(elements.size)}↳ $packageLink"

	private fun getPackageFromAElement(a: DocElement): String {
		var href = a.attributes["href"] ?: return ""
		if (href.endsWith(".html")) {
			href = href.dropLast(5)
		}

		var result = getPackageFromHref(href)
		if (result == href) result = "${currentPath.substringBeforeLast(".")}.${result.substringBefore(".")}"

		val testAfterHash = isValidPackage(result.substringAfter("#"))
		if (testAfterHash) result = result.substringAfter("#")

		if (listOf("jdk", "java", "com").none(result::startsWith)) {
			if (a.hasAttribute("title")) {
				val foundPackage = a.attributes["title"]?.substringAfterLast(" ") ?: return ""
				result = "$foundPackage.$result"
			}
		}

		return result
	}

	private fun getPackageFromHref(href: String) =
		href.split("/").dropLastWhile(String::isEmpty).filter { it != ".." }.joinToString(".")

	private fun textToLinkedText(text: String, link: String) = "[$text]($link)"

	private fun getLinkFromPackage(title: String, packageName: String) = textToLinkedText(title, getLinkFromPackage(packageName, version))

	private fun transformAnchorElementsToPackageList(elements: List<DocElement>) =
		elements.joinToString(", ") { getLinkFromPackage(it.text, getPackageFromAElement(it)) }

	private fun parseTextContainingCode(text: String): String {
		var text = text

		repeat(4) {
			text = text.replace(Regex("<(div|button)(?: [a-z-]+=\".+?\")+>([\\w\\W]+?)</\\1>"), "$2")
			text = text.replace(Regex("<(span)(?: [a-z-]+=\".+?\")+>([\\w\\W]+?)</\\1>"), "")
		}
		text = text.replace(Regex("<img(?: [a-z-]+=\".+?\")+/?>"), "")


		val codeRegex =
			Regex("\\s*(?:<blockquote>\\s*)?<pre(?: [a-z-]+=\".+?\")+>(?:\\s*<code>\\s*)?([\\w\\W]+?)(?:\\s*</code>\\s*)?</pre>(?:\\s*</blockquote>)?\\s*")
		text = text.replace(codeRegex) { "```java\n${it.groupValues[1].trimIndent()}```" }

		println(text)

		text = text.replace(Regex("</?(code|tt)>"), "`")
		text = text.replace(Regex("<a( [a-z-]+=\".+?\")+>\\s*(.+?)\\s*</a>"), "$2")
		text = text.replace(Regex("</?(i|em)>"), "_")
		text = text.replace(Regex("_[^ ]"), "_ ")
		text = text.replace(Regex("<h[0-6]>\\s*(.+?)\\s*</h[0-6]>"), "> **$1**\n")
		text = text.replace(Regex("</?ul>"), "")
		text = text.replace(Regex("<li>\\s*(.+?)\\s*</li>"), " • $1")
		text = text.replace(Regex("</?strong>"), "**")
		text = text.replace(Regex("<p>\\s*(.+)\\s*</p>"), "$1\n")
		text = text.replace("<p></p>", "")

		text = text.replace(Regex("<sup>\\s*(.+?)\\s*</sup>"), "")

		text = text.replace("&lt;", "<")
		text = text.replace("&gt;", ">")
		text = text.replace("&amp;", "&")
		text = text.replace(Regex("<br>"), "\n")

		return text
	}
}
