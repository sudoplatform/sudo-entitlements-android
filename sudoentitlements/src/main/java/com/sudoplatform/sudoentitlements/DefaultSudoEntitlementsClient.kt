/*
 * Copyright © 2024 Anonyome Labs, Inc. All rights reserved.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.sudoplatform.sudoentitlements

import android.content.Context
import androidx.annotation.VisibleForTesting
import com.amazonaws.services.cognitoidentity.model.NotAuthorizedException
import com.amplifyframework.api.graphql.GraphQLResponse
import com.sudoplatform.sudoapiclient.ApiClientManager
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
import com.sudoplatform.sudouser.amplify.GraphQLClient
import com.sudoplatform.sudouser.exceptions.HTTP_STATUS_CODE_KEY
import java.io.IOException
import java.net.HttpURLConnection
import java.util.concurrent.CancellationException

/**
 * Default implementation of the [SudoEntitlementsClient] interface.
 *
 * @property context [Context] Application context.
 * @property sudoUserClient [SudoUserClient] Instance required to issue authentication tokens
 * @property graphQLClient [GraphQLClient] Optional GraphQL client to use. Mainly used for unit testing.
 * @property logger [Logger] Errors and warnings will be logged here.
 */
internal class DefaultSudoEntitlementsClient(
    private val context: Context,
    private val sudoUserClient: SudoUserClient,
    graphQLClient: GraphQLClient? = null,
    private val logger: Logger = Logger(LogConstants.SUDOLOG_TAG, AndroidUtilsLogDriver(LogLevel.INFO)),
) : SudoEntitlementsClient {

    companion object {
        /** Exception messages */
        private const val ENTITLEMENTS_NOT_FOUND_MSG = "No entitlements returned in response"
        private const val AMBIGUOUS_ENTITLEMENTS_MSG = "Multiple conflicting entitlement sets have been recognized"
        private const val INVALID_ARGUMENT_MSG = "Invalid argument provided"
        private const val INSUFFICIENT_ENTITLEMENTS_MSG = "Insufficient entitlements"
        private const val NO_ENTITLEMENTS_MSG = "No entitlements assigned to user"
        private const val SERVICE_ERROR_MSG = "Service error"
        private const val NO_EXTERNAL_ID_ERROR_MSG = "Token does not contain required claims to identify user's external ID"
        private const val NO_BILLING_GROUP_ERROR_MSG = "Token does not contain required claims to identify user's billing group"
        private const val ENTITLEMENTS_SEQUENCE_NOT_FOUND_ERROR_MSG = "Entitlements sequence specified in token was not found"
        private const val ENTITLEMENTS_SET_NOT_FOUND_ERROR_MSG = "Entitlements set specified in token was not found"

        /** Errors returned from the service */
        private const val ERROR_TYPE = "errorType"

        private const val ERROR_SERVICE = "sudoplatform.ServiceError"
        private const val ERROR_INVALID_ARGUMENT = "sudoplatform.InvalidArgumentError"
        private const val ERROR_INSUFFICIENT_ENTITLEMENTS = "sudoplatform.InsufficientEntitlementsError"
        private const val ERROR_NO_ENTITLEMENTS = "sudoplatform.NoEntitlementsError"
        private const val ERROR_AMBIGUOUS_ENTITLEMENTS = "sudoplatform.entitlements.AmbiguousEntitlementsError"
        private const val ERROR_NO_EXTERNAL_ID = "sudoplatform.entitlements.NoExternalIdError"
        private const val ERROR_NO_BILLING_GROUP = "sudoplatform.entitlements.NoBillingGroupError"
        private const val ERROR_ENTITLEMENTS_SEQUENCE_NOT_FOUND = "sudoplatform.entitlements.EntitlementsSequenceNotFoundError"
        private const val ERROR_ENTITLEMENTS_SET_NOT_FOUND = "sudoplatform.entitlements.EntitlementsSetNotFoundError"
    }

    /**
     * Checksums for each file are generated and are used to create a checksum that is used when
     * publishing to maven central. In order to retry a failed publish without needing to change any
     * functionality, we need a way to generate a different checksum for the source code.  We can
     * change the value of this property which will generate a different checksum for publishing
     * and allow us to retry.  The value of `version` doesn't need to be kept up-to-date with the
     * version of the code.
     */
    private val version: String = "12.0.2"

    private val graphQLClient: GraphQLClient =
        graphQLClient ?: ApiClientManager.getClient(
            context,
            this.sudoUserClient,
        )

    @Throws(SudoEntitlementsClient.EntitlementsException::class)
    override suspend fun getEntitlementsConsumption(): EntitlementsConsumption {
        if (!this.sudoUserClient.isSignedIn()) {
            throw SudoEntitlementsClient.EntitlementsException.NotSignedInException()
        }

        try {
            val queryResponse = graphQLClient.query<GetEntitlementsConsumptionQuery, GetEntitlementsConsumptionQuery.Data>(
                GetEntitlementsConsumptionQuery.OPERATION_DOCUMENT,
                emptyMap(),
            )

            if (queryResponse.hasErrors()) {
                logger.warning("errors = ${queryResponse.errors}")
                throw interpretEntitlementsError(queryResponse.errors.first())
            }

            val result = queryResponse.data?.getEntitlementsConsumption
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
            val queryResponse = graphQLClient.query<GetExternalIdQuery, GetExternalIdQuery.Data>(
                GetExternalIdQuery.OPERATION_DOCUMENT,
                emptyMap(),
            )

            if (queryResponse.hasErrors()) {
                logger.warning("errors = ${queryResponse.errors}")
                throw interpretEntitlementsError(queryResponse.errors.first())
            }

            return queryResponse.data?.getExternalId
                ?: throw SudoEntitlementsClient.EntitlementsException.FailedException(ENTITLEMENTS_NOT_FOUND_MSG)
        } catch (e: Throwable) {
            logger.debug("unexpected error $e")
            throw recognizeError(e)
        }
    }

    @Deprecated("Use getEntitlementsConsumption instead")
    @Throws(SudoEntitlementsClient.EntitlementsException::class)
    override suspend fun getEntitlements(): EntitlementsSet? {
        if (!this.sudoUserClient.isSignedIn()) {
            throw SudoEntitlementsClient.EntitlementsException.NotSignedInException()
        }

        try {
            val queryResponse = graphQLClient.query<GetEntitlementsQuery, GetEntitlementsQuery.Data>(
                GetEntitlementsQuery.OPERATION_DOCUMENT,
                emptyMap(),
            )

            if (queryResponse.hasErrors()) {
                logger.warning("errors = ${queryResponse.errors}")
                throw interpretEntitlementsError(queryResponse.errors.first())
            }

            val result = queryResponse.data?.getEntitlements
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
            val mutationResponse = graphQLClient.mutate<RedeemEntitlementsMutation, RedeemEntitlementsMutation.Data>(
                RedeemEntitlementsMutation.OPERATION_DOCUMENT,
                emptyMap(),
            )

            if (mutationResponse.hasErrors()) {
                logger.warning("errors = ${mutationResponse.errors}")
                throw interpretEntitlementsError(mutationResponse.errors.first())
            }

            val result = mutationResponse.data?.redeemEntitlements
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
            val mutationResponse = graphQLClient.mutate<ConsumeBooleanEntitlementsMutation, ConsumeBooleanEntitlementsMutation.Data>(
                ConsumeBooleanEntitlementsMutation.OPERATION_DOCUMENT,
                mapOf("entitlementNames" to entitlementNames.toList()),
            )

            if (mutationResponse.hasErrors()) {
                logger.warning("errors = ${mutationResponse.errors}")
                throw interpretEntitlementsError(mutationResponse.errors.first())
            }
        } catch (e: Throwable) {
            logger.debug("unexpected error $e")
            throw recognizeError(e)
        }
    }

    private fun interpretEntitlementsError(e: GraphQLResponse.Error): SudoEntitlementsClient.EntitlementsException {
        val httpStatusCode = e.extensions?.get(HTTP_STATUS_CODE_KEY) as Int?
        val error = e.extensions?.get(ERROR_TYPE)?.toString() ?: ""

        if (httpStatusCode == HttpURLConnection.HTTP_UNAUTHORIZED) {
            return SudoEntitlementsClient.EntitlementsException.AuthenticationException("$e")
        }
        if (httpStatusCode != null && httpStatusCode >= HttpURLConnection.HTTP_INTERNAL_ERROR) {
            return SudoEntitlementsClient.EntitlementsException.FailedException("$e")
        }
        if (error.contains(ERROR_AMBIGUOUS_ENTITLEMENTS)) {
            return SudoEntitlementsClient.EntitlementsException.AmbiguousEntitlementsException(AMBIGUOUS_ENTITLEMENTS_MSG)
        }
        if (error.contains(ERROR_INVALID_ARGUMENT)) {
            return SudoEntitlementsClient.EntitlementsException.InvalidArgumentException(INVALID_ARGUMENT_MSG)
        }
        if (error.contains(ERROR_INSUFFICIENT_ENTITLEMENTS)) {
            return SudoEntitlementsClient.EntitlementsException.InsufficientEntitlementsException(INSUFFICIENT_ENTITLEMENTS_MSG)
        }
        if (error.contains(ERROR_NO_ENTITLEMENTS)) {
            return SudoEntitlementsClient.EntitlementsException.NoEntitlementsException(NO_ENTITLEMENTS_MSG)
        }
        if (error.contains(ERROR_NO_EXTERNAL_ID)) {
            return SudoEntitlementsClient.EntitlementsException.NoExternalIdException(NO_EXTERNAL_ID_ERROR_MSG)
        }
        if (error.contains(ERROR_NO_BILLING_GROUP)) {
            return SudoEntitlementsClient.EntitlementsException.NoBillingGroupException(NO_BILLING_GROUP_ERROR_MSG)
        }
        if (error.contains(ERROR_ENTITLEMENTS_SEQUENCE_NOT_FOUND)) {
            return SudoEntitlementsClient.EntitlementsException.EntitlementsSequenceNotFoundException(
                ENTITLEMENTS_SEQUENCE_NOT_FOUND_ERROR_MSG,
            )
        }
        if (error.contains(ERROR_ENTITLEMENTS_SET_NOT_FOUND)) {
            return SudoEntitlementsClient.EntitlementsException.EntitlementsSetNotFoundException(
                ENTITLEMENTS_SET_NOT_FOUND_ERROR_MSG,
            )
        }
        if (error.contains(ERROR_SERVICE)) {
            return SudoEntitlementsClient.EntitlementsException.ServiceException(SERVICE_ERROR_MSG)
        }
        return SudoEntitlementsClient.EntitlementsException.FailedException(e.toString())
    }
}

@VisibleForTesting
fun recognizeError(e: Throwable): Throwable {
    return recognizeRootCause(e)
        ?: SudoEntitlementsClient.EntitlementsException.UnknownException(e)
}

private fun recognizeRootCause(e: Throwable?): Throwable? {
    // If we find a Sudo Platform exception, return that
    if (e?.javaClass?.`package`?.name?.startsWith("com.sudoplatform.") ?: false) {
        return e
    }

    return when (e) {
        is NotAuthorizedException -> SudoEntitlementsClient.EntitlementsException.AuthenticationException(cause = e)
        is CancellationException -> e
        is IOException -> recognizeRootCause(e.cause)
        is RuntimeException -> recognizeRootCause(e.cause)
        is SudoEntitlementsClient.EntitlementsException -> e
        else -> null
    }
}
