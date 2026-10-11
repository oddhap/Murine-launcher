package app.murinelauncher.icons

import java.io.StringReader
import org.junit.Assert.assertEquals
import org.junit.Test
import org.xmlpull.v1.XmlPullParserException
import org.xmlpull.v1.XmlPullParserFactory

class IconPackCategoriesTest {
    private fun parse(xml: String): List<Pair<String, List<String>>> {
        val parser = XmlPullParserFactory.newInstance().newPullParser()
        parser.setInput(StringReader(xml))
        return IconPackCategories.parse(parser, "All icons")
    }

    @Test
    fun preservesPackCategoriesAndIconOrder() {
        assertEquals(
            listOf("Social" to listOf("chat", "mail"), "Games" to listOf("chess")),
            parse("""<resources><category title="Social"/><item drawable="chat"/>
                <item drawable="mail"/><category title="Games"/><item drawable="chess"/></resources>"""),
        )
    }

    @Test
    fun supportsUncategorizedIconsAndIgnoresEmptyCategories() {
        assertEquals(
            listOf("All icons" to listOf("first"), "Last" to listOf("last")),
            parse("""<resources><item drawable="first"/><category title="Empty"/>
                <category title="Last"/><item drawable="last"/></resources>"""),
        )
    }

    @Test
    fun ignoresMissingNamesAndDuplicatesAcrossCategories() {
        assertEquals(
            listOf("All icons" to listOf("chat"), "Other" to listOf("mail")),
            parse("""<resources><item/><item drawable=""/><item drawable="chat"/>
                <category title="Other"/><item drawable="chat"/><item drawable="mail"/></resources>"""),
        )
    }

    @Test
    fun blankCategoryTitlesUseDefaultLabel() {
        assertEquals(listOf("All icons" to listOf("mail")),
            parse("""<resources><category title=" "/><item drawable="mail"/></resources>"""))
    }

    @Test
    fun emptyManifestAllowsAppfilterFallback() {
        assertEquals(emptyList<Pair<String, List<String>>>(), parse("<resources/>"))
    }

    @Test(expected = XmlPullParserException::class)
    fun malformedManifestAllowsCallerToFallBack() {
        parse("<resources><item drawable=\"mail\"/></wrong>")
    }
}
