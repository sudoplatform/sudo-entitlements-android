/*
 * Copyright © 2022 Anonyome Labs, Inc. All rights reserved.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.sudoplatform.sudoentitlements

import android.content.Context
import com.amazonaws.mobileconnectors.appsync.AWSAppSyncClient
import com.sudoplatform.sudoapiclient.ApiClientManager
import com.sudoplatform.sudoentitlements.logging.LogConstants
import com.sudoplatform.sudoentitlements.types.EntitlementsConsumption
import com.sudoplatform.sudoentitlements.types.EntitlementsSet
import com.sudoplatform.sudoentitlements.types.UserEntitlements
import com.sudoplatform.sudoentitlements.SudoEntitlementsClient.EntitlementsException.InsufficientEntitlementsException
import com.sudoplatform.sudoentitlements.SudoEntitlementsClient.EntitlementsException.InvalidArgumentException
import com.sudoplatform.sudoentitlements.SudoEntitlementsClient.EntitlementsException.NotSignedInException
import com.sudoplatform.sudoentitlements.SudoEntitlementsClient.EntitlementsException.ServiceException
import com.sudoplatform.sudologging.AndroidUtilsLogDriver
import com.sudoplatform.sudologging.LogLevel
import com.sudoplatform.sudologging.Logger
import com.sudoplatform.sudouser.SudoUserClient
import java.util.Objects

/**
 * Interface encapsulating a library for interacting with the Sudo Platform Entitlements service.
 *
 * @sample com.sudoplatform.sudoentitlements.samples.Samples.sudoEntitlementsClient
 */
interface SudoEntitlementsClient {

    companion object {
        /** Create a [Builder] for [SudoEntitlementsClient]. */
        @JvmStatic
        fun builder() = Builder()
    }

    /**
     * Builder used to construct the [SudoEntitlementsClient].
     */
    class Builder internal constructor() {
        private var context: Context? = null
        private var sudoUserClient: SudoUserClient? = null
        private var appSyncClient: AWSAppSyncClient? = null
        private var logger: Logger = Logger(LogConstants.SUDOLOG_TAG, AndroidUtilsLogDriver(LogLevel.INFO))

        /**
         * Provide the application context (required input).
         */
        fun setContext(context: Context) = also {
            this.context = context
        }

        /**
         * Provide the implementation of the [SudoUserClient] used to perform
         * sign in and ownership operations (required input).
         */
        fun setSudoUserClient(sudoUserClient: SudoUserClient) = also {
            this.sudoUserClient = sudoUserClient
        }

        /**
         * Provide an [AWSAppSyncClient] for the [SudoEntitlementsClient] to use
         * (optional input). If this is not supplied, an [AWSAppSyncClient] will
         * be constructed and used.
         */
        fun setAppSyncClient(appSyncClient: AWSAppSyncClient) = also {
            this.appSyncClient = appSyncClient
        }

        /**
         * Provide the implementation of the [Logger] used for logging errors (optional input).
         * If a value is not supplied a default implementation will be used.
         */
        fun setLogger(logger: Logger) = also {
            this.logger = logger
        }

        /**
         * Construct the [SudoEntitlementsClient]. Will throw a [NullPointerException] if
         * the [context] and [sudoUserClient] has not been provided.
         */
        @Throws(NullPointerException::class)
        fun build(): SudoEntitlementsClient {
            Objects.requireNonNull(context, "Context must be provided.")
            Objects.requireNonNull(sudoUserClient, "SudoUserClient must be provided.")

            val appSyncClient = appSyncClient
                ?: ApiClientManager.getClient(this@Builder.context!!, this@Builder.sudoUserClient!!)

            return DefaultSudoEntitlementsClient(
                context = context!!,
                sudoUserClient = this@Builder.sudoUserClient!!,
                appSyncClient = appSyncClient,
                logger = logger
            )
        }
    }

