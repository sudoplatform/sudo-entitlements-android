/*
 * Copyright © 2020 Anonyome Labs, Inc. All rights reserved.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.sudoplatform.sudoentitlements

import android.content.Context
import com.amazonaws.mobileconnectors.appsync.AWSAppSyncClient
import com.apollographql.apollo.api.Response
import com.apollographql.apollo.exception.ApolloHttpException
import org.mockito.kotlin.any
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.doThrow
import org.mockito.kotlin.mock
import org.mockito.kotlin.stub
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoMoreInteractions
import com.sudoplatform.sudoentitlements.graphql.CallbackHolder
import com.sudoplatform.sudoentitlements.graphql.RedeemEntitlementsMutation
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
import java.net.HttpURLConnection

/**
 * Test the correct operation of [SudoEntitlementsClient.redeemEntitlements] using mocks and spies.
 *
 * @since 2020-08-27
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
                    42
                )
            )
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

    private val mockAppSyncClient by before {
        mock<AWSAppSyncClient>().stub {
            on { mutate(any<RedeemEntitlementsMutation>()) } doReturn mutationHolder.mutationOperation
        }
    }

    private val client by before {
        DefaultSudoEntitlementsClient(
            mockContext,
            mockAppSyncClient,
            mockLogger
        )
    }

    @Before
    fun init() {
        mutationHolder.callback = null
    }

    @After
    fun fini() {
        verifyNoMoreInteractions(mockContext, mockAppSyncClient)
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

        verify(mockAppSyncClient).mutate(any<RedeemEntitlementsMutation>())
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

        verify(mockAppSyncClient).mutate(any<RedeemEntitlementsMutation>())
    }

    @Test
    fun `redeemEntitlements() should throw when response has an entitlement not found error`() = runBlocking<Unit> {

        mutationHolder.callback shouldBe null

        val errorMutationResponse by before {
            val error = com.apollographql.apollo.api.Error(
                "mock",
                emptyList(),
                mapOf("errorType" to "Failed")
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

        verify(mockAppSyncClient).mutate(any<RedeemEntitlementsMutation>())
    }

    @Test
    fun `redeemEntitlements() should throw when response has an unauthorized user error`() = runBlocking<Unit> {

        mutationHolder.callback shouldBe null

        val errorMutationResponse by before {
            val error = com.apollographql.apollo.api.Error(
                "mock",
                emptyList(),
                mapOf("errorType" to "UnauthorisedUser")
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

        verify(mockAppSyncClient).mutate(any<RedeemEntitlementsMutation>())
    }

    @Test
    fun `redeemEntitlements() should throw when response has an ambiguous entitlements error`() = runBlocking<Unit> {

        mutationHolder.callback shouldBe null

        val errorMutationResponse by before {
            val error = com.apollographql.apollo.api.Error(
                "mock",
                emptyList(),
                mapOf("errorType" to "sudoplatform.entitlements.AmbiguousEntitlementsError")
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

        verify(mockAppSyncClient).mutate(any<RedeemEntitlementsMutation>())
    }

    @Test
    fun `redeemEntitlements() should throw when response has an invalid token error`() = runBlocking<Unit> {

        mutationHolder.callback shouldBe null

        val errorMutationResponse by before {
            val error = com.apollographql.apollo.api.Error(
                "mock",
                emptyList(),
                mapOf("errorType" to "sudoplatform.InvalidTokenError")
            )
            Response.builder<RedeemEntitlementsMutation.Data>(RedeemEntitlementsMutation())
                .errors(listOf(error))
                .data(null)
                .build()
        }

        val deferredResult = async(Dispatchers.IO) {
            shouldThrow<SudoEntitlementsClient.EntitlementsException.InvalidTokenException> {
                client.redeemEntitlements()
            }
        }
        deferredResult.start()
        delay(100L)

        mutationHolder.callback shouldNotBe null
        mutationHolder.callback?.onResponse(errorMutationResponse)

        deferredResult.await()

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

        verify(mockAppSyncClient).mutate(any<RedeemEntitlementsMutation>())
    }
}
