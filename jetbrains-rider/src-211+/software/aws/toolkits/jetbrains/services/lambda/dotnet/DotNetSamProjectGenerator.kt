// Copyright 2020 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.services.lambda.dotnet

import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.progress.DumbProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.project.rootManager
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.ui.ColorUtil
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.components.JBTabbedPane
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import com.intellij.xml.util.XmlUtil
import com.jetbrains.rider.ideaInterop.fileTypes.msbuild.CsprojFileType
import com.jetbrains.rider.ideaInterop.fileTypes.sln.SolutionFileType
import com.jetbrains.rider.projectView.SolutionManager
import com.jetbrains.rider.projectView.actions.projectTemplating.backend.ReSharperTemplateGeneratorBase
import com.jetbrains.rider.projectView.actions.projectTemplating.backend.ReSharperTemplatesInteraction
import com.jetbrains.rider.projectView.actions.projectTemplating.impl.ProjectTemplateDialogContext
import com.jetbrains.rider.projectView.actions.projectTemplating.impl.ProjectTemplateTransferableModel
import com.jetbrains.rider.ui.themes.RiderTheme
import software.aws.toolkits.jetbrains.services.lambda.BuiltInRuntimeGroups
import software.aws.toolkits.jetbrains.services.lambda.RuntimeGroup
import software.aws.toolkits.jetbrains.services.lambda.wizard.SamInitSelectionPanel
import software.aws.toolkits.jetbrains.services.lambda.wizard.SamProjectGenerator
import software.aws.toolkits.jetbrains.utils.DotNetRuntimeUtils
import software.aws.toolkits.resources.message
import java.awt.Dimension
import java.io.File
import javax.swing.JScrollPane
import javax.swing.JTabbedPane
import javax.swing.JTextPane

