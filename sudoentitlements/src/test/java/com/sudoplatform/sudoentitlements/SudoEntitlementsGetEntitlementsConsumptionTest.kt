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
import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.doReturn
import com.nhaarman.mockitokotlin2.doThrow
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.stub
import com.nhaarman.mockitokotlin2.verify
import com.nhaarman.mockitokotlin2.verifyNoMoreInteractions
import com.sudoplatform.sudoentitlements.graphql.CallbackHolder
import com.sudoplatform.sudoentitlements.graphql.GetEntitlementsConsumptionQuery
import com.sudoplatform.sudoentitlements.types.EntitlementConsumer
import io.kotlintest.shouldBe
import io.kotlintest.shouldNotBe
import io.kotlintest.shouldThrow
import java.net.HttpURLConnection
import java.util.concurrent.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.After
import org.junit.Before
import org.junit.Test

/**
 * Test the correct operation of [SudoEntitlementsClient.getEntitlements] using mocks and spies.
 *
 * @since 2020-08-26
 */
class SudoEntitlementsGetEntitlementsConsumptionTest : BaseTests() {

    private val queryResult by before {
        GetEntitlementsConsumptionQuery.GetEntitlementsConsumption(
            "typename",
            GetEntitlementsConsumptionQuery.Entitlements(
                "typename",
                1.0,
                "entitlements-set-name",
                listOf(
                    GetEntitlementsConsumptionQuery.Entitlement(
                        "typename",
                        "e.name",
                        "e.description",
                        42
                    )
                )
            ),
            listOf(GetEntitlementsConsumptionQuery.Consumption(
                "typename",
                GetEntitlementsConsumptionQuery.Consumer(
                    "typename",
                    "consumer-id",
                    "consumer-issuer"),
                "e.name",
                42,
                32,
                10
            ))
        )
    }

    private val queryResponse by before {
        Response.builder<GetEntitlementsConsumptionQuery.Data>(GetEntitlementsConsumptionQuery())
            .data(GetEntitlementsConsumptionQuery.Data(queryResult))
            .build()
    }

    private val queryHolder = CallbackHolder<GetEntitlementsConsumptionQuery.Data>()

    private val mockContext by before {
        mock<Context>()
    }

