/*
 * Copyright © 2020 Anonyome Labs, Inc. All rights reserved.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.sudoplatform.sudoentitlements

import com.amazonaws.services.cognitoidentity.model.NotAuthorizedException
import com.apollographql.apollo.exception.ApolloException
import com.sudoplatform.sudouser.exceptions.AuthenticationException
import io.kotlintest.matchers.beInstanceOf
import io.kotlintest.matchers.types.shouldBeSameInstanceAs
import io.kotlintest.should
import org.junit.Test
import java.io.IOException
import java.lang.IllegalArgumentException
import java.util.concurrent.CancellationException

/**
 * Test the correct operation of [SudoEntitlementsClient.getEntitlements] using mocks and spies.
 *
 * @since 2020-08-26
 */
class RecognizeErrorTest {

    private val cancellationException = CancellationException()
    private val noEntitlementsException = SudoEntitlementsClient.EntitlementsException.NoEntitlementsException()
    private val sudoUserNotAuthorizedException = AuthenticationException.NotAuthorizedException()
    private val awsNotAuthorizedException = NotAuthorizedException("AWS.NotAuthorizedException")

    @Test
    fun `recognizeError() should rethrow a sudo entitlements package exception`() {
        val recognized = recognizeError(noEntitlementsException)
        recognized shouldBeSameInstanceAs noEntitlementsException
    }

    @Test
    fun `recognizeError() should rethrow another sudo SDK package exception`() {
        val recognized = recognizeError(sudoUserNotAuthorizedException)
        recognized shouldBeSameInstanceAs sudoUserNotAuthorizedException
    }

    @Test
    fun `recognizeError() should rethrow a CancellationException`() {
        val recognized = recognizeError(cancellationException)
        recognized shouldBeSameInstanceAs cancellationException
    }

    @Test
    fun `recognizeError() should map AWS NotAuthorized exception to Sudo Platform NotAuthorized exception`() {
        val recognized = recognizeError(awsNotAuthorizedException)
        recognized should beInstanceOf<SudoEntitlementsClient.EntitlementsException.AuthenticationException>()
    }

    @Test
    fun `recognizeError() should find an AWS NotAuthorized exception buried in an IOException and map it`() {
        val error = IOException(awsNotAuthorizedException)

        val recognized = recognizeError(error)

        recognized should beInstanceOf<SudoEntitlementsClient.EntitlementsException.AuthenticationException>()
        recognized.cause shouldBeSameInstanceAs awsNotAuthorizedException
    }

    @Test
    fun `recognizeError() should find an AWS NotAuthorized exception buried in an ApolloException and map it`() {
        val error = ApolloException("awsNotAuthorizedException", awsNotAuthorizedException)

        val recognized = recognizeError(error)

        recognized should beInstanceOf<SudoEntitlementsClient.EntitlementsException.AuthenticationException>()
        recognized.cause shouldBeSameInstanceAs awsNotAuthorizedException
    }

    @Test
    fun `recognizeError() should find an AWS NotAuthorized exception buried in an IOException within an ApolloException and map it`() {
        val error = ApolloException("awsNotAuthorizedException", IOException(awsNotAuthorizedException))

        val recognized = recognizeError(error)

        recognized should beInstanceOf<SudoEntitlementsClient.EntitlementsException.AuthenticationException>()
        recognized.cause shouldBeSameInstanceAs awsNotAuthorizedException
    }

    @Test
    fun `recognizeError() should map an ApolloException with a nested unrecognized exception to a FailedException`() {
        val error = ApolloException("awsNotAuthorizedException", IOException(IllegalArgumentException()))

        val recognized = recognizeError(error)

        recognized should beInstanceOf<SudoEntitlementsClient.EntitlementsException.FailedException>()
        recognized.cause shouldBeSameInstanceAs error
    }

    @Test
    fun `recognizeError() should map an unrecognized exception to a UnknownException`() {
        val error = IllegalArgumentException()

        val recognized = recognizeError(error)

        recognized should beInstanceOf<SudoEntitlementsClient.EntitlementsException.UnknownException>()
        recognized.cause shouldBeSameInstanceAs error
    }
}
