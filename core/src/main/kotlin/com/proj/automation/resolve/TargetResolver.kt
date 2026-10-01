package com.proj.automation.resolve

import com.proj.automation.ui.UiNode
import java.text.Normalizer

/** Outcome of resolving a [Target] against a screen snapshot. */
sealed class Resolution {
    data class Found(
        val node: UiNode,
        /** 1.0 for an exact hint; the ranking score otherwise */
        val confidence: Double,
        /** "hint:resource_id", "hint:content_description", "hint:text" or "ranked" */
        val stage: String
    ) : Resolution()

    data class NotFound(val reason: String) : Resolution()

    /** Candidates exist but none is clearly the target: the caller must not guess */
    data class Ambiguous(val reason: String, val top: List<Pair<UiNode, Double>>) : Resolution()
}

/**
 * Resolves targets in two stages (docs/vision/laya-resolution.md, stages 2 and 3):
 *
 * 1. **Exact hints** — `resource_id`, then `content_description`, then `text`. A hint matching
 *    exactly one element wins with confidence 1.0; several matches go on to ranking among them.
 * 2. **Heuristic ranking** — candidates filtered by role and scored by label similarity to the
 *    intent and hints, role fit and screen region. The best one is accepted only if it reaches
 *    `min_confidence` and clearly beats the runner-up; otherwise the result is [Resolution.Ambiguous].
 *
 * This ranker is the baseline that a learned model (Laya, ADR-006) must beat; [rerank] is where
 * that model would plug in later.
 */
