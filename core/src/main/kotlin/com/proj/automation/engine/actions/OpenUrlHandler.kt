package com.proj.automation.engine.actions

import com.proj.automation.engine.ActionContext
import com.proj.automation.engine.ActionHandler
import com.proj.automation.engine.ErrorCode
import com.proj.automation.engine.models.StepResult
import com.proj.automation.parser.ActionType
import com.proj.automation.parser.Step

/**
 * Opens a link (deeplink), for example `https://wa.me/<phone>?text=...`. In a plugin the URL
 * must match the plugin's approved `capabilities.deeplinks` (checked by the capability guard).
 */
class OpenUrlHandler : ActionHandler {
    override val actionType = ActionType.OPEN_URL

    override suspend fun execute(step: Step, context: ActionContext): StepResult {
        val start = context.clock()
        context.throwIfCancelled()
        val url = (step.parameters["url"] as? String)?.takeIf { it.isNotBlank() }
            ?: return context.result(actionType, start, false, "open_url requires a 'url' parameter")
        if (!context.device.openUrl(url)) return context.result(actionType, start, false, "Could not open the link", ErrorCode.E_DEVICE)
        context.sleep(SETTLE_MS) // let the target app come to the foreground
        return context.result(actionType, start, true)
    }

    companion object {
        const val SETTLE_MS = 1000L
    }
}
