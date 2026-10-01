package com.proj.automation.resolve

/** Expected kind of element; used to filter and rank candidates. */
enum class Role(val yamlValue: String) {
    BUTTON("button"),
    EDIT_TEXT("edit_text"),
    TEXT("text"),
    ANY("any");

    companion object {
        fun fromYaml(value: String): Role =
            entries.find { it.yamlValue == value }
                ?: throw IllegalArgumentException("Unknown role '$value'; expected one of ${entries.map { it.yamlValue }}")
    }
}

/** Exact selectors tried before any ranking. */
data class Hints(
    val resourceId: String? = null,
    val contentDescription: String? = null,
    val text: String? = null
) {
    val isEmpty: Boolean get() = resourceId == null && contentDescription == null && text == null
    fun strings(): List<String> = listOfNotNull(resourceId, contentDescription, text)
    fun map(f: (String) -> String) = Hints(resourceId?.let(f), contentDescription?.let(f), text?.let(f))
}

/**
 * DSL v2 target: what the element is for ([intent]), what it is ([role]), exact [hints], and
 * where it usually is ([region], e.g. `bottom-right`). See docs/vision/laya-resolution.md.
 */
data class Target(
    val intent: String? = null,
    val role: Role = Role.ANY,
    val hints: Hints = Hints(),
    val region: String? = null,
    /** Minimum ranking score to accept a candidate that no exact hint identified */
    val minConfidence: Double = DEFAULT_MIN_CONFIDENCE
) {
    fun strings(): List<String> = listOfNotNull(intent) + hints.strings()
    fun map(f: (String) -> String) = copy(intent = intent?.let(f), hints = hints.map(f))

    companion object {
        const val DEFAULT_MIN_CONFIDENCE = 0.6
        private val REGION = Regex("""(top|middle|bottom)?-?(left|center|right)?""")

        /** Parses the YAML `target:` mapping. Throws [IllegalArgumentException] on bad input. */
        fun parse(raw: Map<*, *>): Target {
            val unknown = raw.keys.map { it.toString() } - setOf("intent", "role", "hints", "region", "min_confidence")
            require(unknown.isEmpty()) { "unknown target keys $unknown" }
            val hints = (raw["hints"] as? Map<*, *>).orEmpty()
            val badHints = hints.keys.map { it.toString() } - setOf("resource_id", "content_description", "text")
            require(badHints.isEmpty()) { "unknown hint keys $badHints" }
            val region = raw["region"]?.toString()
            require(region == null || (region.isNotBlank() && REGION.matches(region))) {
                "region must look like 'bottom-right', 'top', 'center'; got '$region'"
            }
            val target = Target(
                intent = raw["intent"]?.toString(),
                role = raw["role"]?.toString()?.let { Role.fromYaml(it) } ?: Role.ANY,
                hints = Hints(
                    resourceId = hints["resource_id"]?.toString(),
                    contentDescription = hints["content_description"]?.toString(),
                    text = hints["text"]?.toString()
                ),
                region = region,
                minConfidence = (raw["min_confidence"] as? Number)?.toDouble() ?: DEFAULT_MIN_CONFIDENCE
            )
            require(target.intent != null || !target.hints.isEmpty) { "a target needs an intent or at least one hint" }
            require(target.minConfidence in 0.0..1.0) { "min_confidence must be between 0 and 1" }
            return target
        }
    }
}
