/*
 * Copyright © 2020 Anonyome Labs, Inc. All rights reserved.
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
import com.sudoplatform.sudoentitlements.graphql.GetExternalIdQuery
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
 * Test the correct operation of [SudoEntitlementsClient.getExternalId] using mocks and spies.
 *
 * @since 2021-07-28
 */
class SudoEntitlementsGetExternalIdTest : BaseTests() {

    private val queryResult = "external-id"

    private val queryResponse by before {
        Response.builder<GetExternalIdQuery.Data>(GetExternalIdQuery())
            .data(GetExternalIdQuery.Data(queryResult))
            .build()
    }

    private val queryHolder = CallbackHolder<GetExternalIdQuery.Data>()

    private val mockContext by before {
        mock<Context>()
    }

    private val mockSudoUserClient by before {
        mock<SudoUserClient>()
    }

    private val mockAppSyncClient by before {
        mock<AWSAppSyncClient>().stub {
            on { query(any<GetExternalIdQuery>()) } doReturn queryHolder.queryOperation
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
    fun `getExternalId() should return results when no error present`() = runBlocking<Unit> {

        queryHolder.callback shouldBe null

        val deferredResult = async(Dispatchers.IO) {
            client.getExternalId()
        }
        deferredResult.start()

        delay(100L)
        queryHolder.callback shouldNotBe null
        queryHolder.callback?.onResponse(queryResponse)

        val result = deferredResult.await()
        result shouldBe queryResult

        verify(mockSudoUserClient).isSignedIn()
        verify(mockAppSyncClient).query(any<GetExternalIdQuery>())
    }

    @Test
    fun `getExternalId() should throw if not signed in`() = runBlocking<Unit> {
        whenever(mockSudoUserClient.isSignedIn()).thenReturn(false)

        queryHolder.callback shouldBe null

        val deferredResult = async(Dispatchers.IO) {
            shouldThrow<SudoEntitlementsClient.EntitlementsException.NotSignedInException> {
                client.getExternalId()
            }
        }
        deferredResult.start()

        delay(100L)
        queryHolder.callback shouldBe null

        verify(mockSudoUserClient).isSignedIn()
        verify(mockAppSyncClient, never()).query(any<GetExternalIdQuery>())
    }

    @Test
    fun `getExternalId() should throw when query response is null`() = runBlocking<Unit> {

        queryHolder.callback shouldBe null

        val nullResponse by before {
            Response.builder<GetExternalIdQuery.Data>(GetExternalIdQuery())
                .data(null)
                .build()
        }

        val deferredResult = async(Dispatchers.IO) {
            shouldThrow<SudoEntitlementsClient.EntitlementsException.FailedException> {
                client.getExternalId()
            }
        }
        deferredResult.start()

        delay(100L)
        queryHolder.callback shouldNotBe null
        queryHolder.callback?.onResponse(nullResponse)

        verify(mockSudoUserClient).isSignedIn()
        verify(mockAppSyncClient).query(any<GetExternalIdQuery>())
    }

    @Test
    fun `getExternalId() should throw when response has an invalid token error`() = runBlocking<Unit> {

        queryHolder.callback shouldBe null

        val errorQueryResponse by before {
            val error = com.apollographql.apollo.api.Error(
                "mock",
                emptyList(),
                mapOf("errorType" to "sudoplatform.InvalidTokenError")
            )
            Response.builder<GetExternalIdQuery.Data>(GetExternalIdQuery())
                .errors(listOf(error))
                .data(null)
                .build()
        }

        val deferredResult = async(Dispatchers.IO) {
            shouldThrow<SudoEntitlementsClient.EntitlementsException.InvalidTokenException> {
                client.getExternalId()
            }
        }
        deferredResult.start()
        delay(100L)

        queryHolder.callback shouldNotBe null
        queryHolder.callback?.onResponse(errorQueryResponse)

        deferredResult.await()

        verify(mockSudoUserClient).isSignedIn()
        verify(mockAppSyncClient).query(any<GetExternalIdQuery>())
    }

    @Test
    fun `getExternalId() should throw when response has error`() = runBlocking<Unit> {

        queryHolder.callback shouldBe null

        val error = com.apollographql.apollo.api.Error(
            "mock",
            emptyList(),
            mapOf("errorType" to "DilithiumCrystalsOutOfAlignment")
        )

        val responseWithNullData by before {
            Response.builder<GetExternalIdQuery.Data>(GetExternalIdQuery())
                .errors(listOf(error))
                .build()
        }

        val deferredResult = async(Dispatchers.IO) {
            shouldThrow<SudoEntitlementsClient.EntitlementsException.FailedException> {
                client.getExternalId()
            }
        }
        deferredResult.start()

        delay(100L)
        queryHolder.callback shouldNotBe null
        queryHolder.callback?.onResponse(responseWithNullData)

        deferredResult.await()

        verify(mockSudoUserClient).isSignedIn()
        verify(mockAppSyncClient).query(any<GetExternalIdQuery>())
    }

    @Test
    fun `getExternalId() should throw when http error occurs`() = runBlocking<Unit> {

        queryHolder.callback shouldBe null

        val deferredResult = async(Dispatchers.IO) {
            shouldThrow<SudoEntitlementsClient.EntitlementsException.FailedException> {
                client.getExternalId()
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
        verify(mockAppSyncClient).query(any<GetExternalIdQuery>())
    }

    @Test
    fun `getExternalId() should throw when unknown error occurs`() = runBlocking<Unit> {

        queryHolder.callback shouldBe null

        mockAppSyncClient.stub {
            on { query(any<GetExternalIdQuery>()) } doThrow RuntimeException("Mock Runtime Exception")
        }

        val deferredResult = async(Dispatchers.IO) {
            shouldThrow<SudoEntitlementsClient.EntitlementsException.UnknownException> {
                client.getExternalId()
            }
        }
        deferredResult.start()

        delay(100L)
        deferredResult.await()

        verify(mockSudoUserClient).isSignedIn()
        verify(mockAppSyncClient).query(any<GetExternalIdQuery>())
    }

    @Test
    fun `getExternalId() should find error when unauthorized error occurs`() = runBlocking<Unit> {

        queryHolder.callback shouldBe null

        val exceptionToThrow = RuntimeException(ApolloException("", IOException(NotAuthorizedException(""))))
        mockAppSyncClient.stub {
            on { query(any<GetExternalIdQuery>()) } doThrow exceptionToThrow
        }

        val deferredResult = async(Dispatchers.IO) {
            shouldThrow<SudoEntitlementsClient.EntitlementsException.AuthenticationException> {
                client.getExternalId()
            }
        }
        deferredResult.start()
        delay(100L)

        deferredResult.await()

        verify(mockSudoUserClient).isSignedIn()
        verify(mockAppSyncClient).query(any<GetExternalIdQuery>())
    }

    @Test
    fun `getExternalId() should not suppress CancellationException`() = runBlocking<Unit> {

        mockAppSyncClient.stub {
            on { query(any<GetExternalIdQuery>()) } doThrow CancellationException("Mock Cancellation Exception")
        }

        shouldThrow<CancellationException> {
            client.getExternalId()
        }

        verify(mockSudoUserClient).isSignedIn()
        verify(mockAppSyncClient).query(any<GetExternalIdQuery>())
    }
}
