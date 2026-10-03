package com.proj.automation.ui

import org.w3c.dom.Element
import java.io.ByteArrayInputStream
import javax.xml.parsers.DocumentBuilderFactory

/** Reads `uiautomator dump` XML into [UiNode] snapshots (fixtures, replay tests, the agp tool). */
object UiXml {

    /** Parses the XML written by `uiautomator dump` (used for fixtures and replay tests). */
    fun parse(xml: String): UiNode {
        val factory = DocumentBuilderFactory.newInstance().apply {
            // fixtures are local files, but never resolve external entities anyway
            setFeature("http://apache.org/xml/features/disallow-doctype-decl", true)
            isExpandEntityReferences = false
        }
        val doc = factory.newDocumentBuilder().parse(ByteArrayInputStream(xml.toByteArray()))
        val hierarchy = doc.documentElement
        val top = hierarchy.childElements("node")
        val roots = top.map { convertXml(it) }
        return if (roots.size == 1) roots.single() else UiNode(className = "hierarchy", children = roots)
    }

    private fun convertXml(e: Element): UiNode = UiNode(
        text = e.getAttribute("text").ifEmpty { null },
        contentDescription = e.getAttribute("content-desc").ifEmpty { null },
        resourceId = e.getAttribute("resource-id").ifEmpty { null },
        className = e.getAttribute("class").ifEmpty { null },
        packageName = e.getAttribute("package").ifEmpty { null },
        clickable = e.getAttribute("clickable") == "true",
        editable = e.getAttribute("class").contains("EditText"),
        scrollable = e.getAttribute("scrollable") == "true",
        focused = e.getAttribute("focused") == "true",
        bounds = parseBounds(e.getAttribute("bounds")),
        children = e.childElements("node").map { convertXml(it) }
    )

    private val BOUNDS = Regex("""\[(-?\d+),(-?\d+)]\[(-?\d+),(-?\d+)]""")

    private fun parseBounds(s: String): Bounds =
        BOUNDS.find(s)?.destructured?.let { (l, t, r, b) -> Bounds(l.toInt(), t.toInt(), r.toInt(), b.toInt()) }
            ?: Bounds(0, 0, 0, 0)

    private fun Element.childElements(tag: String): List<Element> =
        (0 until childNodes.length).map { childNodes.item(it) }.filterIsInstance<Element>().filter { it.tagName == tag }
}
