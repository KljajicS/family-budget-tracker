package rs.budget.ui

import java.io.File
import javax.xml.parsers.DocumentBuilderFactory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test
import org.w3c.dom.Element

class SerbianResourcesTest {
    private val res = File("src/main/res")

    private fun strings(folder: String): Map<String, String> {
        val document = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(File(res, "$folder/strings.xml"))
        val nodes = document.getElementsByTagName("string")
        return (0 until nodes.length).map { nodes.item(it) as Element }
            .filter { it.getAttribute("translatable") != "false" }
            .associate { it.getAttribute("name") to it.textContent }
    }

    @Test fun `Serbian Latin translates all strings and preserves format arguments`() {
        val english = strings("values")
        val serbian = strings("values-b+sr+Latn")
        assertEquals(english.keys, serbian.keys)
        val arguments = Regex("%([0-9]+\\$)?[dsf]")
        english.forEach { (key, value) ->
            assertEquals(key, arguments.findAll(value).map { it.value }.sorted().toList(),
                arguments.findAll(serbian.getValue(key)).map { it.value }.sorted().toList())
        }
        assertFalse(serbian.values.any { text -> text.any { Character.UnicodeScript.of(it.code) == Character.UnicodeScript.CYRILLIC } })
    }
}
