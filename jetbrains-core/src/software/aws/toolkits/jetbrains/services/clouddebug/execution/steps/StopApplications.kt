// Copyright 2019 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.services.clouddebug.execution.steps

import com.intellij.execution.configurations.GeneralCommandLine
import software.aws.toolkits.core.credentials.toEnvironmentVariables
import software.aws.toolkits.jetbrains.services.clouddebug.execution.CloudDebugCliStep
import software.aws.toolkits.jetbrains.services.ecs.execution.EcsServiceCloudDebuggingRunSettings
import software.aws.toolkits.jetbrains.utils.execution.steps.Context
import software.aws.toolkits.jetbrains.utils.execution.steps.ParallelStep
import software.aws.toolkits.jetbrains.utils.execution.steps.Step
import software.aws.toolkits.jetbrains.utils.execution.steps.StepEmitter
import software.aws.toolkits.resources.message
import software.aws.toolkits.telemetry.ClouddebugTelemetry
import software.aws.toolkits.telemetry.Result
import java.time.Duration
import java.time.Instant

class StopApplications(
    private val settings: EcsServiceCloudDebuggingRunSettings,
    private val isCleanup: Boolean
) : ParallelStep() {
    override val stepName = if (isCleanup) {
        message("cloud_debug.step.stop_application.cleanup")
    } else {
        message("cloud_debug.step.stop_application.pre_start")
    }
    override val hidden = false

    override fun buildChildSteps(context: Context): List<Step> = settings.containerOptions.map { (containerName, _) ->
        StopApplication(settings, containerName, isCleanup)
    }
}

class StopApplication(
    private val settings: EcsServiceCloudDebuggingRunSettings,
    private val containerName: String,
    private val isCleanup: Boolean
) : CloudDebugCliStep() {
    override val stepName = if (isCleanup) {
        message("cloud_debug.step.stop_application.cleanup.resource", containerName)
    } else {
        message("cloud_debug.step.stop_application.pre_start.resource", containerName)
    }

    override fun constructCommandLine(context: Context): GeneralCommandLine = getCli(context)
        .withParameters("--verbose")
        .withParameters("--json")
        .withParameters("stop")
        .withParameters("--target")
        .withParameters(ResourceInstrumenter.getTargetForContainer(context, containerName))
        /* TODO remove this when the cli conforms to the contract */
        .withParameters("--selector")
        .withParameters(containerName)
        .withEnvironment(settings.region.toEnvironmentVariables())
        .withEnvironment(settings.credentialProvider.resolveCredentials().toEnvironmentVariables())

    override fun recordTelemetry(context: Context, startTime: Instant, result: Result) {
        ClouddebugTelemetry.stopApplication(
            project = context.getAttribute(Context.PROJECT_ATTRIBUTE),
            result = result,
            workflowToken = context.workflowToken,
            value = Duration.between(startTime, Instant.now()).toMillis().toDouble()
        )
    }

    override fun handleErrorResult(exitCode: Int, output: String, messageEmitter: StepEmitter) =
        if (isCleanup) {
            super.handleErrorResult(exitCode, output, messageEmitter)
        } else {
            messageEmitter.emitMessage(output, true)
            // suppress the error if stop fails
        }
}
