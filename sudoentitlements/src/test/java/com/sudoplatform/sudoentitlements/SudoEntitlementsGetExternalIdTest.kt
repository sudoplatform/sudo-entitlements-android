/*
 * Copyright © 2024 Anonyome Labs, Inc. All rights reserved.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.sudoplatform.sudoentitlements

import android.content.Context
import com.amplifyframework.api.ApiCategory
import com.amplifyframework.api.graphql.GraphQLOperation
import com.amplifyframework.api.graphql.GraphQLResponse
import com.amplifyframework.core.Consumer
import com.sudoplatform.sudoentitlements.graphql.GetExternalIdQuery
import com.sudoplatform.sudouser.SudoUserClient
import com.sudoplatform.sudouser.amplify.GraphQLClient
import io.kotlintest.shouldBe
import io.kotlintest.shouldThrow
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.argThat
import org.mockito.kotlin.check
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.doThrow
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.stub
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoMoreInteractions
import org.mockito.kotlin.whenever
import java.net.HttpURLConnection
import java.util.concurrent.CancellationException

/**
 * Test the correct operation of [SudoEntitlementsClient.getExternalId] using mocks and spies.
 */
class SudoEntitlementsGetExternalIdTest : BaseTests() {

    private val queryResult = "external-id"

    // must match queryResult value...
    private val queryResponse by before {
        JSONObject(
            """
            {'getExternalId': 'external-id'}
            """.trimIndent(),
        )
    }

    private val mockContext by before {
        mock<Context>()
    }

    private val mockSudoUserClient by before {
        mock<SudoUserClient>()
    }

    private val mockApiCategory by before {
        mock<ApiCategory>().stub {
            on {
                query<String>(
                    argThat { this.query.equals(GetExternalIdQuery.OPERATION_DOCUMENT) },
                    any(), any(),
                )
            } doAnswer {
                @Suppress("UNCHECKED_CAST")
                (it.arguments[1] as Consumer<GraphQLResponse<String>>).accept(
                    GraphQLResponse(queryResponse.toString(), null),
                )
                mock<GraphQLOperation<String>>()
            }
        }
    }

    private val client by before {
        DefaultSudoEntitlementsClient(
            mockContext,
            mockSudoUserClient,
            GraphQLClient(mockApiCategory),
            mockLogger,
        )
    }

    @Before
    fun init() {
        whenever(mockSudoUserClient.isSignedIn()).thenReturn(true)
    }

    @After
    fun fini() {
        verifyNoMoreInteractions(mockContext, mockSudoUserClient, mockApiCategory)
    }

    @Test
    fun `getExternalId() should return results when no error present`() = runBlocking<Unit> {
        val deferredResult = async(Dispatchers.IO) {
            client.getExternalId()
        }
        deferredResult.start()

        delay(100L)

        val result = deferredResult.await()
        result shouldBe queryResult

        verify(mockSudoUserClient).isSignedIn()
        verify(mockApiCategory).query<String>(check { assertEquals(it.query, GetExternalIdQuery.OPERATION_DOCUMENT) }, any(), any())
    }

    @Test
    fun `getExternalId() should throw if not signed in`() = runBlocking<Unit> {
        whenever(mockSudoUserClient.isSignedIn()).thenReturn(false)
        val deferredResult = async(Dispatchers.IO) {
            shouldThrow<SudoEntitlementsClient.EntitlementsException.NotSignedInException> {
                client.getExternalId()
            }
        }
        deferredResult.start()

        delay(100L)

        verify(mockSudoUserClient).isSignedIn()
        verify(
            mockApiCategory,
            never(),
        ).query<String>(check { assertEquals(it.query, GetExternalIdQuery.OPERATION_DOCUMENT) }, any(), any())
    }

    @Test
    fun `getExternalId() should throw when query response is null`() = runBlocking<Unit> {
        whenever(
            mockApiCategory.query<String>(argThat { this.query.equals(GetExternalIdQuery.OPERATION_DOCUMENT) }, any(), any()),
        ).thenAnswer {
            @Suppress("UNCHECKED_CAST")
            (it.arguments[1] as Consumer<GraphQLResponse<String>>).accept(
                GraphQLResponse(null, null),
            )
            mock<GraphQLOperation<String>>()
        }

        val deferredResult = async(Dispatchers.IO) {
            shouldThrow<SudoEntitlementsClient.EntitlementsException.FailedException> {
                client.getExternalId()
            }
        }
        deferredResult.start()

        delay(100L)

        verify(mockSudoUserClient).isSignedIn()
        verify(mockApiCategory).query<String>(check { assertEquals(it.query, GetExternalIdQuery.OPERATION_DOCUMENT) }, any(), any())
    }

