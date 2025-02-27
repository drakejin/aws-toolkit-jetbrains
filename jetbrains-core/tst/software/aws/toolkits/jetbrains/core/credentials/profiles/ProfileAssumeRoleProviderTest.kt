// Copyright 2021 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.core.credentials.profiles

import com.intellij.openapi.ui.TestDialogManager
import com.intellij.openapi.ui.TestInputDialog
import com.intellij.testFramework.ApplicationRule
import org.assertj.core.api.Assertions.assertThat
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider
import software.amazon.awssdk.profiles.Profile
import software.amazon.awssdk.profiles.ProfileProperty
import software.amazon.awssdk.services.sts.StsClient
import software.amazon.awssdk.services.sts.model.AssumeRoleRequest
import software.amazon.awssdk.services.sts.model.AssumeRoleResponse
import software.amazon.awssdk.utils.SdkAutoCloseable
import software.aws.toolkits.core.utils.delegateMock
import software.aws.toolkits.core.utils.test.aString
import java.time.Duration
import java.time.Instant

class ProfileAssumeRoleProviderTest {
    @Rule
    @JvmField
    val application = ApplicationRule()

    private val mfaToken = "SomeToken"
    private lateinit var parentProvider: AwsCredentialsProvider
    private lateinit var stsClient: StsClient

    @Before
    fun setup() {
        parentProvider = mock(extraInterfaces = arrayOf(SdkAutoCloseable::class))

        stsClient = delegateMock {
            on { assumeRole(any<AssumeRoleRequest>()) } doReturn AssumeRoleResponse.builder()
                .credentials { c ->
                    c.accessKeyId(aString())
                    c.secretAccessKey(aString())
                    c.sessionToken(aString())
                    c.expiration(Instant.now().plus(Duration.ofHours(1)))
                }.build()
        }

        TestDialogManager.setTestInputDialog { mfaToken }
    }

    @After
    fun tearDown() {
        TestDialogManager.setTestInputDialog(TestInputDialog.DEFAULT)
    }

    @Test
    fun `role_arn gets passed`() {
        val role = aString()
        val profile = profile {
            put(ProfileProperty.ROLE_ARN, role)
        }

        ProfileAssumeRoleProvider(stsClient, parentProvider, profile).resolveCredentials()

        argumentCaptor<AssumeRoleRequest> {
            verify(stsClient).assumeRole(capture())

            assertThat(firstValue.roleArn()).isEqualTo(role)
        }
    }

    @Test
    fun `duration_seconds gets respected if provided`() {
        val profile = profile {
            put(ProfileProperty.ROLE_ARN, aString())
            put(ProfileProperty.DURATION_SECONDS, "12345")
        }

        ProfileAssumeRoleProvider(stsClient, parentProvider, profile).resolveCredentials()

        argumentCaptor<AssumeRoleRequest> {
            verify(stsClient).assumeRole(capture())

            assertThat(firstValue.durationSeconds()).isEqualTo(12345)
        }
    }

    @Test
    fun `duration_seconds uses default if not provided`() {
        val profile = profile {
            put(ProfileProperty.ROLE_ARN, aString())
            put(ProfileProperty.DURATION_SECONDS, "abc")
        }

        ProfileAssumeRoleProvider(stsClient, parentProvider, profile).resolveCredentials()

        argumentCaptor<AssumeRoleRequest> {
            verify(stsClient).assumeRole(capture())

            assertThat(firstValue.durationSeconds()).isEqualTo(3600)
        }
    }

    @Test
    fun `duration_seconds uses default if invalid format`() {
        val profile = profile {
            put(ProfileProperty.ROLE_ARN, aString())
        }

        ProfileAssumeRoleProvider(stsClient, parentProvider, profile).resolveCredentials()

        argumentCaptor<AssumeRoleRequest> {
            verify(stsClient).assumeRole(capture())

            assertThat(firstValue.durationSeconds()).isEqualTo(3600)
        }
    }

    @Test
    fun `MFA is prompted if keys are specified`() {
        val mfaSerial = aString()
        val profile = profile {
            put(ProfileProperty.ROLE_ARN, aString())
            put(ProfileProperty.MFA_SERIAL, mfaSerial)
        }

        ProfileAssumeRoleProvider(stsClient, parentProvider, profile).resolveCredentials()

        argumentCaptor<AssumeRoleRequest> {
            verify(stsClient).assumeRole(capture())

            assertThat(firstValue.tokenCode()).isEqualTo(mfaToken)
        }
    }

    @Test
    fun `external ID is respected if provided`() {
        val id = aString()
        val profile = profile {
            put(ProfileProperty.ROLE_ARN, aString())
            put(ProfileProperty.EXTERNAL_ID, id)
        }

        ProfileAssumeRoleProvider(stsClient, parentProvider, profile).resolveCredentials()

        argumentCaptor<AssumeRoleRequest> {
            verify(stsClient).assumeRole(capture())

            assertThat(firstValue.externalId()).isEqualTo(id)
        }
    }

    @Test
    fun `role session name is respected if provided`() {
        val name = aString()
        val profile = profile {
            put(ProfileProperty.ROLE_ARN, aString())
            put(ProfileProperty.ROLE_SESSION_NAME, name)
        }

        ProfileAssumeRoleProvider(stsClient, parentProvider, profile).resolveCredentials()

        argumentCaptor<AssumeRoleRequest> {
            verify(stsClient).assumeRole(capture())

            assertThat(firstValue.roleSessionName()).isEqualTo(name)
        }
    }

    @Test
    fun `calling close shuts down parent provider and client`() {
        val profile = profile {
            put(ProfileProperty.ROLE_ARN, aString())
        }

        ProfileAssumeRoleProvider(stsClient, parentProvider, profile).close()

        verify(stsClient).close()
        verify(parentProvider as SdkAutoCloseable).close()
    }

    private fun profile(properties: MutableMap<String, String>.() -> Unit) =
        Profile.builder().name(aString())
            .properties(mutableMapOf<String, String>().apply { properties(this) })
            .build()
}
