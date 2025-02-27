// Copyright 2019 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0
package software.aws.toolkits.jetbrains.services.s3

import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileTypes.FileTypes
import com.intellij.testFramework.DisposableRule
import com.intellij.testFramework.ProjectRule
import com.intellij.testFramework.runInEdtAndWait
import org.assertj.core.api.Assertions.assertThat
import org.junit.Rule
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.stub
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyZeroInteractions
import software.amazon.awssdk.services.s3.S3Client
import software.amazon.awssdk.services.s3.model.Bucket
import software.amazon.awssdk.services.s3.model.DeleteBucketRequest
import software.amazon.awssdk.services.s3.model.DeleteObjectsRequest
import software.amazon.awssdk.services.s3.model.ListObjectVersionsRequest
import software.amazon.awssdk.services.s3.model.ListObjectVersionsResponse
import software.amazon.awssdk.services.s3.model.ObjectVersion
import software.amazon.awssdk.services.s3.paginators.ListObjectVersionsIterable
import software.aws.toolkits.core.s3.deleteBucketAndContents
import software.aws.toolkits.jetbrains.core.MockClientManagerRule
import software.aws.toolkits.jetbrains.services.s3.bucketActions.DeleteBucketAction
import software.aws.toolkits.jetbrains.services.s3.editor.S3VirtualBucket
import software.aws.toolkits.jetbrains.utils.associateFilePattern
import java.util.function.Consumer

class DeleteBucketTest {

    @Rule
    @JvmField
    val projectRule = ProjectRule()

    @JvmField
    @Rule
    val disposableRule = DisposableRule()

    @JvmField
    @Rule
    val mockClientManager = MockClientManagerRule()

    private val bucket = Bucket.builder().name("foo").build()

    @Test
    fun deleteEmptyBucket() {
        val s3Mock = mockClientManager.create<S3Client>()
        val mockBucket = S3BucketNode(projectRule.project, bucket)
        val emptyVersionList = mutableListOf<ObjectVersion>()

        s3Mock.stub {
            on { listObjectVersionsPaginator(any<ListObjectVersionsRequest>()) } doReturn
                ListObjectVersionsIterable(s3Mock, ListObjectVersionsRequest.builder().build())
        }
        s3Mock.stub {
            on { listObjectVersions(any<ListObjectVersionsRequest>()) } doReturn
                ListObjectVersionsResponse.builder().versions(emptyVersionList).isTruncated(false).build()
        }

        val deleteBucketAction = DeleteBucketAction()
        deleteBucketAction.performDelete(mockBucket)
        verify(s3Mock).deleteBucket(any<Consumer<DeleteBucketRequest.Builder>>())
    }

    @Test
    fun deleteBucketWithVersionedObjects() {
        val s3Mock = mockClientManager.create<S3Client>()
        val mockBucket = S3BucketNode(projectRule.project, bucket)

        val objectVersionList = mutableListOf(
            ObjectVersion.builder().eTag("123").key("1111").build(),
            ObjectVersion.builder().eTag("123").key("1111").build()
        )

        s3Mock.stub {
            on { listObjectVersionsPaginator(any<ListObjectVersionsRequest>()) } doReturn
                ListObjectVersionsIterable(s3Mock, ListObjectVersionsRequest.builder().build())
        }
        s3Mock.stub {
            on { listObjectVersions(any<ListObjectVersionsRequest>()) } doReturn
                ListObjectVersionsResponse.builder().versions(objectVersionList).isTruncated(false).build()
        }

        val deleteBucketAction = DeleteBucketAction()
        deleteBucketAction.performDelete(mockBucket)
        verify(s3Mock).deleteObjects(any<Consumer<DeleteObjectsRequest.Builder>>())
        verify(s3Mock).deleteBucket(any<Consumer<DeleteBucketRequest.Builder>>())
    }

    @Test(expected = NullPointerException::class)
    fun deleteBucketWhichDoesNotExist() {
        val s3Mock = mockClientManager.create<S3Client>()
        s3Mock.stub {
            on { listObjectVersionsPaginator(any<ListObjectVersionsRequest>()) } doReturn
                ListObjectVersionsIterable(s3Mock, ListObjectVersionsRequest.builder().build())
        }

        s3Mock.deleteBucketAndContents("")
        verifyZeroInteractions(s3Mock.deleteBucket(any<Consumer<DeleteBucketRequest.Builder>>()))
    }

    @Test
    fun deleteBucketClosesItsEditor() {
        val s3Mock = mockClientManager.create<S3Client>()
        val mockBucket = S3BucketNode(projectRule.project, bucket)
        val emptyVersionList = mutableListOf<ObjectVersion>()

        s3Mock.stub {
            on { listObjectVersionsPaginator(any<ListObjectVersionsRequest>()) } doReturn
                ListObjectVersionsIterable(s3Mock, ListObjectVersionsRequest.builder().build())
        }
        s3Mock.stub {
            on { listObjectVersions(any<ListObjectVersionsRequest>()) } doReturn
                ListObjectVersionsResponse.builder().versions(emptyVersionList).isTruncated(false).build()
        }

        runInEdtAndWait {
            // Silly hack because test file editor impl has a bunch of asserts about the document/psi that don't exist in the real impl
            associateFilePattern(FileTypes.PLAIN_TEXT, bucket.name(), disposableRule.disposable)

            assertThat(openEditor(projectRule.project, bucket.name())).isNotNull
        }

        val s3VirtualBucket = S3VirtualBucket(bucket.name(), "", s3Mock, projectRule.project)
        val fileEditorManager = FileEditorManager.getInstance(projectRule.project)

        assertThat(fileEditorManager.openFiles).contains(s3VirtualBucket)

        val deleteBucketAction = DeleteBucketAction()
        deleteBucketAction.performDelete(mockBucket)
        verify(s3Mock).deleteBucket(any<Consumer<DeleteBucketRequest.Builder>>())

        assertThat(fileEditorManager.openFiles).doesNotContain(s3VirtualBucket)
    }
}
