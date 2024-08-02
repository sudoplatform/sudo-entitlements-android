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
import com.sudoplatform.sudoentitlements.graphql.GetEntitlementsQuery
import com.sudoplatform.sudouser.SudoUserClient
import com.sudoplatform.sudouser.amplify.GraphQLClient
import io.kotlintest.shouldBe
import io.kotlintest.shouldNotBe
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
 * Test the correct operation of [SudoEntitlementsClient.getEntitlements] using mocks and spies.
 */
class SudoEntitlementsGetEntitlementsTest : BaseTests() {
    private val queryResponse by before {
        JSONObject(
            """
            {
                'getEntitlements': {
                    'createdAtEpochMs': 1.0,
                    'updatedAtEpochMs': 2.0,
                    'version': 1.0,
                    'name': 'name',
                    'description': 'description',
                    'entitlements': [{
                        'name': 'e.name',
                        'description': 'e.description',
                        'value': 42.0
                    }]

                }
            }
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
                    argThat { this.query.equals(GetEntitlementsQuery.OPERATION_DOCUMENT) },
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
    fun `getEntitlements() should return results when no error present`() = runBlocking<Unit> {
        val deferredResult = async(Dispatchers.IO) {
            client.getEntitlements()
        }
        deferredResult.start()

        delay(100L)

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

        verify(mockSudoUserClient).isSignedIn()
        verify(mockApiCategory).query<String>(
            check { assertEquals(it.query, GetEntitlementsQuery.OPERATION_DOCUMENT) },
            any(),
            any(),
        )
    }

    @Test
    fun `getEntitlements() should return empty list output when query result list is empty`() = runBlocking<Unit> {
        val queryResponseWithEmptyList = JSONObject(
            """
            {
                'getEntitlements': {
                    'createdAtEpochMs': 1.0,
                    'updatedAtEpochMs': 2.0,
                    'version': 1.0,
                    'name': 'name',
                    'description': 'description',
                    'entitlements': []

                }
            }
            """.trimIndent(),
        )
        whenever(
            mockApiCategory.query<String>(
                argThat { this.query.equals(GetEntitlementsQuery.OPERATION_DOCUMENT) },
                any(),
                any(),
            ),
        ).thenAnswer {
            @Suppress("UNCHECKED_CAST")
            (it.arguments[1] as Consumer<GraphQLResponse<String>>).accept(
                GraphQLResponse(queryResponseWithEmptyList.toString(), null),
            )
            mock<GraphQLOperation<String>>()
        }
        val deferredResult = async(Dispatchers.IO) {
            client.getEntitlements()
        }
        deferredResult.start()

        delay(100L)

        val optionalResult = deferredResult.await()
        optionalResult shouldNotBe null
        val result = optionalResult!!
        result.entitlements.isEmpty() shouldBe true
        result.entitlements.size shouldBe 0

        verify(mockSudoUserClient).isSignedIn()
        verify(mockApiCategory).query<String>(
            check { assertEquals(it.query, GetEntitlementsQuery.OPERATION_DOCUMENT) },
            any(),
            any(),
        )
    }

    @Test
    fun `getEntitlements() should throw if not signed in`() = runBlocking<Unit> {
        whenever(mockSudoUserClient.isSignedIn()).thenReturn(false)

        val deferredResult = async(Dispatchers.IO) {
            shouldThrow<SudoEntitlementsClient.EntitlementsException.NotSignedInException> {
                client.getEntitlements()
            }
        }
        deferredResult.start()
        delay(100L)

        verify(mockSudoUserClient).isSignedIn()
        verify(mockApiCategory, never()).query<String>(
            check { assertEquals(it.query, GetEntitlementsQuery.OPERATION_DOCUMENT) },
            any(),
            any(),
        )
    }

    @Test
    fun `getEntitlements() should return null when query response data is null`() = runBlocking<Unit> {
        val queryResponseWithEmptyData = JSONObject(
            """
            {
                'getEntitlements': null
            }
            """.trimIndent(),
        )
        whenever(
            mockApiCategory.query<String>(
                argThat { this.query.equals(GetEntitlementsQuery.OPERATION_DOCUMENT) },
                any(),
                any(),
            ),
        ).thenAnswer {
            @Suppress("UNCHECKED_CAST")
            (it.arguments[1] as Consumer<GraphQLResponse<String>>).accept(
                GraphQLResponse(queryResponseWithEmptyData.toString(), null),
            )
            mock<GraphQLOperation<String>>()
        }

        val deferredResult = async(Dispatchers.IO) {
            client.getEntitlements()
        }
        deferredResult.start()

        delay(100L)

        val optionalResult = deferredResult.await()
        optionalResult shouldBe null

        verify(mockSudoUserClient).isSignedIn()
        verify(mockApiCategory).query<String>(
            check { assertEquals(it.query, GetEntitlementsQuery.OPERATION_DOCUMENT) },
            any(),
            any(),
        )
    }

    @Test
    fun `getEntitlements() should return null when query response is null`() = runBlocking<Unit> {
        whenever(
            mockApiCategory.query<String>(
                argThat { this.query.equals(GetEntitlementsQuery.OPERATION_DOCUMENT) },
                any(),
                any(),
            ),
        ).thenAnswer {
            @Suppress("UNCHECKED_CAST")
            (it.arguments[1] as Consumer<GraphQLResponse<String>>).accept(
                GraphQLResponse(null, null),
            )
            mock<GraphQLOperation<String>>()
        }

        val deferredResult = async(Dispatchers.IO) {
            client.getEntitlements()
        }
        deferredResult.start()

        delay(100L)

        val optionalResult = deferredResult.await()
        optionalResult shouldBe null

        verify(mockSudoUserClient).isSignedIn()
        verify(mockApiCategory).query<String>(
            check { assertEquals(it.query, GetEntitlementsQuery.OPERATION_DOCUMENT) },
            any(),
            any(),
        )
    }

    @Test
    fun `getEntitlements() should throw when response has a NoExternalIdError`() = runBlocking<Unit> {
        whenever(
            mockApiCategory.query<String>(
                argThat { this.query.equals(GetEntitlementsQuery.OPERATION_DOCUMENT) },
                any(),
                any(),
            ),
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
                client.getEntitlements()
            }
        }
        deferredResult.start()
        delay(100L)

        deferredResult.await()

        verify(mockSudoUserClient).isSignedIn()
        verify(mockApiCategory).query<String>(
            check { assertEquals(it.query, GetEntitlementsQuery.OPERATION_DOCUMENT) },
            any(),
            any(),
        )
    }

    @Test
    fun `getEntitlements() should throw when response has a NoBillingGroupError`() = runBlocking<Unit> {
        whenever(
            mockApiCategory.query<String>(
                argThat { this.query.equals(GetEntitlementsQuery.OPERATION_DOCUMENT) },
                any(),
                any(),
            ),
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
                client.getEntitlements()
            }
        }
        deferredResult.start()
        delay(100L)

        deferredResult.await()

        verify(mockSudoUserClient).isSignedIn()
        verify(mockApiCategory).query<String>(
            check { assertEquals(it.query, GetEntitlementsQuery.OPERATION_DOCUMENT) },
            any(),
            any(),
        )
    }

    @Test
    fun `getEntitlements() should throw when response has a EntitlementsSetNotFoundError`() = runBlocking<Unit> {
        whenever(
            mockApiCategory.query<String>(
                argThat { this.query.equals(GetEntitlementsQuery.OPERATION_DOCUMENT) },
                any(),
                any(),
            ),
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
                client.getEntitlements()
            }
        }
        deferredResult.start()
        delay(100L)

        deferredResult.await()

        verify(mockSudoUserClient).isSignedIn()
        verify(mockApiCategory).query<String>(
            check { assertEquals(it.query, GetEntitlementsQuery.OPERATION_DOCUMENT) },
            any(),
            any(),
        )
    }

    @Test
    fun `getEntitlements() should throw when response has a EntitlementsSequenceNotFoundError`() = runBlocking<Unit> {
        whenever(
            mockApiCategory.query<String>(
                argThat { this.query.equals(GetEntitlementsQuery.OPERATION_DOCUMENT) },
                any(),
                any(),
            ),
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
                client.getEntitlements()
            }
        }
        deferredResult.start()
        delay(100L)

        deferredResult.await()

        verify(mockSudoUserClient).isSignedIn()
        verify(mockApiCategory).query<String>(
            check { assertEquals(it.query, GetEntitlementsQuery.OPERATION_DOCUMENT) },
            any(),
            any(),
        )
    }

    @Test
    fun `getEntitlements() should throw when response has error`() = runBlocking<Unit> {
        whenever(
            mockApiCategory.query<String>(
                argThat { this.query.equals(GetEntitlementsQuery.OPERATION_DOCUMENT) },
                any(),
                any(),
            ),
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
                client.getEntitlements()
            }
        }
        deferredResult.start()

        delay(100L)

        deferredResult.await()

        verify(mockSudoUserClient).isSignedIn()
        verify(
            mockApiCategory,
        ).query<String>(check { assertEquals(it.query, GetEntitlementsQuery.OPERATION_DOCUMENT) }, any(), any())
    }

    @Test
    fun `getEntitlements() should throw when http error occurs`() = runBlocking<Unit> {
        whenever(
            mockApiCategory.query<String>(
                argThat { this.query.equals(GetEntitlementsQuery.OPERATION_DOCUMENT) },
                any(),
                any(),
            ),
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
                client.getEntitlements()
            }
        }
        deferredResult.start()
        delay(100L)
        deferredResult.await()

        verify(mockSudoUserClient).isSignedIn()
        verify(
            mockApiCategory,
        ).query<String>(check { assertEquals(it.query, GetEntitlementsQuery.OPERATION_DOCUMENT) }, any(), any())
    }

    @Test
    fun `getEntitlements() should throw when unknown error occurs`() = runBlocking<Unit> {
        mockApiCategory.stub {
            on {
                query<String>(
                    argThat { this.query.equals(GetEntitlementsQuery.OPERATION_DOCUMENT) },
                    any(), any(),
                )
            } doThrow RuntimeException("Mock Runtime Exception")
        }

        val deferredResult = async(Dispatchers.IO) {
            shouldThrow<SudoEntitlementsClient.EntitlementsException.UnknownException> {
                client.getEntitlements()
            }
        }
        deferredResult.start()

        delay(100L)
        deferredResult.await()

        verify(mockSudoUserClient).isSignedIn()
        verify(
            mockApiCategory,
        ).query<String>(check { assertEquals(it.query, GetEntitlementsQuery.OPERATION_DOCUMENT) }, any(), any())
    }

    @Test
    fun `getEntitlements() should find error when unauthorized error occurs`() = runBlocking<Unit> {
        whenever(
            mockApiCategory.query<String>(
                argThat { this.query.equals(GetEntitlementsQuery.OPERATION_DOCUMENT) },
                any(),
                any(),
            ),
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
                client.getEntitlements()
            }
        }
        deferredResult.start()
        delay(100L)

        deferredResult.await()

        verify(mockSudoUserClient).isSignedIn()
        verify(
            mockApiCategory,
        ).query<String>(check { assertEquals(it.query, GetEntitlementsQuery.OPERATION_DOCUMENT) }, any(), any())
    }

    @Test
    fun `getEntitlements() should not suppress CancellationException`() = runBlocking<Unit> {
        mockApiCategory.stub {
            on {
                query<String>(
                    argThat { this.query.equals(GetEntitlementsQuery.OPERATION_DOCUMENT) },
                    any(), any(),
                )
            } doThrow CancellationException("Mock Cancellation Exception")
        }

        shouldThrow<CancellationException> {
            client.getEntitlements()
        }

        verify(mockSudoUserClient).isSignedIn()
        verify(mockApiCategory).query<String>(
            check { assertEquals(it.query, GetEntitlementsQuery.OPERATION_DOCUMENT) },
            any(),
            any(),
        )
    }
}