class TargetResolver(
    /** Optional re-ranker over the shortlisted candidates (future Laya stage). Returns null to skip. */
    private val rerank: ((Target, List<Pair<UiNode, Double>>) -> List<Pair<UiNode, Double>>?)? = null
) {

    fun resolve(target: Target, root: UiNode?): Resolution {
        if (root == null) return Resolution.NotFound("No screen available")
        val all = root.walk().toList()

        var pool: List<UiNode>? = null
        for ((kind, matches) in hintMatches(target.hints, all)) {
            when {
                matches.size == 1 -> return Resolution.Found(matches.single(), 1.0, "hint:$kind")
                matches.size > 1 -> { pool = matches; break }
            }
        }

        val candidates = (pool ?: all).filter { fitsRole(it, target.role) }
        if (candidates.isEmpty()) return Resolution.NotFound("No ${target.role.yamlValue} element on screen")
        if (target.intent == null && pool == null) return Resolution.NotFound("No element matches the hints")

        val screen = root.bounds
        var ranked = candidates
            .map { it to score(target, it, screen) }
            .sortedByDescending { it.second }
            .take(SHORTLIST)
        rerank?.invoke(target, ranked)?.let { ranked = it.sortedByDescending { p -> p.second } }

        val (best, bestScore) = ranked.first()
        val runnerUp = ranked.getOrNull(1)?.second ?: 0.0
        return when {
            bestScore < target.minConfidence ->
                Resolution.Ambiguous("Best match scored %.2f, below %.2f".format(bestScore, target.minConfidence), ranked.take(3))
            bestScore - runnerUp < MIN_MARGIN ->
                Resolution.Ambiguous("Two elements match almost equally (%.2f vs %.2f)".format(bestScore, runnerUp), ranked.take(3))
            else -> Resolution.Found(best, bestScore, "ranked")
        }
    }

    private fun hintMatches(hints: Hints, all: List<UiNode>): List<Pair<String, List<UiNode>>> = listOfNotNull(
        hints.resourceId?.let { id -> "resource_id" to all.filter { it.resourceId == id } },
        hints.contentDescription?.let { d -> "content_description" to all.filter { normalize(it.contentDescription) == normalize(d) } },
        hints.text?.let { t -> "text" to all.filter { normalize(it.text) == normalize(t) } }
    )

    private fun fitsRole(node: UiNode, role: Role): Boolean = when (role) {
        Role.BUTTON -> node.clickable
        Role.EDIT_TEXT -> node.editable
        Role.TEXT -> node.label != null
        Role.ANY -> node.clickable || node.editable || node.label != null
    }

    internal fun score(target: Target, node: UiNode, screen: com.proj.automation.ui.Bounds): Double {
        val text = textScore(target, node)
        val role = roleScore(node, target.role)
        val region = target.region?.let { regionScore(it, node, screen) }
        return if (region == null) 0.75 * text + 0.25 * role
        else 0.6 * text + 0.2 * role + 0.2 * region
    }

    private fun textScore(target: Target, node: UiNode): Double {
        val labels = listOfNotNull(node.text, node.contentDescription, node.resourceId?.substringAfterLast('/')?.replace('_', ' '))
            .map { tokens(it) }.filter { it.isNotEmpty() }
        if (labels.isEmpty()) return 0.0
        val query = tokens(listOfNotNull(target.intent, target.hints.text, target.hints.contentDescription).joinToString(" "))
        if (query.isEmpty()) return 0.0

        val hintTexts = listOfNotNull(target.hints.text, target.hints.contentDescription).map { normalize(it) }.filter { it.isNotEmpty() }
        return labels.maxOf { label ->
            val labelHits = label.count { l -> query.any { q -> sameWord(l, q) } }.toDouble() / label.size
            val queryHits = query.count { q -> label.any { l -> sameWord(l, q) } }.toDouble() / query.size
            val overlap = 0.7 * labelHits + 0.3 * queryHits
            val joined = label.joinToString(" ")
            val hintNear = hintTexts.any { h -> joined.isNotEmpty() && (h.contains(joined) || joined.contains(h)) }
            if (hintNear) maxOf(overlap, 0.9) else overlap
        }
    }

    private fun roleScore(node: UiNode, role: Role): Double {
        val cls = node.className.orEmpty()
        return when (role) {
            Role.BUTTON -> if (cls.contains("Button")) 1.0 else if (node.clickable) 0.8 else 0.0
            Role.EDIT_TEXT -> if (node.editable) 1.0 else 0.0
            Role.TEXT -> if (node.label != null) 1.0 else 0.0
            Role.ANY -> 0.5
        }
    }

    private fun regionScore(region: String, node: UiNode, screen: com.proj.automation.ui.Bounds): Double {
        if (screen.isEmpty || node.bounds.isEmpty) return 0.5
        val parts = region.split('-').filter { it.isNotBlank() }
        val x = (node.bounds.centerX - screen.left).toDouble() / (screen.right - screen.left)
        val y = (node.bounds.centerY - screen.top).toDouble() / (screen.bottom - screen.top)
        val hits = parts.count { p ->
            when (p) {
                "top" -> y < 1.0 / 3
                "middle" -> y in 1.0 / 3..2.0 / 3
                "bottom" -> y > 2.0 / 3
                "left" -> x < 1.0 / 3
                "center" -> x in 1.0 / 3..2.0 / 3
                "right" -> x > 2.0 / 3
                else -> false
            }
        }
        return hits.toDouble() / parts.size
    }

    companion object {
        private const val SHORTLIST = 8
        private const val MIN_MARGIN = 0.05

        /** Words that say nothing about which element is meant */
        private val STOPWORDS = setOf(
            "a", "an", "the", "to", "of", "that", "which", "in", "on", "for", "and", "or", "is", "with",
            "o", "os", "as", "um", "uma", "de", "da", "do", "das", "dos", "que", "para", "no", "na", "e", "com",
            "button", "botao", "field", "campo", "element", "elemento"
        )

        internal fun normalize(s: String?): String =
            Normalizer.normalize(s.orEmpty(), Normalizer.Form.NFD)
                .replace(Regex("\\p{M}+"), "")
                .lowercase()
                .replace(Regex("[^a-z0-9]+"), " ")
                .trim()

        internal fun tokens(s: String): List<String> =
            normalize(s).split(' ')
                // numbers and single letters (amounts, "R$") say nothing about what an element is
                .filter { it.length > 1 && !it.all(Char::isDigit) && it !in STOPWORDS }

        /** Same word, allowing simple inflections: "send"/"sends", "enviar"/"envia" */
        internal fun sameWord(a: String, b: String): Boolean {
            if (a == b) return true
            val n = minOf(a.length, b.length)
            return n >= 4 && a.take(n - (if (n > 4) 1 else 0)) == b.take(n - (if (n > 4) 1 else 0)) &&
                kotlin.math.abs(a.length - b.length) <= 2
        }
    }
}
