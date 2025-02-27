// Copyright 2019 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.core.credentials

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileTypes.FileTypes
import com.intellij.openapi.fileTypes.ex.FileTypeManagerEx
import com.intellij.openapi.ui.TestDialog
import com.intellij.openapi.ui.TestDialogManager
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.testFramework.ProjectRule
import com.intellij.testFramework.TestActionEvent
import com.intellij.testFramework.runInEdtAndWait
import org.assertj.core.api.Assertions.assertThat
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.mockito.kotlin.any
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyZeroInteractions
import java.io.File

class CreateOrUpdateCredentialProfilesActionTest {

    @Rule
    @JvmField
    val folderRule = TemporaryFolder()

    @Rule
    @JvmField
    val projectRule = ProjectRule()

    private lateinit var fileEditorManager: FileEditorManager
    private lateinit var localFileSystem: LocalFileSystem

    @Before
    fun setUp() {
        fileEditorManager = FileEditorManager.getInstance(projectRule.project)
        localFileSystem = LocalFileSystem.getInstance()
    }

    @After
    fun cleanUp() {
        runInEdtAndWait {
            fileEditorManager.openFiles.forEach { fileEditorManager.closeFile(it) }
        }
    }

    @Test
    fun confirmConfigFileCreated_bothFilesDoNotExist() {
        val writer = mock<ConfigFileWriter> {
            on { createFile(any()) }.doAnswer { it.getArgument<File>(0).writeText("hello") }
        }

        val configFile = File(folderRule.newFolder(), "config")
        val credFile = File(folderRule.newFolder(), "credentials")

        val sut = CreateOrUpdateCredentialProfilesAction(writer, configFile, credFile)
        TestDialogManager.setTestDialog(TestDialog.OK)

        sut.actionPerformed(TestActionEvent { projectRule.project })

        verify(writer).createFile(configFile)

        assertThat(fileEditorManager.openFiles).hasOnlyOneElementSatisfying { assertThat(it.name).isEqualTo("config") }
    }

    @Test
    fun bothFilesOpened_bothFilesExists() {
        val writer = mock<ConfigFileWriter>()

        val configFile = folderRule.newFile("config")
        val credFile = folderRule.newFile("credentials")
        // IDE interprets blank files with no extension as binary
        configFile.writeText("config")
        credFile.writeText("cred")

        val sut = CreateOrUpdateCredentialProfilesAction(writer, configFile, credFile)
        TestDialogManager.setTestDialog(TestDialog.OK)

        sut.actionPerformed(TestActionEvent { projectRule.project })

        verifyZeroInteractions(writer)

        assertThat(fileEditorManager.openFiles).hasSize(2)
            .anySatisfy { assertThat(it.name).isEqualTo("config") }
            .anySatisfy { assertThat(it.name).isEqualTo("credentials") }
    }

    @Test
    fun configFileOpened_onlyConfigExists() {
        val writer = mock<ConfigFileWriter>()

        val configFile = folderRule.newFile("config")
        val credFile = File(folderRule.newFolder(), "credentials")
        configFile.writeText("config")

        val sut = CreateOrUpdateCredentialProfilesAction(writer, configFile, credFile)
        TestDialogManager.setTestDialog(TestDialog.OK)

        sut.actionPerformed(TestActionEvent { projectRule.project })

        verifyZeroInteractions(writer)

        assertThat(fileEditorManager.openFiles).hasOnlyOneElementSatisfying { assertThat(it.name).isEqualTo("config") }
    }

    @Test
    fun credentialFileOpened_onlyCredentialsExists() {
        val writer = mock<ConfigFileWriter>()

        val configFile = File(folderRule.newFolder(), "config")
        val credFile = folderRule.newFile("credentials")
        credFile.writeText("cred")

        val sut = CreateOrUpdateCredentialProfilesAction(writer, configFile, credFile)
        TestDialogManager.setTestDialog(TestDialog.OK)

        sut.actionPerformed(TestActionEvent { projectRule.project })

        verifyZeroInteractions(writer)

        assertThat(fileEditorManager.openFiles).hasOnlyOneElementSatisfying { assertThat(it.name).isEqualTo("credentials") }
    }

    @Test
    fun emptyFileCanBeOpenedAsPlainText() {
        val writer = mock<ConfigFileWriter>()

        val configFile = File(folderRule.newFolder(), "config")
        val credFile = folderRule.newFile("credentials")

        // Mark the file as unknown for the purpose of the test. This is needed because some
        // other extensions can have weird file type association patterns (like Docker having
        // *. (?)) which makes this test fail because it is not file type unknown
        localFileSystem.refreshAndFindFileByIoFile(credFile)
        runInEdtAndWait {
            ApplicationManager.getApplication().runWriteAction {
                FileTypeManagerEx.getInstanceEx().associatePattern(
                    FileTypes.UNKNOWN,
                    "credentials"
                )
            }
        }

        val sut = CreateOrUpdateCredentialProfilesAction(writer, configFile, credFile)
        TestDialogManager.setTestDialog(TestDialog.OK)

        sut.actionPerformed(TestActionEvent { projectRule.project })

        verifyZeroInteractions(writer)

        assertThat(fileEditorManager.openFiles).hasOnlyOneElementSatisfying {
            assertThat(it.name).isEqualTo("credentials")
            assertThat(it.fileType).isEqualTo(FileTypes.PLAIN_TEXT)
        }
    }

    @Test
    fun negativeConfirmationDoesNotCreateFile() {
        val writer = mock<ConfigFileWriter>()

        val configFile = File(folderRule.newFolder(), "config")
        val credFile = File(folderRule.newFolder(), "credentials")

        val sut = CreateOrUpdateCredentialProfilesAction(writer, configFile, credFile)
        TestDialogManager.setTestDialog(TestDialog.NO)

        sut.actionPerformed(TestActionEvent { projectRule.project })

        verifyZeroInteractions(writer)
    }
}
