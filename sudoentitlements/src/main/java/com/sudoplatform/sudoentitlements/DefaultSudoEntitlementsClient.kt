/*
 * Copyright © 2020 Anonyome Labs, Inc. All rights reserved.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.sudoplatform.sudoentitlements

import android.content.Context
import androidx.annotation.VisibleForTesting
import com.amazonaws.mobileconnectors.appsync.AWSAppSyncClient
import com.amazonaws.mobileconnectors.appsync.fetcher.AppSyncResponseFetchers
import com.amazonaws.services.cognitoidentity.model.NotAuthorizedException
import com.apollographql.apollo.api.Error
import com.apollographql.apollo.exception.ApolloException
import com.sudoplatform.sudoapiclient.ApiClientManager
import com.sudoplatform.sudoentitlements.appsync.enqueue
import com.sudoplatform.sudoentitlements.appsync.enqueueFirst
import com.sudoplatform.sudoentitlements.graphql.ConsumeBooleanEntitlementsMutation
import com.sudoplatform.sudoentitlements.graphql.GetEntitlementsConsumptionQuery
import com.sudoplatform.sudoentitlements.graphql.GetEntitlementsQuery
import com.sudoplatform.sudoentitlements.graphql.GetExternalIdQuery
import com.sudoplatform.sudoentitlements.graphql.RedeemEntitlementsMutation
import com.sudoplatform.sudoentitlements.logging.LogConstants
import com.sudoplatform.sudoentitlements.types.EntitlementsConsumption
import com.sudoplatform.sudoentitlements.types.EntitlementsSet
import com.sudoplatform.sudoentitlements.types.transformers.EntitlementsTransformer
import com.sudoplatform.sudologging.AndroidUtilsLogDriver
import com.sudoplatform.sudologging.LogLevel
import com.sudoplatform.sudologging.Logger
import com.sudoplatform.sudouser.SudoUserClient
import java.io.IOException
import java.util.concurrent.CancellationException

/**
 * Default implementation of the [SudoEntitlementsClient] interface.
 *
 * @property context Application context.
 * @property sudoUserClient `SudoUserClient` instance required to issue authentication tokens
 * @property appSyncClient optional AppSync client to use. Mainly used for unit testing.
 * @property logger Errors and warnings will be logged here.
 *
 * @since 2020-08-26
 */
