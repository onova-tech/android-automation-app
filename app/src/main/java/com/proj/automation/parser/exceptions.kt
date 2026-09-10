package com.proj.automation.parser

/** Thrown when YAML syntax is invalid (malformed, bad indentation, etc.) */
class YamlParseException(
    message: String,
    val line: Int = 0,
    val column: Int = 0
) : Exception("YAML Error at line $line, column $column: $message")

/** Thrown when a step references an action type not in the ActionType enum */
class UnknownActionException(
    val unknownAction: String
) : Exception(
    "Unknown action '$unknownAction'. Valid actions: ${ActionType.values.joinToString(", ")}"
)

/** Thrown when a parsed step has missing required parameters */
class ValidationException(
    val field: String,
    message: String
) : Exception("Validation error in '$field': $message")