class DotNetSamProjectGenerator(
    private val context: ProjectTemplateDialogContext,
    group: String,
    categoryName: String,
    model: ProjectTemplateTransferableModel
) : ReSharperTemplateGeneratorBase(
    model = model,
    createSolution = true,
    createProject = true,
    item = context.entity
) {
    companion object {
        private const val SAM_HELLO_WORLD_PROJECT_NAME = "HelloWorld"
    }

    // TODO: Decouple SamProjectGenerator from the framework wizards so we can re-use its panels
    private val generator = SamProjectGenerator()
    private val samPanel = SamInitSelectionPanel(
        generator.wizardFragments,
        // Only show templates for DotNet in Rider
        runtimeFilter = { RuntimeGroup.getById(BuiltInRuntimeGroups.Dotnet).supportedRuntimes.contains(it) },
        // needed to rerun the validation when the wizard is changed
        wizardUpdateCallback = { validateData() }
    )

    private val projectStructurePanel: JTabbedPane

    private val structurePane = JTextPane().apply {
        contentType = "text/html"
        isEditable = false
        background = RiderTheme.activeFieldBackground
        border = null
    }

    init {
        title.labels = arrayOf(group, categoryName)
        initProjectTextField()
        initSamPanel()

        projectStructurePanel = JBTabbedPane()
        val structureScroll = JBScrollPane(structurePane).apply {
            horizontalScrollBarPolicy = JScrollPane.HORIZONTAL_SCROLLBAR_NEVER
            verticalScrollBarPolicy = JBScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED
            border = JBUI.Borders.empty()
            background = UIUtil.getTextFieldBackground()
            preferredSize = Dimension(1, JBUI.scale(60))
        }

        projectStructurePanel.add("Resulting project structure", structureScroll)

        updateInfo()
        super.initialize()
        super.layout()

        // Call this init method after super.initialize() to make sure solutionNameField override a base listener
        initSolutionTextField()

        addAdditionPane(samPanel.mainPanel)
        addAdditionPane(projectStructurePanel)
    }

    override fun validateData() {
        super.validateData()
        if (validationError.value != null) {
            return
        }
        samPanel.validate()?.let {
            validationError.set(it.message)
            return
        }
        validationError.set(null)
    }

    override fun updateInfo() {
        super.updateInfo()
        val sep = File.separator
        val builder = StringBuilder()
        val font = JBUI.Fonts.label()
        builder.appendLine("<html><span style=\"font-family:${font.family};font-size:${font.size}\"")

        val solutionDirectory = getSolutionDirectory()
        val projectDirectory = getProjectDirectory()

        val parentName = solutionDirectory?.parentFile?.name
        val parentStr = if (parentName.isNullOrEmpty()) sep else "$sep$parentName$sep"

        val vcsMarker = vcsPanel?.getVcsMarker()
        if (solutionDirectory != null && vcsMarker != null) {
            builder.appendLine(
                htmlText(
                    "$sep${solutionDirectory.parentFile.name}$sep",
                    "${solutionDirectory.name}$sep$vcsMarker"
                )
            )
        }

        if (solutionDirectory != null) {
            val solutionName = getSolutionName() + SolutionFileType.solutionExtensionWithDot
            builder.appendLine(htmlText(parentStr, "${solutionDirectory.name}$sep$solutionName"))
        }

        if (projectDirectory != null) {
            val projectsText = "project files"
            val projectFilesLabel = XmlUtil.escape("<$projectsText>")
            if (solutionDirectory != null && solutionDirectory != projectDirectory) {
                builder.appendLine(htmlText(parentStr, "${solutionDirectory.name}${sep}src$sep${projectDirectory.name}$sep$projectFilesLabel"))
                builder.appendLine(htmlText(parentStr, "${solutionDirectory.name}${sep}test$sep${projectDirectory.name}.Test$sep$projectFilesLabel"))
            } else {
                builder.appendLine(htmlText(parentStr, "src$sep${projectDirectory.name}$sep$projectFilesLabel"))
                builder.appendLine(htmlText(parentStr, "test$sep${projectDirectory.name}.Test$sep$projectFilesLabel"))
            }
        }

        builder.appendLine("</span></html>")
        structurePane.text = builder.toString()
        validateData()
    }

    override fun expand() = Runnable {
        val samSettings = samPanel.getNewProjectSettings()

        val solutionDirectory = getSolutionDirectory()
            ?: throw Exception(message("sam.init.error.no.solution.basepath"))

        val fileSystem = LocalFileSystem.getInstance()
        if (!solutionDirectory.exists()) {
            FileUtil.createDirectory(solutionDirectory)
        }

        val outDirVf = fileSystem.refreshAndFindFileByIoFile(solutionDirectory)
            ?: throw Exception(message("sam.init.error.no.virtual.file"))

        val progressManager = ProgressManager.getInstance()
        val samProjectBuilder = generator.createModuleBuilder()
        progressManager.runProcessWithProgressSynchronously(
            {
                samProjectBuilder.runSamInit(
                    context.project,
                    projectNameField.text,
                    samSettings,
                    null,
                    outDirVf
                )
            },
            message("sam.init.generating.template"),
            false,
            null
        )

        // Create solution file
        val projectFiles =
            File(solutionDirectory, "src").walk().filter { it.extension == CsprojFileType.defaultExtension } +
                File(solutionDirectory, "test").walk().filter { it.extension == CsprojFileType.defaultExtension }

        // Get the rest of generated files and copy to "SolutionItems" default folder in project structure
        val solutionFiles = solutionDirectory.listFiles()?.filter { it.isFile }?.toList() ?: emptyList()

        val solutionFile = ReSharperTemplatesInteraction.createSolution(
            name = getSolutionName(),
            directory = solutionDirectory,
            projectFiles = projectFiles.toList(),
            protocolHost = context.protocolHost,
            solutionFiles = solutionFiles
        ) ?: throw Exception(message("sam.init.error.solution.create.fail"))

        val project = SolutionManager.openExistingSolution(
            projectToClose = null,
            forceOpenInNewFrame = false,
            solutionFile = solutionFile,
            forceConsiderTrusted = true
        ) ?: return@Runnable

        vcsPanel?.createInitializer()?.execute(project)

        val modifiableModel = ModuleManager.getInstance(project).modules.firstOrNull()?.rootManager?.modifiableModel ?: return@Runnable
        try {
            val progressIndicator = if (progressManager.hasProgressIndicator()) progressManager.progressIndicator else DumbProgressIndicator()

            samProjectBuilder.runPostSamInit(project, modifiableModel, progressIndicator, samSettings, outDirVf)
        } finally {
            modifiableModel.dispose()
        }
    }

    override fun refreshUI() {
        super.refreshUI()
        // This restore project name when user change a solution name and switch between templates
        projectNameField.text = SAM_HELLO_WORLD_PROJECT_NAME
        validationError.set(null)
        validateData()
    }

    private fun initSolutionTextField() {
        solutionNameField.text = getPossibleName(SAM_HELLO_WORLD_PROJECT_NAME)
    }

    /**
     * The project name is generated inside SAM CLI generator and cannot be re-defined via parameters.
     * Hardcode the project name to the generated one - "HelloWorld".
     */
    private fun initProjectTextField() {
        projectNameField.text = SAM_HELLO_WORLD_PROJECT_NAME
        projectNameField.isEnabled = false
        projectNameSetByUser = true

        sameDirectoryCheckBox.isEnabled = false
    }

    private fun initSamPanel() {
        samPanel.setRuntime(DotNetRuntimeUtils.getCurrentDotNetCoreRuntime())
    }

    private fun htmlText(baseDir: String, relativePath: String) =
        "<font color=#${ColorUtil.toHex(UIUtil.getLabelDisabledForeground())}>...$baseDir</font>$relativePath<br>"
}
