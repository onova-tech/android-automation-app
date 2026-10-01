package com.proj.automation.ui

import android.graphics.Rect
import android.view.accessibility.AccessibilityNodeInfo
import org.w3c.dom.Element
import java.io.ByteArrayInputStream
import javax.xml.parsers.DocumentBuilderFactory

/** Builds [UiNode] snapshots from the live accessibility tree or from `uiautomator dump` XML. */
object UiSnapshots {

    private const val MAX_NODES = 5_000

    fun fromAccessibility(root: AccessibilityNodeInfo): UiNode {
        var count = 0
        fun convert(node: AccessibilityNodeInfo): UiNode {
            count++
            val rect = Rect().also { node.getBoundsInScreen(it) }
            val children = (0 until node.childCount).mapNotNull { i ->
                if (count >= MAX_NODES) null else node.getChild(i)?.let { convert(it) }
            }
            return UiNode(
                text = node.text?.toString(),
                contentDescription = node.contentDescription?.toString(),
                resourceId = node.viewIdResourceName,
                className = node.className?.toString(),
                packageName = node.packageName?.toString(),
                clickable = node.isClickable,
                editable = node.isEditable,
                scrollable = node.isScrollable,
                focused = node.isFocused,
                bounds = Bounds(rect.left, rect.top, rect.right, rect.bottom),
                children = children,
                ref = node
            )
        }
        return convert(root)
    }

    /** Parses the XML written by `uiautomator dump` (used for fixtures and replay tests). */
    fun fromUiAutomatorXml(xml: String): UiNode {
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
