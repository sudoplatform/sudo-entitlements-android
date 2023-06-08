/*
 * Copyright © 2022 Anonyome Labs, Inc. All rights reserved.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.sudoplatform.sudoentitlements

import android.content.Context
import com.amazonaws.mobileconnectors.appsync.AWSAppSyncClient
import com.amazonaws.services.cognitoidentity.model.NotAuthorizedException
import com.apollographql.apollo.api.Response
import com.apollographql.apollo.exception.ApolloException
import com.apollographql.apollo.exception.ApolloHttpException
import com.sudoplatform.sudoentitlements.graphql.CallbackHolder
import com.sudoplatform.sudoentitlements.graphql.ConsumeBooleanEntitlementsMutation
import com.sudoplatform.sudouser.SudoUserClient
import io.kotlintest.shouldBe
import io.kotlintest.shouldNotBe
import io.kotlintest.shouldThrow
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Protocol
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.doThrow
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.stub
import org.mockito.kotlin.whenever
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoMoreInteractions
import java.io.IOException
import java.net.HttpURLConnection

/**
 * Test the correct operation of [SudoEntitlementsClient.consumeBooleanEntitlements] using mocks and spies.
 */
class SudoEntitlementsConsumeBooleanEntitlementsTest : BaseTests() {

    private val mutationResult by before {
        true
    }

    private val mutationResponse by before {
        Response.builder<ConsumeBooleanEntitlementsMutation.Data>(ConsumeBooleanEntitlementsMutation(listOf("some-entitlement")))
            .data(ConsumeBooleanEntitlementsMutation.Data(mutationResult))
            .build()
    }

    private val mutationHolder = CallbackHolder<ConsumeBooleanEntitlementsMutation.Data>()

    private val mockContext by before {
        mock<Context>()
    }

    private val mockSudoUserClient by before {
        mock<SudoUserClient>()
    }

    private val mockAppSyncClient by before {
        mock<AWSAppSyncClient>().stub {
            on { mutate(any<ConsumeBooleanEntitlementsMutation>()) } doReturn mutationHolder.mutationOperation
        }
    }

    private val client by before {
        DefaultSudoEntitlementsClient(
            mockContext,
            mockSudoUserClient,
            mockAppSyncClient,
            mockLogger
        )
    }

    @Before
    fun init() {
        whenever(mockSudoUserClient.isSignedIn()).thenReturn(true)

        mutationHolder.callback = null
    }

    @After
    fun fini() {
        verifyNoMoreInteractions(mockContext, mockSudoUserClient, mockAppSyncClient)
    }

    @Test
    fun `consumeBooleanEntitlements() should return results when no error present`() = runBlocking<Unit> {
        mutationHolder.callback shouldBe null

        val deferredResult = async(Dispatchers.IO) {
            client.consumeBooleanEntitlements(arrayOf("some-entitlement"))
        }
        deferredResult.start()

        delay(100L)
        mutationHolder.callback shouldNotBe null
        mutationHolder.callback?.onResponse(mutationResponse)

        val result = deferredResult.await()
        result shouldBe Unit

        verify(mockSudoUserClient).isSignedIn()
        verify(mockAppSyncClient).mutate(any<ConsumeBooleanEntitlementsMutation>())
    }

    @Test
    fun `consumeBooleanEntitlements() should throw if not signed in`() = runBlocking<Unit> {
        whenever(mockSudoUserClient.isSignedIn()).thenReturn(false)

        mutationHolder.callback shouldBe null

        val deferredResult = async(Dispatchers.IO) {
            shouldThrow<SudoEntitlementsClient.EntitlementsException.NotSignedInException> {
                client.consumeBooleanEntitlements(arrayOf("some-entitlement"))
            }
        }
        deferredResult.start()

        delay(100L)
        mutationHolder.callback shouldBe null

        verify(mockSudoUserClient).isSignedIn()
        verify(mockAppSyncClient, never()).mutate(any<ConsumeBooleanEntitlementsMutation>())
    }

    @Test
    fun `consumeBooleanEntitlements() should throw when response has an invalid argument error`() = runBlocking<Unit> {
        mutationHolder.callback shouldBe null

        val errorMutationResponse by before {
            val error = com.apollographql.apollo.api.Error(
                "mock",
                emptyList(),
                mapOf("errorType" to "sudoplatform.InvalidArgumentError")
            )
            Response.builder<ConsumeBooleanEntitlementsMutation.Data>(ConsumeBooleanEntitlementsMutation(listOf("some-entitlement")))
                .errors(listOf(error))
                .data(null)
                .build()
        }

        val deferredResult = async(Dispatchers.IO) {
            shouldThrow<SudoEntitlementsClient.EntitlementsException.InvalidArgumentException> {
                client.consumeBooleanEntitlements(arrayOf("some-entitlement"))
            }
        }
        deferredResult.start()
        delay(100L)

        mutationHolder.callback shouldNotBe null
        mutationHolder.callback?.onResponse(errorMutationResponse)

        deferredResult.await()

        verify(mockSudoUserClient).isSignedIn()
        verify(mockAppSyncClient).mutate(any<ConsumeBooleanEntitlementsMutation>())
    }

