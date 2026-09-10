package com.proj.automation.engine.actions

import android.content.Intent
import android.content.pm.PackageManager
import com.proj.automation.App
import com.proj.automation.engine.ActionContext
import com.proj.automation.engine.ActionHandler
import com.proj.automation.engine.HandlerException
import com.proj.automation.engine.models.StepResult
import com.proj.automation.parser.ActionType
import com.proj.automation.parser.Step
import kotlinx.coroutines.delay

/**
 * Starts the target app via implicit Intent.
 */
class LaunchAppHandler : ActionHandler {
    override val actionType = ActionType.LAUNCH_APP

    override suspend fun execute(step: Step, context: ActionContext): StepResult {
        val startTime = System.currentTimeMillis()
        context.throwIfCancelled()

        val packageName = step.parameters["package"] as? String
            ?: throw HandlerException("launch_app requires 'package' parameter", actionType)

        // Verify the package is installed
        val pm = App.instance.packageManager
        if (!isPackageInstalled(pm, packageName)) {
            return StepResult(
                stepIndex = 0, action = actionType, success = false,
                durationMs = System.currentTimeMillis() - startTime,
                errorMessage = "Package not found: $packageName"
            )
        }

        // Resolve the launchable activity
        val intent = pm.getLaunchIntentForPackage(packageName)
            ?: return StepResult(
                stepIndex = 0, action = actionType, success = false,
                durationMs = System.currentTimeMillis() - startTime,
                errorMessage = "No launchable activity found for: $packageName"
            )

        context.automation.startActivity(intent)
        // Wait for the app to settle
        delay(1000)

        return StepResult(
            stepIndex = 0, action = actionType, success = true,
            durationMs = System.currentTimeMillis() - startTime,
            details = mapOf("package" to packageName)
        )
    }

    private fun isPackageInstalled(pm: PackageManager, packageName: String): Boolean {
        return try {
            pm.getPackageInfo(packageName, 0)
            true
        } catch (_: PackageManager.NameNotFoundException) {
            false
        }
    }
}
