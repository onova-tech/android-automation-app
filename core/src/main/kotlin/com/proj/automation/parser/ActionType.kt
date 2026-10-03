package com.proj.automation.parser

/**
 * Enumeration of all supported action types in the workflow DSL.
 * Each value maps to a YAML key (e.g., "launch_app" → LAUNCH_APP).
 */
enum class ActionType(val yamlValue: String) {
    LAUNCH_APP("launch_app"),
    WAIT("wait"),
    WAIT_FOR("wait_for"),
    CLICK("click"),
    TYPE("type"),
    BACK("back"),
    HOME("home"),
    SCROLL("scroll"),
    LOG("log"),
    READ_TEXT("read_text"),
    READ_LIST("read_list"),
    SCROLL_UNTIL("scroll_until"),
    OPEN_URL("open_url");

    companion object {
        /** Resolve a YAML key string to its ActionType, throwing if unknown */
        fun fromYaml(value: String): ActionType {
            return entries.find { it.yamlValue == value }
                ?: throw UnknownActionException(value)
        }

        /** Set of all valid YAML action keys */
        val values: Set<String> get() = entries.map { it.yamlValue }.toSet()
    }
}
