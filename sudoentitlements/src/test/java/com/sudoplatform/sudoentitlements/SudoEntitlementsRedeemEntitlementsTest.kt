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
import com.sudoplatform.sudoentitlements.graphql.RedeemEntitlementsMutation
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
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoMoreInteractions
import org.mockito.kotlin.whenever
import java.io.IOException
import java.net.HttpURLConnection

/**
 * Test the correct operation of [SudoEntitlementsClient.redeemEntitlements] using mocks and spies.
 */
class SudoEntitlementsRedeemEntitlementsTest : BaseTests() {

    private val mutationResult by before {
        RedeemEntitlementsMutation.RedeemEntitlements(
            "typename",
            1.0,
            1.0,
            1.0,
            "name",
            "description",
            listOf(
                RedeemEntitlementsMutation.Entitlement(
                    "typename",
                    "e.name",
                    "e.description",
                    42.0,
                ),
            ),
        )
    }

    private val mutationResponse by before {
        Response.builder<RedeemEntitlementsMutation.Data>(RedeemEntitlementsMutation())
            .data(RedeemEntitlementsMutation.Data(mutationResult))
            .build()
    }

    private val mutationHolder = CallbackHolder<RedeemEntitlementsMutation.Data>()

    private val mockContext by before {
        mock<Context>()
    }

    private val mockSudoUserClient by before {
        mock<SudoUserClient>()
    }

    private val mockAppSyncClient by before {
        mock<AWSAppSyncClient>().stub {
            on { mutate(any<RedeemEntitlementsMutation>()) } doReturn mutationHolder.mutationOperation
        }
    }

