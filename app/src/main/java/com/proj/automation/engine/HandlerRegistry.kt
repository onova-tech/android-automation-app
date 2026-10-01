package com.proj.automation.engine

import com.proj.automation.engine.actions.*
import com.proj.automation.parser.ActionType

/**
 * Builds the registry of all action handlers.
 * Called once at engine startup to wire up all available actions.
 */
fun buildHandlerRegistry(): Map<ActionType, ActionHandler> {
    return mapOf(
        ActionType.LAUNCH_APP to LaunchAppHandler(),
        ActionType.WAIT to WaitHandler(),
        ActionType.WAIT_FOR to WaitForHandler(),
        ActionType.CLICK to ClickHandler(),
        ActionType.TYPE to TypeHandler(),
        ActionType.BACK to BackHandler(),
        ActionType.HOME to HomeHandler(),
        ActionType.SCROLL to ScrollHandler(),
        ActionType.LOG to LogHandler()
    )
}
