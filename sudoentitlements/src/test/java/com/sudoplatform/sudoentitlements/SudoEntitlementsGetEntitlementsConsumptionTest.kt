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
import com.sudoplatform.sudoentitlements.graphql.GetEntitlementsConsumptionQuery
import com.sudoplatform.sudoentitlements.types.EntitlementConsumer
import com.sudoplatform.sudouser.SudoUserClient
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

/**
 * Test the correct operation of [SudoEntitlementsClient.getEntitlements] using mocks and spies.
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
            listOf(
                GetEntitlementsConsumptionQuery.Consumption(
                    "typename",
                    GetEntitlementsConsumptionQuery.Consumer(
                        "typename",
                        "consumer-id",
                        "consumer-issuer"
                    ),
                    "e.name",
                    42,
                    32,
                    10,
                    50.0,
                    100.0
                )
            )
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

    private val mockSudoUserClient by before {
        mock<SudoUserClient>()
    }

    private val mockAppSyncClient by before {
        mock<AWSAppSyncClient>().stub {
            on { query(any<GetEntitlementsConsumptionQuery>()) } doReturn queryHolder.queryOperation
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

        queryHolder.callback = null
    }

    @After
    fun fini() {
        verifyNoMoreInteractions(mockContext, mockSudoUserClient, mockAppSyncClient)
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
            firstConsumedAtEpochMs shouldBe 50.0
            lastConsumedAtEpochMs shouldBe 100.0
        }

        verify(mockSudoUserClient).isSignedIn()
        verify(mockAppSyncClient).query(any<GetEntitlementsConsumptionQuery>())
    }

    @Test
    fun `getEntitlementsConsumption() should throw if not signed in`() = runBlocking<Unit> {
        whenever(mockSudoUserClient.isSignedIn()).thenReturn(false)

        queryHolder.callback shouldBe null

        val deferredResult = async(Dispatchers.IO) {
            shouldThrow<SudoEntitlementsClient.EntitlementsException.NotSignedInException> {
                client.getEntitlementsConsumption()
            }
        }
        deferredResult.start()

        delay(100L)
        queryHolder.callback shouldBe null

        verify(mockSudoUserClient).isSignedIn()
        verify(mockAppSyncClient, never()).query(any<GetEntitlementsConsumptionQuery>())
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

        verify(mockSudoUserClient).isSignedIn()
        verify(mockAppSyncClient).query(any<GetEntitlementsConsumptionQuery>())
    }

    @Test
    fun `getEntitlementsConsumption() should throw when response has a NoExternalIdError`() = runBlocking<Unit> {
        queryHolder.callback shouldBe null

        val errorQueryResponse by before {
            val error = com.apollographql.apollo.api.Error(
                "mock",
                emptyList(),
                mapOf("errorType" to "sudoplatform.entitlements.NoExternalIdError")
            )
            Response.builder<GetEntitlementsConsumptionQuery.Data>(GetEntitlementsConsumptionQuery())
                .errors(listOf(error))
                .data(null)
                .build()
        }

        val deferredResult = async(Dispatchers.IO) {
            shouldThrow<SudoEntitlementsClient.EntitlementsException.NoExternalIdException> {
                client.getEntitlementsConsumption()
            }
        }
        deferredResult.start()
        delay(100L)

        queryHolder.callback shouldNotBe null
        queryHolder.callback?.onResponse(errorQueryResponse)

        deferredResult.await()

        verify(mockSudoUserClient).isSignedIn()
        verify(mockAppSyncClient).query(any<GetEntitlementsConsumptionQuery>())
    }

    @Test
    fun `getEntitlementsConsumption() should throw when response has a NoBillingGroupError`() = runBlocking<Unit> {
        queryHolder.callback shouldBe null

        val errorQueryResponse by before {
            val error = com.apollographql.apollo.api.Error(
                "mock",
                emptyList(),
                mapOf("errorType" to "sudoplatform.entitlements.NoBillingGroupError")
            )
            Response.builder<GetEntitlementsConsumptionQuery.Data>(GetEntitlementsConsumptionQuery())
                .errors(listOf(error))
                .data(null)
                .build()
        }

        val deferredResult = async(Dispatchers.IO) {
            shouldThrow<SudoEntitlementsClient.EntitlementsException.NoBillingGroupException> {
                client.getEntitlementsConsumption()
            }
        }
        deferredResult.start()
        delay(100L)

        queryHolder.callback shouldNotBe null
        queryHolder.callback?.onResponse(errorQueryResponse)

        deferredResult.await()

        verify(mockSudoUserClient).isSignedIn()
        verify(mockAppSyncClient).query(any<GetEntitlementsConsumptionQuery>())
    }

    @Test
    fun `getEntitlementsConsumption() should throw when response has a EntitlementsSetNotFoundError`() = runBlocking<Unit> {
        queryHolder.callback shouldBe null

        val errorQueryResponse by before {
            val error = com.apollographql.apollo.api.Error(
                "mock",
                emptyList(),
                mapOf("errorType" to "sudoplatform.entitlements.EntitlementsSetNotFoundError")
            )
            Response.builder<GetEntitlementsConsumptionQuery.Data>(GetEntitlementsConsumptionQuery())
                .errors(listOf(error))
                .data(null)
                .build()
        }

        val deferredResult = async(Dispatchers.IO) {
            shouldThrow<SudoEntitlementsClient.EntitlementsException.EntitlementsSetNotFoundException> {
                client.getEntitlementsConsumption()
            }
        }
        deferredResult.start()
        delay(100L)

        queryHolder.callback shouldNotBe null
        queryHolder.callback?.onResponse(errorQueryResponse)

        deferredResult.await()

        verify(mockSudoUserClient).isSignedIn()
        verify(mockAppSyncClient).query(any<GetEntitlementsConsumptionQuery>())
    }

    @Test
    fun `getEntitlementsConsumption() should throw when response has a EntitlementsSequenceNotFoundError`() = runBlocking<Unit> {
        queryHolder.callback shouldBe null

        val errorQueryResponse by before {
            val error = com.apollographql.apollo.api.Error(
                "mock",
                emptyList(),
                mapOf("errorType" to "sudoplatform.entitlements.EntitlementsSequenceNotFoundError")
            )
            Response.builder<GetEntitlementsConsumptionQuery.Data>(GetEntitlementsConsumptionQuery())
                .errors(listOf(error))
                .data(null)
                .build()
        }

        val deferredResult = async(Dispatchers.IO) {
            shouldThrow<SudoEntitlementsClient.EntitlementsException.EntitlementsSequenceNotFoundException> {
                client.getEntitlementsConsumption()
            }
        }
        deferredResult.start()
        delay(100L)

        queryHolder.callback shouldNotBe null
        queryHolder.callback?.onResponse(errorQueryResponse)

        deferredResult.await()

        verify(mockSudoUserClient).isSignedIn()
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

        verify(mockSudoUserClient).isSignedIn()
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

        verify(mockSudoUserClient).isSignedIn()
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

        verify(mockSudoUserClient).isSignedIn()
        verify(mockAppSyncClient).query(any<GetEntitlementsConsumptionQuery>())
    }

    @Test
    fun `getEntitlementsConsumption() should find error when unauthorized error occurs`() = runBlocking<Unit> {
        queryHolder.callback shouldBe null

        val exceptionToThrow = RuntimeException(ApolloException("", IOException(NotAuthorizedException(""))))
        mockAppSyncClient.stub {
            on { query(any<GetEntitlementsConsumptionQuery>()) } doThrow exceptionToThrow
        }

        val deferredResult = async(Dispatchers.IO) {
            shouldThrow<SudoEntitlementsClient.EntitlementsException.AuthenticationException> {
                client.getEntitlementsConsumption()
            }
        }
        deferredResult.start()
        delay(100L)

        deferredResult.await()

        verify(mockSudoUserClient).isSignedIn()
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

        verify(mockSudoUserClient).isSignedIn()
        verify(mockAppSyncClient).query(any<GetEntitlementsConsumptionQuery>())
    }
}
