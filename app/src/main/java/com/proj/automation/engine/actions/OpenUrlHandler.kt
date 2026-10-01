package com.proj.automation.engine.actions

import android.content.Intent
import android.net.Uri
import com.proj.automation.engine.ActionContext
import com.proj.automation.engine.ActionHandler
import com.proj.automation.engine.models.StepResult
import com.proj.automation.parser.ActionType
import com.proj.automation.parser.Step
import kotlinx.coroutines.delay

/**
 * Opens a link (deeplink) with `Intent.ACTION_VIEW`, for example `https://wa.me/<phone>?text=...`.
 * In a plugin, the URL must match the plugin's approved `capabilities.deeplinks`.
 */
class OpenUrlHandler : ActionHandler {
    override val actionType = ActionType.OPEN_URL

    override suspend fun execute(step: Step, context: ActionContext): StepResult {
        val startTime = System.currentTimeMillis()
        context.throwIfCancelled()
        val url = (step.parameters["url"] as? String)?.takeIf { it.isNotBlank() }
            ?: return StepResult(
                stepIndex = 0, action = actionType, success = false,
                durationMs = System.currentTimeMillis() - startTime,
                errorMessage = "open_url requires a 'url' parameter"
            )
        context.automation.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
        delay(1000) // let the target app come to the foreground
        return StepResult(
            stepIndex = 0, action = actionType, success = true,
            durationMs = System.currentTimeMillis() - startTime
        )
    }
}