    private val mockAppSyncClient by before {
        mock<AWSAppSyncClient>().stub {
            on { query(any<GetEntitlementsConsumptionQuery>()) } doReturn queryHolder.queryOperation
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
        queryHolder.callback = null
    }

    @After
    fun fini() {
        verifyNoMoreInteractions(mockContext, mockAppSyncClient)
    }

    @Test
    fun `getEntitlementsConsumption() should return results when no error present`() = runBlocking<Unit> {

        queryHolder.callback shouldBe null

        val deferredResult = async(Dispatchers.IO) {
            client.getEntitlementsConsumption()
        }
        deferredResult.start()

        delay(100L)
        queryHolder.callback shouldNotBe null
        queryHolder.callback?.onResponse(queryResponse)

        val result = deferredResult.await()
        result.entitlements.version shouldBe 1.0
        result.entitlements.entitlementsSetName shouldBe "entitlements-set-name"
        result.entitlements.entitlements.size shouldBe 1
        with(result.entitlements.entitlements.first()) {
            name shouldBe "e.name"
            description shouldBe "e.description"
            value shouldBe 42
        }
        result.consumption.size shouldBe 1
        with(result.consumption.first()) {
            name shouldBe "e.name"
            consumer shouldBe EntitlementConsumer("consumer-id", "consumer-issuer")
            value shouldBe 42
            consumed shouldBe 32
            available shouldBe 10
        }

        verify(mockAppSyncClient).query(any<GetEntitlementsConsumptionQuery>())
    }

    @Test
    fun `getEntitlementsConsumption() should throw when query response is null`() = runBlocking<Unit> {

        queryHolder.callback shouldBe null

        val nullResponse by before {
            Response.builder<GetEntitlementsConsumptionQuery.Data>(GetEntitlementsConsumptionQuery())
                .data(null)
                .build()
        }

        val deferredResult = async(Dispatchers.IO) {
            shouldThrow<SudoEntitlementsClient.EntitlementsException.FailedException> {
                client.getEntitlementsConsumption()
            }
        }
        deferredResult.start()

        delay(100L)
        queryHolder.callback shouldNotBe null
        queryHolder.callback?.onResponse(nullResponse)

        verify(mockAppSyncClient).query(any<GetEntitlementsConsumptionQuery>())
    }

    @Test
    fun `getEntitlementsConsumption() should throw when response has an invalid token error`() = runBlocking<Unit> {

        queryHolder.callback shouldBe null

        val errorQueryResponse by before {
            val error = com.apollographql.apollo.api.Error(
                "mock",
                emptyList(),
                mapOf("errorType" to "sudoplatform.InvalidTokenError")
            )
            Response.builder<GetEntitlementsConsumptionQuery.Data>(GetEntitlementsConsumptionQuery())
                .errors(listOf(error))
                .data(null)
                .build()
        }

        val deferredResult = async(Dispatchers.IO) {
            shouldThrow<SudoEntitlementsClient.EntitlementsException.InvalidTokenException> {
                client.getEntitlementsConsumption()
            }
        }
        deferredResult.start()
        delay(100L)

        queryHolder.callback shouldNotBe null
        queryHolder.callback?.onResponse(errorQueryResponse)

        deferredResult.await()

        verify(mockAppSyncClient).query(any<GetEntitlementsConsumptionQuery>())
    }

    @Test
    fun `getEntitlementsConsumption() should throw when response has error`() = runBlocking<Unit> {

        queryHolder.callback shouldBe null

        val error = com.apollographql.apollo.api.Error(
            "mock",
            emptyList(),
            mapOf("errorType" to "DilithiumCrystalsOutOfAlignment")
        )

        val responseWithNullData by before {
            Response.builder<GetEntitlementsConsumptionQuery.Data>(GetEntitlementsConsumptionQuery())
                .errors(listOf(error))
                .build()
        }

        val deferredResult = async(Dispatchers.IO) {
            shouldThrow<SudoEntitlementsClient.EntitlementsException.FailedException> {
                client.getEntitlementsConsumption()
            }
        }
        deferredResult.start()

        delay(100L)
        queryHolder.callback shouldNotBe null
        queryHolder.callback?.onResponse(responseWithNullData)

        deferredResult.await()

        verify(mockAppSyncClient).query(any<GetEntitlementsConsumptionQuery>())
    }

    @Test
    fun `getEntitlementsConsumption() should throw when http error occurs`() = runBlocking<Unit> {

        queryHolder.callback shouldBe null

        val deferredResult = async(Dispatchers.IO) {
            shouldThrow<SudoEntitlementsClient.EntitlementsException.FailedException> {
                client.getEntitlementsConsumption()
            }
        }
        deferredResult.start()
        delay(100L)

        val request = Request.Builder()
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

        queryHolder.callback shouldNotBe null
        queryHolder.callback?.onHttpError(ApolloHttpException(forbidden))

        deferredResult.await()

        verify(mockAppSyncClient).query(any<GetEntitlementsConsumptionQuery>())
    }

    @Test
    fun `getEntitlementsConsumption() should throw when unknown error occurs`() = runBlocking<Unit> {

        queryHolder.callback shouldBe null

        mockAppSyncClient.stub {
            on { query(any<GetEntitlementsConsumptionQuery>()) } doThrow RuntimeException("Mock Runtime Exception")
        }

        val deferredResult = async(Dispatchers.IO) {
            shouldThrow<SudoEntitlementsClient.EntitlementsException.UnknownException> {
                client.getEntitlementsConsumption()
            }
        }
        deferredResult.start()

        delay(100L)
        deferredResult.await()

        verify(mockAppSyncClient).query(any<GetEntitlementsConsumptionQuery>())
    }

    @Test
    fun `getEntitlementsConsumption() should not suppress CancellationException`() = runBlocking<Unit> {

        mockAppSyncClient.stub {
            on { query(any<GetEntitlementsConsumptionQuery>()) } doThrow CancellationException("Mock Cancellation Exception")
        }

        shouldThrow<CancellationException> {
            client.getEntitlementsConsumption()
        }

        verify(mockAppSyncClient).query(any<GetEntitlementsConsumptionQuery>())
    }
}
