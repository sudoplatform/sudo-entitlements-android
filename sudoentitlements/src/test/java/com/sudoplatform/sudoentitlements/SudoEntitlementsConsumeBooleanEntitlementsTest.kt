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
import com.sudoplatform.sudoentitlements.graphql.ConsumeBooleanEntitlementsMutation
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
 * Test the correct operation of [SudoEntitlementsClient.consumeBooleanEntitlements] using mocks and spies.
 */
class SudoEntitlementsConsumeBooleanEntitlementsTest : BaseTests() {

    private val mockContext by before {
        mock<Context>()
    }

    private val mockSudoUserClient by before {
        mock<SudoUserClient>()
    }

    private val mockApiCategory by before {
        mock<ApiCategory>().stub {
            on {
                mutate<String>(
                    argThat { this.query.equals(ConsumeBooleanEntitlementsMutation.OPERATION_DOCUMENT) },
                    any(),
                    any(),
                )
            } doAnswer {
                val graphqlResponse = JSONObject(
                    """
                    {'consumeBooleanEntitlements': true}
                    """.trimIndent(),
                )
                @Suppress("UNCHECKED_CAST")
                (it.arguments[1] as Consumer<GraphQLResponse<String>>).accept(
                    GraphQLResponse(graphqlResponse.toString(), null),
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
    fun `consumeBooleanEntitlements() should return results when no error present`() = runBlocking<Unit> {
        val deferredResult = async(Dispatchers.IO) {
            client.consumeBooleanEntitlements(arrayOf("some-entitlement"))
        }
        deferredResult.start()

        delay(100L)

        val result = deferredResult.await()
        result shouldBe Unit

        verify(mockSudoUserClient).isSignedIn()
        verify(mockApiCategory).mutate<String>(
            check { assertEquals(it.query, ConsumeBooleanEntitlementsMutation.OPERATION_DOCUMENT) },
            any(),
            any(),
        )
    }

    @Test
    fun `consumeBooleanEntitlements() should throw if not signed in`() = runBlocking<Unit> {
        whenever(mockSudoUserClient.isSignedIn()).thenReturn(false)

        val deferredResult = async(Dispatchers.IO) {
            shouldThrow<SudoEntitlementsClient.EntitlementsException.NotSignedInException> {
                client.consumeBooleanEntitlements(arrayOf("some-entitlement"))
            }
        }
        deferredResult.start()

        delay(100L)

        verify(mockSudoUserClient).isSignedIn()
        verify(mockApiCategory, never()).mutate<String>(
            check { assertEquals(it.query, ConsumeBooleanEntitlementsMutation.OPERATION_DOCUMENT) },
            any(),
            any(),
        )
    }

    @Test
    fun `consumeBooleanEntitlements() should throw when response has an invalid argument error`() = runBlocking<Unit> {
        whenever(
            mockApiCategory.mutate<String>(
                argThat { this.query.equals(ConsumeBooleanEntitlementsMutation.OPERATION_DOCUMENT) },
                any(),
                any(),
            ),
        ).thenAnswer {
            val error = GraphQLResponse.Error(
                "mock",
                emptyList(),
                emptyList(),
                mapOf("errorType" to "sudoplatform.InvalidArgumentError"),
            )
            @Suppress("UNCHECKED_CAST")
            (it.arguments[1] as Consumer<GraphQLResponse<String>>).accept(
                GraphQLResponse(null, listOf(error)),
            )
            mock<GraphQLOperation<String>>()
        }

        val deferredResult = async(Dispatchers.IO) {
            shouldThrow<SudoEntitlementsClient.EntitlementsException.InvalidArgumentException> {
                client.consumeBooleanEntitlements(arrayOf("some-entitlement"))
            }
        }
        deferredResult.start()
        delay(100L)

        deferredResult.await()

        verify(mockSudoUserClient).isSignedIn()
        verify(mockApiCategory).mutate<String>(
            check { assertEquals(it.query, ConsumeBooleanEntitlementsMutation.OPERATION_DOCUMENT) },
            any(),
            any(),
        )
    }

    @Test
    fun `consumeBooleanEntitlements() should throw when response has an insufficient entitlements error`() = runBlocking<Unit> {
        whenever(
            mockApiCategory.mutate<String>(
                argThat { this.query.equals(ConsumeBooleanEntitlementsMutation.OPERATION_DOCUMENT) },
                any(),
                any(),
            ),
        ).thenAnswer {
            val error = GraphQLResponse.Error(
                "mock",
                emptyList(),
                emptyList(),
                mapOf("errorType" to "sudoplatform.InsufficientEntitlementsError"),
            )
            @Suppress("UNCHECKED_CAST")
            (it.arguments[1] as Consumer<GraphQLResponse<String>>).accept(
                GraphQLResponse(null, listOf(error)),
            )
            mock<GraphQLOperation<String>>()
        }

        val deferredResult = async(Dispatchers.IO) {
            shouldThrow<SudoEntitlementsClient.EntitlementsException.InsufficientEntitlementsException> {
                client.consumeBooleanEntitlements(arrayOf("some-entitlement"))
            }
        }
        deferredResult.start()
        delay(100L)
        deferredResult.await()

        verify(mockSudoUserClient).isSignedIn()
        verify(mockApiCategory).mutate<String>(
            check { assertEquals(it.query, ConsumeBooleanEntitlementsMutation.OPERATION_DOCUMENT) },
            any(),
            any(),
        )
    }

    @Test
    fun `consumeBooleanEntitlements() should throw when http error occurs`() = runBlocking<Unit> {
        whenever(
            mockApiCategory.mutate<String>(
                argThat { this.query.equals(ConsumeBooleanEntitlementsMutation.OPERATION_DOCUMENT) },
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
                client.consumeBooleanEntitlements(arrayOf("some-entitlement"))
            }
        }
        deferredResult.start()
        delay(100L)
        deferredResult.await()

        verify(mockSudoUserClient).isSignedIn()
        verify(mockApiCategory).mutate<String>(
            check { assertEquals(it.query, ConsumeBooleanEntitlementsMutation.OPERATION_DOCUMENT) },
            any(),
            any(),
        )
    }

    @Test
    fun `consumeBooleanEntitlements() should throw when unknown error occurs`() = runBlocking<Unit> {
        mockApiCategory.stub {
            on {
                mutate<String>(
                    argThat { this.query.equals(ConsumeBooleanEntitlementsMutation.OPERATION_DOCUMENT) },
                    any(), any(),
                )
            } doThrow RuntimeException("Mock Runtime Exception")
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
        verify(mockApiCategory).mutate<String>(
            check { assertEquals(it.query, ConsumeBooleanEntitlementsMutation.OPERATION_DOCUMENT) },
            any(),
            any(),
        )
    }

    @Test
    fun `consumeBooleanEntitlements() should find error when unauthorized error occurs`() = runBlocking<Unit> {
        whenever(
            mockApiCategory.mutate<String>(
                argThat { this.query.equals(ConsumeBooleanEntitlementsMutation.OPERATION_DOCUMENT) },
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
                client.consumeBooleanEntitlements(arrayOf("some-entitlement"))
            }
        }
        deferredResult.start()
        delay(200L)

        deferredResult.await()

        verify(mockSudoUserClient).isSignedIn()
        verify(mockApiCategory).mutate<String>(
            check { assertEquals(it.query, ConsumeBooleanEntitlementsMutation.OPERATION_DOCUMENT) },
            any(),
            any(),
        )
    }

    @Test
    fun `consumeBooleanEntitlements() should not suppress CancellationException`() = runBlocking<Unit> {
        mockApiCategory.stub {
            on {
                mutate<String>(
                    argThat { this.query.equals(ConsumeBooleanEntitlementsMutation.OPERATION_DOCUMENT) },
                    any(), any(),
                )
            } doThrow CancellationException("Mock Cancellation Exception")
        }

        shouldThrow<CancellationException> {
            client.consumeBooleanEntitlements(arrayOf("some-entitlement"))
        }

        verify(mockSudoUserClient).isSignedIn()
        verify(mockApiCategory).mutate<String>(
            check { assertEquals(it.query, ConsumeBooleanEntitlementsMutation.OPERATION_DOCUMENT) },
            any(),
            any(),
        )
    }
}
