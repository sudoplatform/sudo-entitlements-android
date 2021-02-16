/*
 * Copyright © 2020 Anonyome Labs, Inc. All rights reserved.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.sudoplatform.sudoentitlements

import android.content.Context
import com.amazonaws.mobileconnectors.appsync.AWSAppSyncClient
import com.amazonaws.mobileconnectors.appsync.fetcher.AppSyncResponseFetchers
import com.amazonaws.services.cognitoidentity.model.NotAuthorizedException
import com.apollographql.apollo.api.Error
import com.apollographql.apollo.exception.ApolloException
import com.sudoplatform.sudoentitlements.appsync.enqueue
import com.sudoplatform.sudoentitlements.appsync.enqueueFirst
import com.sudoplatform.sudoentitlements.graphql.GetEntitlementsConsumptionQuery
import com.sudoplatform.sudoentitlements.graphql.GetEntitlementsQuery
import com.sudoplatform.sudoentitlements.graphql.RedeemEntitlementsMutation
import com.sudoplatform.sudoentitlements.logging.LogConstants
import com.sudoplatform.sudoentitlements.types.EntitlementsConsumption
import com.sudoplatform.sudoentitlements.types.EntitlementsSet
import com.sudoplatform.sudoentitlements.types.transformers.EntitlementsTransformer
import com.sudoplatform.sudologging.AndroidUtilsLogDriver
import com.sudoplatform.sudologging.LogLevel
import com.sudoplatform.sudologging.Logger
import java.util.concurrent.CancellationException

/**
 * Default implementation of the [SudoEntitlementsClient] interface.
 *
 * @property context Application context.
 * @property appSyncClient GraphQL client used to make requests to AWS and call sudo entitlements service API.
 * @property logger Errors and warnings will be logged here.
 *
 * @since 2020-08-26
 */
internal class DefaultSudoEntitlementsClient(
    private val context: Context,
    private val appSyncClient: AWSAppSyncClient,
    private val logger: Logger = Logger(LogConstants.SUDOLOG_TAG, AndroidUtilsLogDriver(LogLevel.INFO))
) : SudoEntitlementsClient {

    companion object {
        /** Exception messages */
        private const val ENTITLEMENTS_NOT_FOUND_MSG = "No entitlements returned in response"
        private const val AMBIGUOUS_ENTITLEMENTS_MSG = "Multiple conflicting entitlement sets have been recognized"
        private const val INVALID_TOKEN_MSG = "Invalid identity token recognized"
        private const val NO_ENTITLEMENTS_MSG = "No entitlements assigned to user"
        private const val SERVICE_ERROR_MSG = "Service error"

        /** Errors returned from the service */
        private const val ERROR_TYPE = "errorType"

        private const val ERROR_SERVICE = "sudoplatform.ServiceError"
        private const val ERROR_INVALID_TOKEN = "sudoplatform.InvalidTokenError"
        private const val ERROR_NO_ENTITLEMENTS = "sudoplatform.NoEntitlementsError"
        private const val ERROR_AMBIGUOUS_ENTITLEMENTS = "sudoplatform.entitlements.AmbiguousEntitlementsError"
    }

    @Throws(SudoEntitlementsClient.EntitlementsException::class)
    override suspend fun getEntitlementsConsumption(): EntitlementsConsumption {
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
            when (e) {
                is NotAuthorizedException -> throw SudoEntitlementsClient.EntitlementsException.AuthenticationException(cause = e)
                is ApolloException -> throw SudoEntitlementsClient.EntitlementsException.FailedException(cause = e)
                else -> throw interpretEntitlementsException(e)
            }
        }
    }

    @Throws(SudoEntitlementsClient.EntitlementsException::class)
    override suspend fun getEntitlements(): EntitlementsSet? {
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
            when (e) {
                is NotAuthorizedException -> throw SudoEntitlementsClient.EntitlementsException.AuthenticationException(cause = e)
                is ApolloException -> throw SudoEntitlementsClient.EntitlementsException.FailedException(cause = e)
                else -> throw interpretEntitlementsException(e)
            }
        }
    }

    @Throws(SudoEntitlementsClient.EntitlementsException::class)
    override suspend fun redeemEntitlements(): EntitlementsSet {
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
            when (e) {
                is NotAuthorizedException -> throw SudoEntitlementsClient.EntitlementsException.AuthenticationException(cause = e)
                is ApolloException -> throw SudoEntitlementsClient.EntitlementsException.FailedException(cause = e)
                else -> throw interpretEntitlementsException(e)
            }
        }
    }

    private fun interpretEntitlementsError(e: Error): SudoEntitlementsClient.EntitlementsException {
        val error = e.customAttributes()[ERROR_TYPE]?.toString() ?: ""
        if (error.contains(ERROR_AMBIGUOUS_ENTITLEMENTS)) {
            return SudoEntitlementsClient.EntitlementsException.AmbiguousEntitlementsException(AMBIGUOUS_ENTITLEMENTS_MSG)
        }
        if (error.contains(ERROR_INVALID_TOKEN)) {
            return SudoEntitlementsClient.EntitlementsException.InvalidTokenException(INVALID_TOKEN_MSG)
        }
        if (error.contains(ERROR_NO_ENTITLEMENTS)) {
            return SudoEntitlementsClient.EntitlementsException.NoEntitlementsException(NO_ENTITLEMENTS_MSG)
        }
        if (error.contains(ERROR_SERVICE)) {
            return SudoEntitlementsClient.EntitlementsException.ServiceException(SERVICE_ERROR_MSG)
        }
        return SudoEntitlementsClient.EntitlementsException.FailedException(e.toString())
    }

    private fun interpretEntitlementsException(e: Throwable): Throwable {
        return when (e) {
            is CancellationException,
            is SudoEntitlementsClient.EntitlementsException -> e
            else -> SudoEntitlementsClient.EntitlementsException.UnknownException(e)
        }
    }
}
