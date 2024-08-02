/*
 * Copyright © 2024 Anonyome Labs, Inc. All rights reserved.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.sudoplatform.sudoentitlements

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import com.sudoplatform.sudoentitlementsadmin.SudoEntitlementsAdminClient
import com.sudoplatform.sudoentitlementsadmin.types.Entitlement
import com.sudoplatform.sudokeymanager.KeyManagerFactory
import com.sudoplatform.sudouser.JWT
import com.sudoplatform.sudouser.SudoUserClient
import com.sudoplatform.sudouser.TESTAuthenticationProvider
import io.kotlintest.shouldBe
import io.kotlintest.shouldNotBe
import timber.log.Timber

/**
 * Test the operation of the [SudoEntitlementsClient].
 */
abstract class BaseIntegrationTest {

    protected val context: Context = ApplicationProvider.getApplicationContext()

    protected val userClient by lazy {
        SudoUserClient.builder(context)
            .setNamespace("ent-client-test")
            .build()
    }

    protected val keyManager by lazy {
        KeyManagerFactory(context).createAndroidKeyManager()
    }

    protected val entitlementsAdminClient by lazy {
        val value = InstrumentationRegistry.getArguments().getString("ADMIN_API_KEY")
        val apiKey = value ?: readTextFile("api.key")
        SudoEntitlementsAdminClient.builder(context, apiKey).build()
    }

    private suspend fun register() {
        userClient.isRegistered() shouldBe false

        val privateKey = readTextFile("register_key.private")
        val keyId = readTextFile("register_key.id")

        val authProvider = TESTAuthenticationProvider(
            name = "ent-client-test",
            privateKey = privateKey,
            publicKey = null,
            keyManager = keyManager,
            keyId = keyId,
        )

        userClient.registerWithAuthenticationProvider(authProvider, "ent-client-test")
    }

    private fun readTextFile(fileName: String): String {
        return context.assets.open(fileName).bufferedReader().use {
            it.readText().trim()
        }
    }

    private fun identityTokenHasAttribute(name: String): Boolean {
        userClient.isSignedIn() shouldBe true

        val encodedIdentityToken = userClient.getIdToken()
        encodedIdentityToken shouldNotBe null

        val identityToken = JWT.decode(encodedIdentityToken!!)
        identityToken shouldNotBe null

        return identityToken!!.payload.has(name)
    }

    protected suspend fun signInAndRegister() {
        if (!userClient.isRegistered()) {
            register()
        }
        userClient.isRegistered() shouldBe true
        if (userClient.isSignedIn()) {
            userClient.getRefreshToken()?.let { userClient.refreshTokens(it) }
        } else {
            userClient.signInWithKey()
        }
        userClient.isSignedIn() shouldBe true
    }

    protected fun clientConfigFilesPresent(): Boolean {
        val configFiles = context.assets.list("")?.filter { fileName ->
            fileName == "sudoplatformconfig.json" ||
                fileName == "register_key.private" ||
                fileName == "register_key.id"
        } ?: emptyList()
        Timber.d("config files present ${configFiles.size}")
        return configFiles.size == 3
    }

    protected fun integrationTestEntitlementsSetAvailable(): Boolean {
        val integrationTestEntitlementsSet = InstrumentationRegistry.getArguments().getString("INTEGRATION_TEST_ENTITLEMENTS_SET_AVAILABLE")
        return (integrationTestEntitlementsSet ?: "false").toBoolean()
    }

    /**
     * @return external ID of user
     */
    protected suspend fun enableUserForEntitlementsRedemption(entitlementsSet: String = "integration-test"): String {
        userClient.isSignedIn() shouldBe true
        val username = userClient.getUserName()
        require(username != null) { "Username is null" }
        entitlementsAdminClient.applyEntitlementsSetToUser(username, entitlementsSet)
        return username
    }

    /**
     * @return external ID of user
     */
    protected suspend fun enableUserForEntitlementsRedemption(entitlement: Entitlement): String {
        userClient.isSignedIn() shouldBe true
        val username = userClient.getUserName()
        require(username != null) { "Username is null" }
        entitlementsAdminClient.applyEntitlementsToUser(username, listOf(entitlement))
        return username
    }
}