    @Test
    fun `consumeBooleanEntitlements() should throw when response has an insufficient entitlements error`() = runBlocking<Unit> {
        mutationHolder.callback shouldBe null

        val errorMutationResponse by before {
            val error = com.apollographql.apollo.api.Error(
                "mock",
                emptyList(),
                mapOf("errorType" to "sudoplatform.InsufficientEntitlementsError")
            )
            Response.builder<ConsumeBooleanEntitlementsMutation.Data>(ConsumeBooleanEntitlementsMutation(listOf("some-entitlement")))
                .errors(listOf(error))
                .data(null)
                .build()
        }

        val deferredResult = async(Dispatchers.IO) {
            shouldThrow<SudoEntitlementsClient.EntitlementsException.InsufficientEntitlementsException> {
                client.consumeBooleanEntitlements(arrayOf("some-entitlement"))
            }
        }
        deferredResult.start()
        delay(100L)

        mutationHolder.callback shouldNotBe null
        mutationHolder.callback?.onResponse(errorMutationResponse)

        deferredResult.await()

        verify(mockSudoUserClient).isSignedIn()
        verify(mockAppSyncClient).mutate(any<ConsumeBooleanEntitlementsMutation>())
    }

    @Test
    fun `consumeBooleanEntitlements() should throw when http error occurs`() = runBlocking<Unit> {
        mutationHolder.callback shouldBe null

        val deferredResult = async(Dispatchers.IO) {
            shouldThrow<SudoEntitlementsClient.EntitlementsException.FailedException> {
                client.consumeBooleanEntitlements(arrayOf("some-entitlement"))
            }
        }
        deferredResult.start()
        delay(100L)

        val request = okhttp3.Request.Builder()
            .get()
            .url("http://www.smh.com.au")
            .build()
        val responseBody = "{}".toResponseBody("application/json; charset=utf-8".toMediaType())
        val forbidden = okhttp3.Response.Builder()
            .protocol(Protocol.HTTP_1_1)
            .code(HttpURLConnection.HTTP_FORBIDDEN)
            .request(request)
            .message("Forbidden")
            .body(responseBody)
            .build()

        mutationHolder.callback shouldNotBe null
        mutationHolder.callback?.onHttpError(ApolloHttpException(forbidden))

        deferredResult.await()

        verify(mockSudoUserClient).isSignedIn()
        verify(mockAppSyncClient).mutate(any<ConsumeBooleanEntitlementsMutation>())
    }

    @Test
    fun `consumeBooleanEntitlements() should throw when unknown error occurs`() = runBlocking<Unit> {
        mutationHolder.callback shouldBe null

        mockAppSyncClient.stub {
            on { mutate(any<ConsumeBooleanEntitlementsMutation>()) } doThrow RuntimeException("Mock Runtime Exception")
        }

        val deferredResult = async(Dispatchers.IO) {
            shouldThrow<SudoEntitlementsClient.EntitlementsException.UnknownException> {
                client.consumeBooleanEntitlements(arrayOf("some-entitlement"))
            }
        }
        deferredResult.start()
        delay(100L)

        deferredResult.await()

        verify(mockSudoUserClient).isSignedIn()
        verify(mockAppSyncClient).mutate(any<ConsumeBooleanEntitlementsMutation>())
    }

    @Test
    fun `consumeBooleanEntitlements() should find error when unauthorized error occurs`() = runBlocking<Unit> {
        mutationHolder.callback shouldBe null

        val exceptionToThrow = RuntimeException(ApolloException("", IOException(NotAuthorizedException(""))))
        mockAppSyncClient.stub {
            on { mutate(any<ConsumeBooleanEntitlementsMutation>()) } doThrow exceptionToThrow
        }

        val deferredResult = async(Dispatchers.IO) {
            shouldThrow<SudoEntitlementsClient.EntitlementsException.AuthenticationException> {
                client.consumeBooleanEntitlements(arrayOf("some-entitlement"))
            }
        }
        deferredResult.start()
        delay(200L)

        deferredResult.await()

        verify(mockSudoUserClient).isSignedIn()
        verify(mockAppSyncClient).mutate(any<ConsumeBooleanEntitlementsMutation>())
    }

    @Test
    fun `consumeBooleanEntitlements() should not suppress CancellationException`() = runBlocking<Unit> {
        mockAppSyncClient.stub {
            on { mutate(any<ConsumeBooleanEntitlementsMutation>()) } doThrow CancellationException("Mock Cancellation Exception")
        }

        shouldThrow<CancellationException> {
            client.consumeBooleanEntitlements(arrayOf("some-entitlement"))
        }

        verify(mockSudoUserClient).isSignedIn()
        verify(mockAppSyncClient).mutate(any<ConsumeBooleanEntitlementsMutation>())
    }
}
