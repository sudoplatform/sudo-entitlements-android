/*
 * Copyright © 2020 Anonyome Labs, Inc. All rights reserved.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.sudoplatform.sudoentitlements

import android.content.Context
import com.amazonaws.mobileconnectors.appsync.AWSAppSyncClient
import com.sudoplatform.sudoapiclient.ApiClientManager
import com.sudoplatform.sudoentitlements.logging.LogConstants
import com.sudoplatform.sudoentitlements.types.EntitlementsSet
import com.sudoplatform.sudologging.AndroidUtilsLogDriver
import com.sudoplatform.sudologging.LogLevel
import com.sudoplatform.sudologging.Logger
import com.sudoplatform.sudouser.SudoUserClient
import java.util.Objects

/**
 * Interface encapsulating a library for interacting with the Sudo Platform Entitlements service.
 *
 * @sample com.sudoplatform.sudoentitlements.samples.Samples.sudoEntitlementsClient
 * @since 2020-08-26
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
        class InvalidTokenException(message: String? = null, cause: Throwable? = null) :
            EntitlementsException(message = message, cause = cause)
        class AuthenticationException(message: String? = null, cause: Throwable? = null) :
                EntitlementsException(message = message, cause = cause)
        class FailedException(message: String? = null, cause: Throwable? = null) :
                EntitlementsException(message = message, cause = cause)
        class UnknownException(cause: Throwable) :
                EntitlementsException(cause = cause)
    }

    /**
     * Get the current set of entitlements for the user.
     *
     * @return The [EntitlementsSet] the user currently has or null if unentitled.
     */
    @Throws(EntitlementsException::class)
    suspend fun getEntitlements(): EntitlementsSet?

    /**
     * Redeem the entitlements for the user.
     *
     * @return The current [EntitlementsSet] the user has after the redemption has completed.
     */
    @Throws(EntitlementsException::class)
    suspend fun redeemEntitlements(): EntitlementsSet
}
