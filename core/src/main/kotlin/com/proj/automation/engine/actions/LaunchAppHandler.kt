package com.proj.automation.engine.actions

import com.proj.automation.engine.ActionContext
import com.proj.automation.engine.ActionHandler
import com.proj.automation.engine.ErrorCode
import com.proj.automation.engine.models.StepResult
import com.proj.automation.parser.ActionType
import com.proj.automation.parser.Step
import com.proj.automation.engine.LaunchResult

/** Opens an app by package name and lets it settle. */
class LaunchAppHandler : ActionHandler {
    override val actionType = ActionType.LAUNCH_APP

    override suspend fun execute(step: Step, context: ActionContext): StepResult {
        val start = context.clock()
        context.throwIfCancelled()
        val pkg = (step.parameters["package"] as? String)?.takeIf { it.isNotBlank() }
            ?: return context.result(actionType, start, false, "launch_app requires a 'package' parameter")
        return when (context.device.launchApp(pkg)) {
            LaunchResult.OK -> {
                context.sleep(SETTLE_MS)
                context.result(actionType, start, true, details = mapOf("package" to pkg))
            }
            LaunchResult.NOT_INSTALLED -> context.result(actionType, start, false, "App not installed: $pkg", ErrorCode.E_DEVICE)
            LaunchResult.NOT_LAUNCHABLE -> context.result(actionType, start, false, "App cannot be launched: $pkg", ErrorCode.E_DEVICE)
        }
    }

    companion object {
        const val SETTLE_MS = 1000L
    }
}
