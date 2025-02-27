// Copyright 2021 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.services.ecs

import software.aws.toolkits.jetbrains.core.experiments.ToolkitExperiment
import software.aws.toolkits.resources.message

object EcsExecExperiment : ToolkitExperiment(
    "ecsExec",
    { message("ecs.execute_command.experiment.title") },
    { message("ecs.execute_command.experiment.description") }
)

object EcsCloudDebugExperiment : ToolkitExperiment(
    "ecsCloudDebug",
    { message("cloud_debug.experiment.title") },
    { message("cloud_debug.experiment.description") },
    default = true
)
