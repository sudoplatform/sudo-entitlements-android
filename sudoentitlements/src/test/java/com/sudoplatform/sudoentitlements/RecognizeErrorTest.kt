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

    val cancellationException = CancellationException()
    val noEntitlementsException = SudoEntitlementsClient.EntitlementsException.NoEntitlementsException()
    val sudoUserNotAuthorizedException = AuthenticationException.NotAuthorizedException()
    val awsNotAuthorizedException = NotAuthorizedException("AWS.NotAuthorizedException")

    @Test
    fun `recognizeError() should rethrow a sudo entitlements package exception`() {
        var recognized: Throwable? = null
        recognized = recognizeError(noEntitlementsException)
        recognized shouldBeSameInstanceAs noEntitlementsException
    }

    @Test
    fun `recognizeError() should rethrow another sudo SDK package exception`() {
        var recognized: Throwable? = null
        recognized = recognizeError(sudoUserNotAuthorizedException)
        recognized shouldBeSameInstanceAs sudoUserNotAuthorizedException
    }

    @Test
    fun `recognizeError() should rethrow a CancellationException`() {
        var recognized: Throwable? = null
        recognized = recognizeError(cancellationException)
        recognized shouldBeSameInstanceAs cancellationException
    }

    @Test
    fun `recognizeError() should map AWS NotAuthorized exception to Sudo Platform NotAuthorized exception`() {
        var recognized: Throwable? = null
        recognized = recognizeError(awsNotAuthorizedException)
        recognized should beInstanceOf<SudoEntitlementsClient.EntitlementsException.AuthenticationException>()
    }

    @Test
    fun `recognizeError() should find an AWS NotAuthorized exception buried in an IOException and map it`() {
        var recognized: Throwable? = null
        val error = IOException(awsNotAuthorizedException)

        recognized = recognizeError(error)

        recognized should beInstanceOf<SudoEntitlementsClient.EntitlementsException.AuthenticationException>()
        recognized.cause shouldBeSameInstanceAs awsNotAuthorizedException
    }

    @Test
    fun `recognizeError() should find an AWS NotAuthorized exception buried in an ApolloException and map it`() {
        var recognized: Throwable? = null
        val error = ApolloException("awsNotAuthorizedException", awsNotAuthorizedException)

        recognized = recognizeError(error)

        recognized should beInstanceOf<SudoEntitlementsClient.EntitlementsException.AuthenticationException>()
        recognized.cause shouldBeSameInstanceAs awsNotAuthorizedException
    }

    @Test
    fun `recognizeError() should find an AWS NotAuthorized exception buried in an IOException within an ApolloException and map it`() {
        var recognized: Throwable? = null
        val error = ApolloException("awsNotAuthorizedException", IOException(awsNotAuthorizedException))

        recognized = recognizeError(error)

        recognized should beInstanceOf<SudoEntitlementsClient.EntitlementsException.AuthenticationException>()
        recognized.cause shouldBeSameInstanceAs awsNotAuthorizedException
    }

    @Test
    fun `recognizeError() should map an ApolloException with a nested unrecognized exception to a FailedException`() {
        var recognized: Throwable? = null
        val error = ApolloException("awsNotAuthorizedException", IOException(IllegalArgumentException()))

        recognized = recognizeError(error)

        recognized should beInstanceOf<SudoEntitlementsClient.EntitlementsException.FailedException>()
        recognized.cause shouldBeSameInstanceAs error
    }

    @Test
    fun `recognizeError() should map an unrecognized exception to a UnknownException`() {
        var recognized: Throwable? = null
        val error = IllegalArgumentException()

        recognized = recognizeError(error)

        recognized should beInstanceOf<SudoEntitlementsClient.EntitlementsException.UnknownException>()
        recognized.cause shouldBeSameInstanceAs error
    }
}