    @Test
    fun `getExternalId() should throw when response has a NoExternalIdError`() = runBlocking<Unit> {
        whenever(
            mockApiCategory.query<String>(argThat { this.query.equals(GetExternalIdQuery.OPERATION_DOCUMENT) }, any(), any()),
        ).thenAnswer {
            val error = GraphQLResponse.Error(
                "mock",
                emptyList(),
                emptyList(),
                mapOf("errorType" to "sudoplatform.entitlements.NoExternalIdError"),
            )
            @Suppress("UNCHECKED_CAST")
            (it.arguments[1] as Consumer<GraphQLResponse<String>>).accept(
                GraphQLResponse(null, listOf(error)),
            )
            mock<GraphQLOperation<String>>()
        }

        val deferredResult = async(Dispatchers.IO) {
            shouldThrow<SudoEntitlementsClient.EntitlementsException.NoExternalIdException> {
                client.getExternalId()
            }
        }
        deferredResult.start()
        delay(100L)
        deferredResult.await()

        verify(mockSudoUserClient).isSignedIn()
        verify(mockApiCategory).query<String>(check { assertEquals(it.query, GetExternalIdQuery.OPERATION_DOCUMENT) }, any(), any())
    }

    @Test
    fun `getExternalId() should throw when response has a NoBillingGroupError`() = runBlocking<Unit> {
        whenever(
            mockApiCategory.query<String>(argThat { this.query.equals(GetExternalIdQuery.OPERATION_DOCUMENT) }, any(), any()),
        ).thenAnswer {
            val error = GraphQLResponse.Error(
                "mock",
                emptyList(),
                emptyList(),
                mapOf("errorType" to "sudoplatform.entitlements.NoBillingGroupError"),
            )
            @Suppress("UNCHECKED_CAST")
            (it.arguments[1] as Consumer<GraphQLResponse<String>>).accept(
                GraphQLResponse(null, listOf(error)),
            )
            mock<GraphQLOperation<String>>()
        }

        val deferredResult = async(Dispatchers.IO) {
            shouldThrow<SudoEntitlementsClient.EntitlementsException.NoBillingGroupException> {
                client.getExternalId()
            }
        }
        deferredResult.start()
        delay(100L)

        deferredResult.await()

        verify(mockSudoUserClient).isSignedIn()
        verify(mockApiCategory).query<String>(check { assertEquals(it.query, GetExternalIdQuery.OPERATION_DOCUMENT) }, any(), any())
    }

    @Test
    fun `getExternalId() should throw when response has a EntitlementsSetNotFoundError`() = runBlocking<Unit> {
        whenever(
            mockApiCategory.query<String>(argThat { this.query.equals(GetExternalIdQuery.OPERATION_DOCUMENT) }, any(), any()),
        ).thenAnswer {
            val error = GraphQLResponse.Error(
                "mock",
                emptyList(),
                emptyList(),
                mapOf("errorType" to "sudoplatform.entitlements.EntitlementsSetNotFoundError"),
            )
            @Suppress("UNCHECKED_CAST")
            (it.arguments[1] as Consumer<GraphQLResponse<String>>).accept(
                GraphQLResponse(null, listOf(error)),
            )
            mock<GraphQLOperation<String>>()
        }

        val deferredResult = async(Dispatchers.IO) {
            shouldThrow<SudoEntitlementsClient.EntitlementsException.EntitlementsSetNotFoundException> {
                client.getExternalId()
            }
        }
        deferredResult.start()
        delay(100L)

        deferredResult.await()

        verify(mockSudoUserClient).isSignedIn()
        verify(mockApiCategory).query<String>(check { assertEquals(it.query, GetExternalIdQuery.OPERATION_DOCUMENT) }, any(), any())
    }

    @Test
    fun `getExternalId() should throw when response has a EntitlementsSequenceNotFoundError`() = runBlocking<Unit> {
        whenever(
            mockApiCategory.query<String>(argThat { this.query.equals(GetExternalIdQuery.OPERATION_DOCUMENT) }, any(), any()),
        ).thenAnswer {
            val error = GraphQLResponse.Error(
                "mock",
                emptyList(),
                emptyList(),
                mapOf("errorType" to "sudoplatform.entitlements.EntitlementsSequenceNotFoundError"),
            )
            @Suppress("UNCHECKED_CAST")
            (it.arguments[1] as Consumer<GraphQLResponse<String>>).accept(
                GraphQLResponse(null, listOf(error)),
            )
            mock<GraphQLOperation<String>>()
        }

        val deferredResult = async(Dispatchers.IO) {
            shouldThrow<SudoEntitlementsClient.EntitlementsException.EntitlementsSequenceNotFoundException> {
                client.getExternalId()
            }
        }
        deferredResult.start()
        delay(100L)

        deferredResult.await()

        verify(mockSudoUserClient).isSignedIn()
        verify(mockApiCategory).query<String>(check { assertEquals(it.query, GetExternalIdQuery.OPERATION_DOCUMENT) }, any(), any())
    }