internal class DefaultSudoEntitlementsClient(
    private val context: Context,
    private val sudoUserClient: SudoUserClient,
    appSyncClient: AWSAppSyncClient? = null,
    private val logger: Logger = Logger(LogConstants.SUDOLOG_TAG, AndroidUtilsLogDriver(LogLevel.INFO))
) : SudoEntitlementsClient {

    companion object {
        /** Exception messages */
        private const val ENTITLEMENTS_NOT_FOUND_MSG = "No entitlements returned in response"
        private const val AMBIGUOUS_ENTITLEMENTS_MSG = "Multiple conflicting entitlement sets have been recognized"
        private const val INVALID_ARGUMENT_MSG = "Invalid argument provided"
        private const val INVALID_TOKEN_MSG = "Invalid identity token recognized"
        private const val INSUFFICIENT_ENTITLEMENTS_MSG = "Insufficient entitlements"
        private const val NO_ENTITLEMENTS_MSG = "No entitlements assigned to user"
        private const val SERVICE_ERROR_MSG = "Service error"

        /** Errors returned from the service */
        private const val ERROR_TYPE = "errorType"

        private const val ERROR_SERVICE = "sudoplatform.ServiceError"
        private const val ERROR_INVALID_ARGUMENT = "sudoplatform.InvalidArgumentError"
        private const val ERROR_INVALID_TOKEN = "sudoplatform.InvalidTokenError"
        private const val ERROR_INSUFFICIENT_ENTITLEMENTS = "sudoplatform.InsufficientEntitlementsError"
        private const val ERROR_NO_ENTITLEMENTS = "sudoplatform.NoEntitlementsError"
        private const val ERROR_AMBIGUOUS_ENTITLEMENTS = "sudoplatform.entitlements.AmbiguousEntitlementsError"
    }

    /**
     * Checksum's for each file are generated and are used to create a checksum that is used when
     * publishing to maven central. In order to retry a failed publish without needing to change any
     * functionality, we need a way to generate a different checksum for the source code.  We can
     * change the value of this property which will generate a different checksum for publishing
     * and allow us to retry.  The value of `version` doesn't need to be kept up-to-date with the
     * version of the code.
     */
    private val version: String = "2.3.1"

    private val appSyncClient: AWSAppSyncClient

    init {
        this.appSyncClient = appSyncClient ?: ApiClientManager.getClient(
            context,
            this.sudoUserClient
        )
    }

    @Throws(SudoEntitlementsClient.EntitlementsException::class)
    override suspend fun getEntitlementsConsumption(): EntitlementsConsumption {
        if (!this.sudoUserClient.isSignedIn()) {
            throw SudoEntitlementsClient.EntitlementsException.NotSignedInException()
        }

        try {
            val query = GetEntitlementsConsumptionQuery.builder().build()

            val queryResponse = appSyncClient.query(query)
                .responseFetcher(AppSyncResponseFetchers.NETWORK_ONLY)
                .enqueueFirst()

            if (queryResponse.hasErrors()) {
                logger.warning("errors = ${queryResponse.errors()}")
                throw interpretEntitlementsError(queryResponse.errors().first())
            }

            val result = queryResponse.data()?.entitlementsConsumption
                ?: throw SudoEntitlementsClient.EntitlementsException.FailedException(ENTITLEMENTS_NOT_FOUND_MSG)
            return result.let { EntitlementsTransformer.toEntityFromGetEntitlementsConsumptionQueryResult(it) }
        } catch (e: Throwable) {
            logger.debug("unexpected error $e")
            throw recognizeError(e)
        }
    }

    @Throws(SudoEntitlementsClient.EntitlementsException::class)
    override suspend fun getExternalId(): String {
        if (!this.sudoUserClient.isSignedIn()) {
            throw SudoEntitlementsClient.EntitlementsException.NotSignedInException()
        }

        try {
            val query = GetExternalIdQuery.builder().build()

            val queryResponse = appSyncClient.query(query)
                .responseFetcher(AppSyncResponseFetchers.NETWORK_ONLY)
                .enqueueFirst()

            if (queryResponse.hasErrors()) {
                logger.warning("errors = ${queryResponse.errors()}")
                throw interpretEntitlementsError(queryResponse.errors().first())
            }

            val result = queryResponse.data()?.externalId
                ?: throw SudoEntitlementsClient.EntitlementsException.FailedException(ENTITLEMENTS_NOT_FOUND_MSG)
            return result
        } catch (e: Throwable) {
            logger.debug("unexpected error $e")
            throw recognizeError(e)
        }
    }

    @Throws(SudoEntitlementsClient.EntitlementsException::class)
    override suspend fun getEntitlements(): EntitlementsSet? {
        if (!this.sudoUserClient.isSignedIn()) {
            throw SudoEntitlementsClient.EntitlementsException.NotSignedInException()
        }

        try {
            val query = GetEntitlementsQuery.builder().build()

            val queryResponse = appSyncClient.query(query)
                .responseFetcher(AppSyncResponseFetchers.NETWORK_ONLY)
                .enqueueFirst()

            if (queryResponse.hasErrors()) {
                logger.warning("errors = ${queryResponse.errors()}")
                throw interpretEntitlementsError(queryResponse.errors().first())
            }

            val result = queryResponse.data()?.entitlements
            return result?.let { EntitlementsTransformer.toEntityFromGetEntitlementsQueryResult(it) }
        } catch (e: Throwable) {
            logger.debug("unexpected error $e")
            throw recognizeError(e)
        }
    }

    @Throws(SudoEntitlementsClient.EntitlementsException::class)
    override suspend fun redeemEntitlements(): EntitlementsSet {
        if (!this.sudoUserClient.isSignedIn()) {
            throw SudoEntitlementsClient.EntitlementsException.NotSignedInException()
        }

        try {
            val mutation = RedeemEntitlementsMutation.builder()
                .build()

            val mutationResponse = appSyncClient.mutate(mutation)
                .enqueue()

            if (mutationResponse.hasErrors()) {
                logger.warning("errors = ${mutationResponse.errors()}")
                throw interpretEntitlementsError(mutationResponse.errors().first())
            }

            val result = mutationResponse.data()?.redeemEntitlements()
                ?: throw SudoEntitlementsClient.EntitlementsException.FailedException(ENTITLEMENTS_NOT_FOUND_MSG)
            return EntitlementsTransformer.toEntityFromRedeemEntitlementsMutationResult(result)
        } catch (e: Throwable) {
            logger.debug("unexpected error $e")
            throw recognizeError(e)
        }
    }

    @Throws(SudoEntitlementsClient.EntitlementsException::class)
    override suspend fun consumeBooleanEntitlements(entitlementNames: Array<String>) {
        if (!this.sudoUserClient.isSignedIn()) {
            throw SudoEntitlementsClient.EntitlementsException.NotSignedInException()
        }

        try {
            val mutation = ConsumeBooleanEntitlementsMutation.builder()
                .entitlementNames(entitlementNames.toList())
                .build()

            val mutationResponse = appSyncClient.mutate(mutation)
                .enqueue()

            if (mutationResponse.hasErrors()) {
                logger.warning("errors = ${mutationResponse.errors()}")
                throw interpretEntitlementsError(mutationResponse.errors().first())
            }
        } catch (e: Throwable) {
            logger.debug("unexpected error $e")
            throw recognizeError(e)
        }
    }

    private fun interpretEntitlementsError(e: Error): SudoEntitlementsClient.EntitlementsException {
        val error = e.customAttributes()[ERROR_TYPE]?.toString() ?: ""
        if (error.contains(ERROR_AMBIGUOUS_ENTITLEMENTS)) {
            return SudoEntitlementsClient.EntitlementsException.AmbiguousEntitlementsException(AMBIGUOUS_ENTITLEMENTS_MSG)
        }
        if (error.contains(ERROR_INVALID_ARGUMENT)) {
            return SudoEntitlementsClient.EntitlementsException.InvalidArgumentException(INVALID_ARGUMENT_MSG)
        }
        if (error.contains(ERROR_INVALID_TOKEN)) {
            return SudoEntitlementsClient.EntitlementsException.InvalidTokenException(INVALID_TOKEN_MSG)
        }
        if (error.contains(ERROR_INSUFFICIENT_ENTITLEMENTS)) {
            return SudoEntitlementsClient.EntitlementsException.InsufficientEntitlementsException(INSUFFICIENT_ENTITLEMENTS_MSG)
        }
        if (error.contains(ERROR_NO_ENTITLEMENTS)) {
            return SudoEntitlementsClient.EntitlementsException.NoEntitlementsException(NO_ENTITLEMENTS_MSG)
        }
        if (error.contains(ERROR_SERVICE)) {
            return SudoEntitlementsClient.EntitlementsException.ServiceException(SERVICE_ERROR_MSG)
        }
        return SudoEntitlementsClient.EntitlementsException.FailedException(e.toString())
    }
}

@VisibleForTesting
fun recognizeError(e: Throwable): Throwable {
    return recognizeRootCause(e) ?: when (e) {
        is ApolloException -> SudoEntitlementsClient.EntitlementsException.FailedException(cause = e)
        else -> SudoEntitlementsClient.EntitlementsException.UnknownException(e)
    }
}

private fun recognizeRootCause(e: Throwable?): Throwable? {
    // If we find a Sudo Platform exception, return that
    if (e?.javaClass?.`package`?.name?.startsWith("com.sudoplatform.") ?: false) {
        return e
    }

    return when (e) {
        is NotAuthorizedException -> SudoEntitlementsClient.EntitlementsException.AuthenticationException(cause = e)
        is ApolloException -> recognizeRootCause(e.cause)
        is CancellationException -> e
        is IOException -> recognizeRootCause(e.cause)
        is RuntimeException -> recognizeRootCause(e.cause)
        is SudoEntitlementsClient.EntitlementsException -> e
        else -> null
    }
}
