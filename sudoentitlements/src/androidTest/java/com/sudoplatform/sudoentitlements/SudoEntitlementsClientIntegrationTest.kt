/*
 * Copyright © 2022 Anonyome Labs, Inc. All rights reserved.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.sudoplatform.sudoentitlements

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.sudoplatform.sudoapiclient.ApiClientManager
import com.sudoplatform.sudoentitlements.types.EntitlementsSet
import com.sudoplatform.sudologging.AndroidUtilsLogDriver
import com.sudoplatform.sudologging.LogLevel
import com.sudoplatform.sudologging.Logger
import io.kotlintest.matchers.numerics.shouldBeGreaterThan
import io.kotlintest.matchers.numerics.shouldBeGreaterThanOrEqual
import io.kotlintest.shouldBe
import io.kotlintest.shouldNotBe
import io.kotlintest.shouldThrow
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import timber.log.Timber
import java.util.UUID

/**
 * Test the operation of the [SudoEntitlementsClient].
 */
@RunWith(AndroidJUnit4::class)
class SudoEntitlementsClientIntegrationTest : BaseIntegrationTest() {

    private val verbose = false
    private val logLevel = if (verbose) LogLevel.VERBOSE else LogLevel.INFO
    private val logger = Logger("entitlements-test", AndroidUtilsLogDriver(logLevel))

    private lateinit var entitlementsClient: SudoEntitlementsClient

    @Before
    fun init() {
        Timber.plant(Timber.DebugTree())

        if (verbose) {
            java.util.logging.Logger.getLogger("com.amazonaws").level = java.util.logging.Level.FINEST
            java.util.logging.Logger.getLogger("org.apache.http").level = java.util.logging.Level.FINEST
        }

        userClient.reset()
        entitlementsClient = SudoEntitlementsClient.builder()
            .setContext(context)
            .setSudoUserClient(userClient)
            .setLogger(logger)
            .build()
    }

    @After
    fun fini() = runBlocking {
        withTimeout(30000) {
            if (userClient.isRegistered()) {
                if (!userClient.isSignedIn()) {
                    userClient.signInWithKey()
                }
                userClient.deregister()
            }
        }
        userClient.reset()
        Timber.uprootAll()
    }

    @Test
    fun shouldThrowIfRequiredItemsNotProvidedToBuilder() {
        // Can only run if client config files are present
        assumeTrue(clientConfigFilesPresent())

        // All required items not provided
        shouldThrow<NullPointerException> {
            SudoEntitlementsClient.builder().build()
        }

        // Context not provided
        shouldThrow<NullPointerException> {
            SudoEntitlementsClient.builder()
                .setSudoUserClient(userClient)
                .build()
        }

        // SudoUserClient not provided
        shouldThrow<NullPointerException> {
            SudoEntitlementsClient.builder()
                .setContext(context)
                .build()
        }
    }

    @Test
    fun shouldNotThrowIfTheRequiredItemsAreProvidedToBuilder() {
        // Can only run if client config files are present
        assumeTrue(clientConfigFilesPresent())

        SudoEntitlementsClient.builder()
            .setContext(context)
            .setSudoUserClient(userClient)
            .build()
    }

    @Test
    fun shouldNotThrowIfAllItemsAreProvidedToBuilder() {
        // Can only run if client config files are present
        assumeTrue(clientConfigFilesPresent())

        val appSyncClient = ApiClientManager.getClient(context, userClient)

        SudoEntitlementsClient.builder()
            .setContext(context)
            .setSudoUserClient(userClient)
            .setAppSyncClient(appSyncClient)
            .setLogger(logger)
            .build()
    }

    @Test
    fun redeemEntitlementsShouldReturnEntitlementsSet() = runBlocking {
        // Can only run if client config files are present
        assumeTrue(clientConfigFilesPresent())
        assumeTrue(integrationTestEntitlementsSetAvailable())

        signInAndRegister()

        enableUserForEntitlementsRedemption()

        checkEntitlementsSet(entitlementsClient.redeemEntitlements())
    }

    @Test
    fun redeemEntitlementsShouldSucceedForRawTestUser() = runBlocking {
        // Can only run if client config files are present
        assumeTrue(clientConfigFilesPresent())

        signInAndRegister()

        val redeemedEntitlements = entitlementsClient.redeemEntitlements()
        redeemedEntitlements shouldNotBe null
    }

    @Test
    fun getEntitlementsConsumptionShouldReturnEntitlementsSet() = runBlocking {
        // Can only run if client config files are present
        assumeTrue(clientConfigFilesPresent())
        assumeTrue(integrationTestEntitlementsSetAvailable())

        signInAndRegister()

        enableUserForEntitlementsRedemption()

        val redeemed = entitlementsClient.redeemEntitlements()
        checkEntitlementsSet(redeemed)

        val consumption = entitlementsClient.getEntitlementsConsumption()

        consumption.entitlements.version shouldBe redeemed.version
        consumption.entitlements.entitlementsSetName shouldBe redeemed.name
        consumption.entitlements.entitlements.size shouldBe redeemed.entitlements.size
        for (element in consumption.entitlements.entitlements) {
            redeemed.entitlements.contains(element) shouldBe true
        }
        consumption.consumption shouldBe listOf()
    }