    @Test
    fun `getExternalId() should throw when response has error`() = runBlocking<Unit> {
        whenever(
            mockApiCategory.query<String>(argThat { this.query.equals(GetExternalIdQuery.OPERATION_DOCUMENT) }, any(), any()),
        ).thenAnswer {
            val error = GraphQLResponse.Error(
                "mock",
                emptyList(),
                emptyList(),
                mapOf("errorType" to "DilithiumCrystalsOutOfAlignment"),
            )
            @Suppress("UNCHECKED_CAST")
            (it.arguments[1] as Consumer<GraphQLResponse<String>>).accept(
                GraphQLResponse(null, listOf(error)),
            )
            mock<GraphQLOperation<String>>()
        }

        val deferredResult = async(Dispatchers.IO) {
            shouldThrow<SudoEntitlementsClient.EntitlementsException.FailedException> {
                client.getExternalId()
            }
        }
        deferredResult.start()

        delay(100L)

        deferredResult.await()

        verify(mockSudoUserClient).isSignedIn()
        verify(mockApiCategory).query<String>(check { assertEquals(it.query, GetExternalIdQuery.OPERATION_DOCUMENT) }, any(), any())
    }

    @Test
    fun `getExternalId() should throw when http error occurs`() = runBlocking<Unit> {
        whenever(
            mockApiCategory.query<String>(argThat { this.query.equals(GetExternalIdQuery.OPERATION_DOCUMENT) }, any(), any()),
        ).thenAnswer {
            val error = GraphQLResponse.Error(
                "mock",
                emptyList(),
                emptyList(),
                mapOf("httpStatus" to HttpURLConnection.HTTP_FORBIDDEN),
            )
            @Suppress("UNCHECKED_CAST")
            (it.arguments[1] as Consumer<GraphQLResponse<String>>).accept(
                GraphQLResponse(null, listOf(error)),
            )
            mock<GraphQLOperation<String>>()
        }
        val deferredResult = async(Dispatchers.IO) {
            shouldThrow<SudoEntitlementsClient.EntitlementsException.FailedException> {
                client.getExternalId()
            }
        }
        deferredResult.start()
        delay(100L)
        deferredResult.await()

        verify(mockSudoUserClient).isSignedIn()
        verify(mockApiCategory).query<String>(check { assertEquals(it.query, GetExternalIdQuery.OPERATION_DOCUMENT) }, any(), any())
    }

    @Test
    fun `getExternalId() should throw when unknown error occurs`() = runBlocking<Unit> {
        mockApiCategory.stub {
            on {
                query<String>(
                    argThat {
                        this.query.equals(GetExternalIdQuery.OPERATION_DOCUMENT)
                    },
                    any(), any(),
                )
            } doThrow RuntimeException("Mock Runtime Exception")
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
        verify(mockApiCategory).query<String>(check { assertEquals(it.query, GetExternalIdQuery.OPERATION_DOCUMENT) }, any(), any())
    }

    @Test
    fun `getExternalId() should find error when unauthorized error occurs`() = runBlocking<Unit> {
        whenever(
            mockApiCategory.query<String>(argThat { this.query.equals(GetExternalIdQuery.OPERATION_DOCUMENT) }, any(), any()),
        ).thenAnswer {
            val error = GraphQLResponse.Error(
                "mock",
                emptyList(),
                emptyList(),
                mapOf("httpStatus" to HttpURLConnection.HTTP_UNAUTHORIZED),
            )
            @Suppress("UNCHECKED_CAST")
            (it.arguments[1] as Consumer<GraphQLResponse<String>>).accept(
                GraphQLResponse(null, listOf(error)),
            )
            mock<GraphQLOperation<String>>()
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
        verify(mockApiCategory).query<String>(check { assertEquals(it.query, GetExternalIdQuery.OPERATION_DOCUMENT) }, any(), any())
    }

    @Test
    fun `getExternalId() should not suppress CancellationException`() = runBlocking<Unit> {
        mockApiCategory.stub {
            on {
                query<String>(
                    argThat {
                        this.query.equals(GetExternalIdQuery.OPERATION_DOCUMENT)
                    },
                    any(), any(),
                )
            } doThrow CancellationException("Mock Cancellation Exception")
        }

        shouldThrow<CancellationException> {
            client.getExternalId()
        }

        verify(mockSudoUserClient).isSignedIn()
        verify(mockApiCategory).query<String>(
            check { assertEquals(it.query, GetExternalIdQuery.OPERATION_DOCUMENT) },
            any(),
            any(),
        )
    }
}
