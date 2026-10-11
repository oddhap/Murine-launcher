package app.murinelauncher.icons

import org.xmlpull.v1.XmlPullParser

/** Keeps the order and category titles supplied by drawable.xml. */
internal object IconPackCategories {
    fun parse(parser: XmlPullParser, defaultTitle: String): List<Pair<String, List<String>>> {
        val result = mutableListOf<Pair<String, List<String>>>()
        val seen = mutableSetOf<String>()
        var title = defaultTitle
        var icons = mutableListOf<String>()
        fun finishCategory() {
            if (icons.isNotEmpty()) result.add(title to icons.toList())
            icons = mutableListOf()
        }
        var event = parser.eventType
        while (event != XmlPullParser.END_DOCUMENT) {
            if (event == XmlPullParser.START_TAG) {
                when (parser.name) {
                    "category" -> {
                        finishCategory()
                        title = parser.getAttributeValue(null, "title")
                            ?.takeIf { it.isNotBlank() } ?: defaultTitle
                    }
                    "item" -> parser.getAttributeValue(null, "drawable")
                        ?.takeIf { it.isNotBlank() && seen.add(it) }?.let(icons::add)
                }
            }
            event = parser.next()
        }
        finishCategory()
        return result
    }
}