    @Test
    fun getEntitlementsConsumptionShouldThrowNoEntitlementsErrorForRawTestUser() = runBlocking<Unit> {
        // Can only run if client config files are present
        assumeTrue(clientConfigFilesPresent())

        signInAndRegister()

        shouldThrow<SudoEntitlementsClient.EntitlementsException.NoEntitlementsException> {
            entitlementsClient.getEntitlementsConsumption()
        }
    }

    @Test
    fun getEntitlementsShouldReturnEntitlementsSetResult() = runBlocking {
        // Can only run if client config files are present
        assumeTrue(clientConfigFilesPresent())
        assumeTrue(integrationTestEntitlementsSetAvailable())

        signInAndRegister()

        enableUserForEntitlementsRedemption()

        val redeemedEntitlements = entitlementsClient.redeemEntitlements()
        checkEntitlementsSet(redeemedEntitlements)

        val optionalRetrievedEntitlements = entitlementsClient.getEntitlements()
        optionalRetrievedEntitlements shouldNotBe null
        val retrievedEntitlements = optionalRetrievedEntitlements!!
        checkEntitlementsSet(retrievedEntitlements)

        retrievedEntitlements.name shouldBe redeemedEntitlements.name
        retrievedEntitlements.version shouldBe redeemedEntitlements.version
        retrievedEntitlements.entitlements shouldBe redeemedEntitlements.entitlements
    }

    @Test
    fun getEntitlementsShouldThrowForUnauthorizedCall() = runBlocking<Unit> {
        // Can only run if client config files are present
        assumeTrue(clientConfigFilesPresent())

        shouldThrow<SudoEntitlementsClient.EntitlementsException.NotSignedInException> {
            entitlementsClient.getEntitlements()
        }

        signInAndRegister()

        // Attempt to query entitlements after signing in then out
        userClient.globalSignOut()

        shouldThrow<SudoEntitlementsClient.EntitlementsException.NotSignedInException> {
            entitlementsClient.getEntitlements()
        }
    }

    @Test
    fun getEntitlementsShouldReturnNullForRawTestUser() = runBlocking {
        // Can only run if client config files are present
        assumeTrue(clientConfigFilesPresent())

        signInAndRegister()

        entitlementsClient.getEntitlements() shouldBe null
    }

    @Test
    fun getEntitlementsShouldSucceedForRawTestUser() = runBlocking<Unit> {
        // Can only run if client config files are present
        assumeTrue(clientConfigFilesPresent())

        signInAndRegister()

        entitlementsClient.getEntitlements()
    }

    @Test
    fun getExternalIdBeforeRedeemShouldWork() = runBlocking {
        // Can only run if client config files are present
        assumeTrue(clientConfigFilesPresent())

        signInAndRegister()

        val actualExternalId = entitlementsClient.getExternalId()

        actualExternalId shouldBe userClient.getUserName()
    }

    @Test
    fun getExternalIdAfterClaimEntitlementsRedeemShouldWork() = runBlocking {
        // Can only run if client config files are present
        assumeTrue(clientConfigFilesPresent())

        signInAndRegister()

        val expectedExternalId = enableUserForEntitlementsRedemption()

        entitlementsClient.redeemEntitlements()

        val actualExternalId = entitlementsClient.getExternalId()

        actualExternalId shouldBe expectedExternalId
    }

    @Test
    fun consumeBooleanEntitlementsShouldThrowInvalidArgumentError() = runBlocking {
        assumeTrue(clientConfigFilesPresent())

        signInAndRegister()

        shouldThrow<SudoEntitlementsClient.EntitlementsException.InvalidArgumentException> {
            entitlementsClient.consumeBooleanEntitlements(arrayOf(UUID.randomUUID().toString()))
        }
        Unit
    }

    @Test
    fun consumeBooleanEntitlementsShouldSucceed() = runBlocking {
        assumeTrue(clientConfigFilesPresent())
        assumeTrue(integrationTestEntitlementsSetAvailable())

        signInAndRegister()

        enableUserForEntitlementsRedemption()
        entitlementsClient.redeemEntitlements()

        entitlementsClient.consumeBooleanEntitlements(arrayOf("sudoplatform.test.testEntitlement-2"))
    }

    private fun checkEntitlementsSet(entitlementsSet: EntitlementsSet) {
        with(entitlementsSet) {
            name.isNotBlank() shouldBe true
            description.isNullOrBlank() shouldBe false
            createdAt.time shouldBeGreaterThan 0L
            updatedAt.time shouldBeGreaterThan 0L
            entitlements.isNotEmpty() shouldBe true
            entitlements.forEach {
                it.name.isNotBlank() shouldBe true
                it.description.isNullOrBlank() shouldBe false
                it.value shouldBeGreaterThanOrEqual 0
            }
        }
    }
}
