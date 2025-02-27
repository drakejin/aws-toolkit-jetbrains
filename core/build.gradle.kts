// Copyright 2019 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

val awsSdkVersion: String by project
val jacksonVersion: String by project
val junitVersion: String by project

plugins {
    id("toolkit-kotlin-conventions")
    id("toolkit-testing")
    id("toolkit-integration-testing")
}

dependencies {
    api(project(":resources"))
    api(project(":sdk-codegen"))

    api("com.fasterxml.jackson.module:jackson-module-kotlin:$jacksonVersion")
    api("com.fasterxml.jackson.datatype:jackson-datatype-jsr310:$jacksonVersion")
    api("com.fasterxml.jackson.dataformat:jackson-dataformat-xml:$jacksonVersion")
    api("software.amazon.awssdk:cognitoidentity:$awsSdkVersion")
    api("software.amazon.awssdk:ecr:$awsSdkVersion")
    api("software.amazon.awssdk:ecs:$awsSdkVersion")
    api("software.amazon.awssdk:lambda:$awsSdkVersion")
    api("software.amazon.awssdk:s3:$awsSdkVersion")
    api("software.amazon.awssdk:sso:$awsSdkVersion")
    api("software.amazon.awssdk:ssooidc:$awsSdkVersion")
    api("software.amazon.awssdk:sts:$awsSdkVersion")

    testImplementation("junit:junit:$junitVersion")
}
