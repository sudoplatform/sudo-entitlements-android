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
import com.sudoplatform.sudoentitlements.graphql.GetEntitlementsQuery
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
class SudoEntitlementsGetEntitlementsTest : BaseTests() {

    private val queryResult by before {
        GetEntitlementsQuery.GetEntitlements(
            "typename",
            1.0,
            1.0,
            1.0,
            "name",
            "description",
            listOf(
                GetEntitlementsQuery.Entitlement(
                    "typename",
                    "e.name",
                    "e.description",
                    42
                )
            )
        )
    }

    private val queryResponse by before {
        Response.builder<GetEntitlementsQuery.Data>(GetEntitlementsQuery())
            .data(GetEntitlementsQuery.Data(queryResult))
            .build()
    }

    private val queryHolder = CallbackHolder<GetEntitlementsQuery.Data>()

    private val mockContext by before {
        mock<Context>()
    }

    private val mockAppSyncClient by before {
        mock<AWSAppSyncClient>().stub {
            on { query(any<GetEntitlementsQuery>()) } doReturn queryHolder.queryOperation
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
    fun `getEntitlements() should return results when no error present`() = runBlocking<Unit> {

        queryHolder.callback shouldBe null

        val deferredResult = async(Dispatchers.IO) {
            client.getEntitlements()
        }
        deferredResult.start()

        delay(100L)
        queryHolder.callback shouldNotBe null
        queryHolder.callback?.onResponse(queryResponse)

        val optionalResult = deferredResult.await()
        optionalResult shouldNotBe null
        val result = optionalResult!!
        result.name shouldBe "name"
        result.description shouldBe "description"
        result.version shouldBe 1.0
        result.entitlements.isEmpty() shouldBe false
        result.entitlements.isEmpty() shouldBe false
        result.entitlements.size shouldBe 1
        with(result.entitlements.first()) {
            name shouldBe "e.name"
            description shouldBe "e.description"
            value shouldBe 42
        }

        verify(mockAppSyncClient).query(any<GetEntitlementsQuery>())
    }

    @Test
    fun `getEntitlements() should return empty list output when query result data is empty`() = runBlocking<Unit> {

        queryHolder.callback shouldBe null

        val queryResultWithEmptyList by before {
            GetEntitlementsQuery.GetEntitlements(
                "typename",
                1.0,
                1.0,
                1.0,
                "name",
                "description",
                emptyList()
            )
        }

        val responseWithEmptyList by before {
            Response.builder<GetEntitlementsQuery.Data>(GetEntitlementsQuery())
                .data(GetEntitlementsQuery.Data(queryResultWithEmptyList))
                .build()
        }

        val deferredResult = async(Dispatchers.IO) {
            client.getEntitlements()
        }
        deferredResult.start()

        delay(100L)
        queryHolder.callback shouldNotBe null
        queryHolder.callback?.onResponse(responseWithEmptyList)

        val optionalResult = deferredResult.await()
        optionalResult shouldNotBe null
        val result = optionalResult!!
        result.entitlements.isEmpty() shouldBe true
        result.entitlements.size shouldBe 0

        verify(mockAppSyncClient).query(any<GetEntitlementsQuery>())
    }

    @Test
    fun `getEntitlements() should return null when query response data is null`() = runBlocking<Unit> {

        queryHolder.callback shouldBe null

        val responseWithNullResult by before {
            Response.builder<GetEntitlementsQuery.Data>(GetEntitlementsQuery())
                .data(GetEntitlementsQuery.Data(null))
                .build()
        }

        val deferredResult = async(Dispatchers.IO) {
            client.getEntitlements()
        }
        deferredResult.start()

        delay(100L)
        queryHolder.callback shouldNotBe null
        queryHolder.callback?.onResponse(responseWithNullResult)

        val optionalResult = deferredResult.await()
        optionalResult shouldBe null

        verify(mockAppSyncClient).query(any<GetEntitlementsQuery>())
    }

    @Test
    fun `getEntitlements() should return null when query response is null`() = runBlocking<Unit> {

        queryHolder.callback shouldBe null

        val nullResponse by before {
            Response.builder<GetEntitlementsQuery.Data>(GetEntitlementsQuery())
                .data(null)
                .build()
        }

        val deferredResult = async(Dispatchers.IO) {
            client.getEntitlements()
        }
        deferredResult.start()

        delay(100L)
        queryHolder.callback shouldNotBe null
        queryHolder.callback?.onResponse(nullResponse)

        val optionalResult = deferredResult.await()
        optionalResult shouldBe null

        verify(mockAppSyncClient).query(any<GetEntitlementsQuery>())
    }

    @Test
    fun `getEntitlements() should throw when response has an invalid token error`() = runBlocking<Unit> {

        queryHolder.callback shouldBe null

        val errorQueryResponse by before {
            val error = com.apollographql.apollo.api.Error(
                "mock",
                emptyList(),
                mapOf("errorType" to "sudoplatform.InvalidTokenError")
            )
            Response.builder<GetEntitlementsQuery.Data>(GetEntitlementsQuery())
                .errors(listOf(error))
                .data(null)
                .build()
        }

        val deferredResult = async(Dispatchers.IO) {
            shouldThrow<SudoEntitlementsClient.EntitlementsException.InvalidTokenException> {
                client.getEntitlements()
            }
        }
        deferredResult.start()
        delay(100L)

        queryHolder.callback shouldNotBe null
        queryHolder.callback?.onResponse(errorQueryResponse)

        deferredResult.await()

        verify(mockAppSyncClient).query(any<GetEntitlementsQuery>())
    }

    @Test
    fun `getEntitlements() should throw when response has error`() = runBlocking<Unit> {

        queryHolder.callback shouldBe null

        val error = com.apollographql.apollo.api.Error(
            "mock",
            emptyList(),
            mapOf("errorType" to "DilithiumCrystalsOutOfAlignment")
        )

        val responseWithNullData by before {
            Response.builder<GetEntitlementsQuery.Data>(GetEntitlementsQuery())
                .errors(listOf(error))
                .build()
        }

        val deferredResult = async(Dispatchers.IO) {
            shouldThrow<SudoEntitlementsClient.EntitlementsException.FailedException> {
                client.getEntitlements()
            }
        }
        deferredResult.start()

        delay(100L)
        queryHolder.callback shouldNotBe null
        queryHolder.callback?.onResponse(responseWithNullData)

        deferredResult.await()

        verify(mockAppSyncClient).query(any<GetEntitlementsQuery>())
    }

    @Test
    fun `getEntitlements() should throw when http error occurs`() = runBlocking<Unit> {

        queryHolder.callback shouldBe null

        val deferredResult = async(Dispatchers.IO) {
            shouldThrow<SudoEntitlementsClient.EntitlementsException.FailedException> {
                client.getEntitlements()
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

        verify(mockAppSyncClient).query(any<GetEntitlementsQuery>())
    }

    @Test
    fun `getEntitlements() should throw when unknown error occurs`() = runBlocking<Unit> {

        queryHolder.callback shouldBe null

        mockAppSyncClient.stub {
            on { query(any<GetEntitlementsQuery>()) } doThrow RuntimeException("Mock Runtime Exception")
        }

        val deferredResult = async(Dispatchers.IO) {
            shouldThrow<SudoEntitlementsClient.EntitlementsException.UnknownException> {
                client.getEntitlements()
            }
        }
        deferredResult.start()

        delay(100L)
        deferredResult.await()

        verify(mockAppSyncClient).query(any<GetEntitlementsQuery>())
    }

    @Test
    fun `getEntitlements() should not suppress CancellationException`() = runBlocking<Unit> {

        mockAppSyncClient.stub {
            on { query(any<GetEntitlementsQuery>()) } doThrow CancellationException("Mock Cancellation Exception")
        }

        shouldThrow<CancellationException> {
            client.getEntitlements()
        }

        verify(mockAppSyncClient).query(any<GetEntitlementsQuery>())
    }
}