    /**
     * Defines the exceptions for the entitlement methods.
     *
     * @property message Accompanying message for the exception.
     * @property cause The cause for the exception.
     */
    sealed class EntitlementsException(message: String? = null, cause: Throwable? = null) : RuntimeException(message, cause) {
        class AmbiguousEntitlementsException(message: String? = null, cause: Throwable? = null) :
            EntitlementsException(message = message, cause = cause)
        class AuthenticationException(message: String? = null, cause: Throwable? = null) :
            EntitlementsException(message = message, cause = cause)
        class FailedException(message: String? = null, cause: Throwable? = null) :
            EntitlementsException(message = message, cause = cause)
        class InsufficientEntitlementsException(message: String? = null, cause: Throwable? = null) :
            EntitlementsException(message = message, cause = cause)
        class InvalidArgumentException(message: String? = null, cause: Throwable? = null) :
            EntitlementsException(message = message, cause = cause)
        class InvalidTokenException(message: String? = null, cause: Throwable? = null) :
            EntitlementsException(message = message, cause = cause)
        class NoEntitlementsException(message: String? = null, cause: Throwable? = null) :
            EntitlementsException(message = message, cause = cause)
        class NoExternalIdException(message: String? = null, cause: Throwable? = null) :
            EntitlementsException(message = message, cause = cause)
        class NoBillingGroupException(message: String? = null, cause: Throwable? = null) :
            EntitlementsException(message = message, cause = cause)
        class EntitlementsSequenceNotFoundException(message: String? = null, cause: Throwable? = null) :
            EntitlementsException(message = message, cause = cause)
        class EntitlementsSetNotFoundException(message: String? = null, cause: Throwable? = null) :
            EntitlementsException(message = message, cause = cause)
        class ServiceException(message: String? = null, cause: Throwable? = null) :
            EntitlementsException(message = message, cause = cause)
        class NotSignedInException(message: String? = null, cause: Throwable? = null) :
            EntitlementsException(message = message, cause = cause)
        class UnknownException(cause: Throwable) :
            EntitlementsException(cause = cause)
    }

    /**
     * Get the current set of entitlements and their consumption for the user.
     *
     * @return The [EntitlementsConsumption] for the user
     */
    @Throws(EntitlementsException::class)
    suspend fun getEntitlementsConsumption(): EntitlementsConsumption

    /**
     * Get the current set of entitlements for the user.
     *
     * @return The [EntitlementsSet] the user currently has or null if unentitled.
     */
    @Deprecated("Use getEntitlementsConsumption instead")
    @Throws(EntitlementsException::class)
    suspend fun getEntitlements(): EntitlementsSet?

    /**
     * Get the user's external ID as determined by entitlements service
     *
     * @return The external ID of the user
     */
    @Throws(EntitlementsException::class)
    suspend fun getExternalId(): String

    /**
     * Redeem the entitlements for the user.
     *
     * @return The current [EntitlementsSet] the user has after the redemption has completed.
     */
    @Throws(EntitlementsException::class)
    suspend fun redeemEntitlements(): EntitlementsSet

    /**
     * Record consumption of a set of boolean entitlements.
     *
     * This is to support services that want a record of
     * usage recorded but have no service side enforcement
     * point.
     *
     * @param entitlementNames Boolean entitlement names to record consumption of
     *
     * @throws NotSignedInException
     *   User is not signed in
     *
     * @throws InsufficientEntitlementsException
     *   User is not entitled to one or more of the boolean entitlements.
     *   Check entitlements and that redeemEntitlements has been called
     *   for the user.
     *
     * @throws InvalidArgumentException
     *   One or more of the specified entitlement names does not correspond
     *   to a boolean entitlement defined to the entitlements service
     *
     * @throws ServiceException
     *   An error occurred within the entitlements service that indicates an
     *   issue with the configuration or operation of the service.
     */
    @Throws(EntitlementsException::class)
    suspend fun consumeBooleanEntitlements(entitlementNames: Array<String>)
}

/**
 * Scaling factor used to scale down the entitlements set version when
 * constructing a composite version as in [UserEntitlements] and [EntitlementsSet]
 * types.
 */
const val entitlementsSetVersionScalingFactor = 100000

@Throws(IllegalArgumentException::class)
fun splitUserEntitlementsVersion(version: Double): Pair<Long, Long> {
    if (version < 0) {
        throw IllegalArgumentException("version negative")
    }
    val userEntitlementsVersion = Math.round(version)
    val entitlementsSetVersion = Math.round(version * entitlementsSetVersionScalingFactor % entitlementsSetVersionScalingFactor)

    val reconstructed = userEntitlementsVersion + entitlementsSetVersion.toDouble() / entitlementsSetVersionScalingFactor
    if (reconstructed != version) {
        throw IllegalArgumentException("version too precise")
    }

    return Pair(userEntitlementsVersion, entitlementsSetVersion)
}