    private val client by before {
        DefaultSudoEntitlementsClient(
            mockContext,
            mockSudoUserClient,
            mockAppSyncClient,
            mockLogger,
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
    fun `redeemEntitlements() should return results when no error present`() = runBlocking<Unit> {
        mutationHolder.callback shouldBe null

        val deferredResult = async(Dispatchers.IO) {
            client.redeemEntitlements()
        }
        deferredResult.start()

        delay(100L)
        mutationHolder.callback shouldNotBe null
        mutationHolder.callback?.onResponse(mutationResponse)

        val result = deferredResult.await()
        result shouldNotBe null
        result.name shouldBe "name"
        result.description shouldBe "description"
        result.version shouldBe 1.0
        result.entitlements.isEmpty() shouldBe false
        result.entitlements.size shouldBe 1
        with(result.entitlements.first()) {
            name shouldBe "e.name"
            description shouldBe "e.description"
            value shouldBe 42
        }

        verify(mockSudoUserClient).isSignedIn()
        verify(mockAppSyncClient).mutate(any<RedeemEntitlementsMutation>())
    }

    @Test
    fun `redeemEntitlements() should throw if not signed in`() = runBlocking<Unit> {
        whenever(mockSudoUserClient.isSignedIn()).thenReturn(false)

        mutationHolder.callback shouldBe null

        val deferredResult = async(Dispatchers.IO) {
            shouldThrow<SudoEntitlementsClient.EntitlementsException.NotSignedInException> {
                client.redeemEntitlements()
            }
        }
        deferredResult.start()

        delay(100L)
        mutationHolder.callback shouldBe null

        verify(mockSudoUserClient).isSignedIn()
        verify(mockAppSyncClient, never()).mutate(any<RedeemEntitlementsMutation>())
    }

    @Test
    fun `redeemEntitlements() should throw when mutation response is null`() = runBlocking<Unit> {
        mutationHolder.callback shouldBe null

        val nullResponse by before {
            Response.builder<RedeemEntitlementsMutation.Data>(RedeemEntitlementsMutation())
                .data(null)
                .build()
        }

        val deferredResult = async(Dispatchers.IO) {
            shouldThrow<SudoEntitlementsClient.EntitlementsException.FailedException> {
                client.redeemEntitlements()
            }
        }
        deferredResult.start()
        delay(100L)

        mutationHolder.callback shouldNotBe null
        mutationHolder.callback?.onResponse(nullResponse)

        deferredResult.await()

        verify(mockSudoUserClient).isSignedIn()
        verify(mockAppSyncClient).mutate(any<RedeemEntitlementsMutation>())
    }

    @Test
    fun `redeemEntitlements() should throw when response has an entitlement not found error`() = runBlocking<Unit> {
        mutationHolder.callback shouldBe null

        val errorMutationResponse by before {
            val error = com.apollographql.apollo.api.Error(
                "mock",
                emptyList(),
                mapOf("errorType" to "Failed"),
            )
            Response.builder<RedeemEntitlementsMutation.Data>(RedeemEntitlementsMutation())
                .errors(listOf(error))
                .data(null)
                .build()
        }

        val deferredResult = async(Dispatchers.IO) {
            shouldThrow<SudoEntitlementsClient.EntitlementsException.FailedException> {
                client.redeemEntitlements()
            }
        }
        deferredResult.start()
        delay(100L)

        mutationHolder.callback shouldNotBe null
        mutationHolder.callback?.onResponse(errorMutationResponse)

        deferredResult.await()

        verify(mockSudoUserClient).isSignedIn()
        verify(mockAppSyncClient).mutate(any<RedeemEntitlementsMutation>())
    }

    @Test
    fun `redeemEntitlements() should throw when response has an unauthorized user error`() = runBlocking<Unit> {
        mutationHolder.callback shouldBe null

        val errorMutationResponse by before {
            val error = com.apollographql.apollo.api.Error(
                "mock",
                emptyList(),
                mapOf("errorType" to "UnauthorisedUser"),
            )
            Response.builder<RedeemEntitlementsMutation.Data>(RedeemEntitlementsMutation())
                .errors(listOf(error))
                .data(null)
                .build()
        }

        val deferredResult = async(Dispatchers.IO) {
            shouldThrow<SudoEntitlementsClient.EntitlementsException.FailedException> {
                client.redeemEntitlements()
            }
        }
        deferredResult.start()
        delay(100L)

        mutationHolder.callback shouldNotBe null
        mutationHolder.callback?.onResponse(errorMutationResponse)

        deferredResult.await()

        verify(mockSudoUserClient).isSignedIn()
        verify(mockAppSyncClient).mutate(any<RedeemEntitlementsMutation>())
    }

    @Test
    fun `redeemEntitlements() should throw when response has an ambiguous entitlements error`() = runBlocking<Unit> {
        mutationHolder.callback shouldBe null

        val errorMutationResponse by before {
            val error = com.apollographql.apollo.api.Error(
                "mock",
                emptyList(),
                mapOf("errorType" to "sudoplatform.entitlements.AmbiguousEntitlementsError"),
            )
            Response.builder<RedeemEntitlementsMutation.Data>(RedeemEntitlementsMutation())
                .errors(listOf(error))
                .data(null)
                .build()
        }

        val deferredResult = async(Dispatchers.IO) {
            shouldThrow<SudoEntitlementsClient.EntitlementsException.AmbiguousEntitlementsException> {
                client.redeemEntitlements()
            }
        }
        deferredResult.start()
        delay(100L)

        mutationHolder.callback shouldNotBe null
        mutationHolder.callback?.onResponse(errorMutationResponse)

        deferredResult.await()

        verify(mockSudoUserClient).isSignedIn()
        verify(mockAppSyncClient).mutate(any<RedeemEntitlementsMutation>())
    }

    @Test
    fun `redeemEntitlements() should throw when response has a NoExternalIdError`() = runBlocking<Unit> {
        mutationHolder.callback shouldBe null

        val errorMutationResponse by before {
            val error = com.apollographql.apollo.api.Error(
                "mock",
                emptyList(),
                mapOf("errorType" to "sudoplatform.entitlements.NoExternalIdError"),
            )
            Response.builder<RedeemEntitlementsMutation.Data>(RedeemEntitlementsMutation())
                .errors(listOf(error))
                .data(null)
                .build()
        }

        val deferredResult = async(Dispatchers.IO) {
            shouldThrow<SudoEntitlementsClient.EntitlementsException.NoExternalIdException> {
                client.redeemEntitlements()
            }
        }
        deferredResult.start()
        delay(100L)

        mutationHolder.callback shouldNotBe null
        mutationHolder.callback?.onResponse(errorMutationResponse)

        deferredResult.await()

        verify(mockSudoUserClient).isSignedIn()
        verify(mockAppSyncClient).mutate(any<RedeemEntitlementsMutation>())
    }

    @Test
    fun `redeemEntitlements() should throw when response has a NoBillingGroupError`() = runBlocking<Unit> {
        mutationHolder.callback shouldBe null

        val errorMutationResponse by before {
            val error = com.apollographql.apollo.api.Error(
                "mock",
                emptyList(),
                mapOf("errorType" to "sudoplatform.entitlements.NoBillingGroupError"),
            )
            Response.builder<RedeemEntitlementsMutation.Data>(RedeemEntitlementsMutation())
                .errors(listOf(error))
                .data(null)
                .build()
        }

        val deferredResult = async(Dispatchers.IO) {
            shouldThrow<SudoEntitlementsClient.EntitlementsException.NoBillingGroupException> {
                client.redeemEntitlements()
            }
        }
        deferredResult.start()
        delay(100L)

        mutationHolder.callback shouldNotBe null
        mutationHolder.callback?.onResponse(errorMutationResponse)

        deferredResult.await()

        verify(mockSudoUserClient).isSignedIn()
        verify(mockAppSyncClient).mutate(any<RedeemEntitlementsMutation>())
    }

    @Test
    fun `redeemEntitlements() should throw when response has a EntitlementsSetNotFoundError`() = runBlocking<Unit> {
        mutationHolder.callback shouldBe null

        val errorMutationResponse by before {
            val error = com.apollographql.apollo.api.Error(
                "mock",
                emptyList(),
                mapOf("errorType" to "sudoplatform.entitlements.EntitlementsSetNotFoundError"),
            )
            Response.builder<RedeemEntitlementsMutation.Data>(RedeemEntitlementsMutation())
                .errors(listOf(error))
                .data(null)
                .build()
        }

        val deferredResult = async(Dispatchers.IO) {
            shouldThrow<SudoEntitlementsClient.EntitlementsException.EntitlementsSetNotFoundException> {
                client.redeemEntitlements()
            }
        }
        deferredResult.start()
        delay(100L)

        mutationHolder.callback shouldNotBe null
        mutationHolder.callback?.onResponse(errorMutationResponse)

        deferredResult.await()

        verify(mockSudoUserClient).isSignedIn()
        verify(mockAppSyncClient).mutate(any<RedeemEntitlementsMutation>())
    }

    @Test
    fun `redeemEntitlements() should throw when response has a EntitlementsSequenceNotFoundError`() = runBlocking<Unit> {
        mutationHolder.callback shouldBe null

        val errorMutationResponse by before {
            val error = com.apollographql.apollo.api.Error(
                "mock",
                emptyList(),
                mapOf("errorType" to "sudoplatform.entitlements.EntitlementsSequenceNotFoundError"),
            )
            Response.builder<RedeemEntitlementsMutation.Data>(RedeemEntitlementsMutation())
                .errors(listOf(error))
                .data(null)
                .build()
        }

        val deferredResult = async(Dispatchers.IO) {
            shouldThrow<SudoEntitlementsClient.EntitlementsException.EntitlementsSequenceNotFoundException> {
                client.redeemEntitlements()
            }
        }
        deferredResult.start()
        delay(100L)

        mutationHolder.callback shouldNotBe null
        mutationHolder.callback?.onResponse(errorMutationResponse)

        deferredResult.await()

        verify(mockSudoUserClient).isSignedIn()
        verify(mockAppSyncClient).mutate(any<RedeemEntitlementsMutation>())
    }

    @Test
    fun `redeemEntitlements() should throw when http error occurs`() = runBlocking<Unit> {
        mutationHolder.callback shouldBe null

        val deferredResult = async(Dispatchers.IO) {
            shouldThrow<SudoEntitlementsClient.EntitlementsException.FailedException> {
                client.redeemEntitlements()
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
        verify(mockAppSyncClient).mutate(any<RedeemEntitlementsMutation>())
    }

    @Test
    fun `redeemEntitlements() should throw when unknown error occurs`() = runBlocking<Unit> {
        mutationHolder.callback shouldBe null

        mockAppSyncClient.stub {
            on { mutate(any<RedeemEntitlementsMutation>()) } doThrow RuntimeException("Mock Runtime Exception")
        }

        val deferredResult = async(Dispatchers.IO) {
            shouldThrow<SudoEntitlementsClient.EntitlementsException.UnknownException> {
                client.redeemEntitlements()
            }
        }
        deferredResult.start()
        delay(100L)

        deferredResult.await()

        verify(mockSudoUserClient).isSignedIn()
        verify(mockAppSyncClient).mutate(any<RedeemEntitlementsMutation>())
    }

    @Test
    fun `redeemEntitlements() should find error when unauthorized error occurs`() = runBlocking<Unit> {
        mutationHolder.callback shouldBe null

        val exceptionToThrow = RuntimeException(ApolloException("", IOException(NotAuthorizedException(""))))
        mockAppSyncClient.stub {
            on { mutate(any<RedeemEntitlementsMutation>()) } doThrow exceptionToThrow
        }

        val deferredResult = async(Dispatchers.IO) {
            shouldThrow<SudoEntitlementsClient.EntitlementsException.AuthenticationException> {
                client.redeemEntitlements()
            }
        }
        deferredResult.start()
        delay(200L)

        deferredResult.await()

        verify(mockSudoUserClient).isSignedIn()
        verify(mockAppSyncClient).mutate(any<RedeemEntitlementsMutation>())
    }

    @Test
    fun `redeemEntitlements() should not suppress CancellationException`() = runBlocking<Unit> {
        mockAppSyncClient.stub {
            on { mutate(any<RedeemEntitlementsMutation>()) } doThrow CancellationException("Mock Cancellation Exception")
        }

        shouldThrow<CancellationException> {
            client.redeemEntitlements()
        }

        verify(mockSudoUserClient).isSignedIn()
        verify(mockAppSyncClient).mutate(any<RedeemEntitlementsMutation>())
    }
}
