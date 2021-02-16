/*
 * Copyright © 2020 Anonyome Labs, Inc. All rights reserved.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.sudoplatform.sudoentitlements

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import com.amazonaws.auth.BasicSessionCredentials
import com.amazonaws.services.cognitoidentityprovider.AmazonCognitoIdentityProviderClient
import com.amazonaws.services.cognitoidentityprovider.model.AdminUpdateUserAttributesRequest
import com.amazonaws.services.cognitoidentityprovider.model.AttributeType
import com.sudoplatform.sudoconfigmanager.DefaultSudoConfigManager
import com.sudoplatform.sudokeymanager.KeyManagerFactory
import com.sudoplatform.sudouser.JWT
import com.sudoplatform.sudouser.SudoUserClient
import com.sudoplatform.sudouser.TESTAuthenticationProvider
import io.kotlintest.shouldBe
import io.kotlintest.shouldNotBe
import java.util.Date
import java.util.UUID
import org.json.JSONObject
import timber.log.Timber

/**
 * Test the operation of the [SudoEntitlementsClient].
 *
 * @since 2020-08-26
 */
abstract class BaseIntegrationTest {

    protected val context: Context = ApplicationProvider.getApplicationContext<Context>()

    protected val userClient by lazy {
        SudoUserClient.builder(context)
            .setNamespace("ent-client-test")
            .build()
    }

    protected val keyManager by lazy {
        KeyManagerFactory(context).createAndroidKeyManager()
    }

    protected val configManager by lazy {
        DefaultSudoConfigManager(context)
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
            keyId = keyId
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

    protected suspend fun setCustomAttributesAndSignInAgain(attributes: Map<String, String>) {
        userClient.isSignedIn() shouldBe true

        val identityServiceConfig = configManager.getConfigSet("identityService")
        identityServiceConfig shouldNotBe null

        val region = identityServiceConfig!!.getString("region")
        region shouldNotBe null

        val userPoolId = identityServiceConfig.getString("poolId")
        userPoolId shouldNotBe null

        val sessionCredentials = BasicSessionCredentials(
            InstrumentationRegistry.getArguments().getString("AWS_ACCESS_KEY_ID"),
            InstrumentationRegistry.getArguments().getString("AWS_SECRET_ACCESS_KEY"),
            InstrumentationRegistry.getArguments().getString("AWS_SESSION_TOKEN")
        )
        val identityProvider = AmazonCognitoIdentityProviderClient(sessionCredentials)

        identityProvider.adminUpdateUserAttributes(
            AdminUpdateUserAttributesRequest()
                .withUserPoolId(userPoolId)
                .withUsername(userClient.getUserName())
                .withUserAttributes(attributes.map { AttributeType().withName(it.key).withValue(it.value) })
        )

        val latest = Date(Date().time + 30000)
        while (Date().before(latest) && !attributes.keys.all { identityTokenHasAttribute(it) }) {
            // Keep signing in until we see the attributes
            userClient.signInWithKey()
        }

        attributes.keys.all { identityTokenHasAttribute(it) } shouldBe true
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

    protected fun defaultEntitlementsSetForTestUsers(): Boolean {
        val defaultEntitlementsSetForTestUsers = InstrumentationRegistry.getArguments().getString("DEFAULT_ENTITLEMENTS_SET_FOR_TEST_USERS")
        return (defaultEntitlementsSetForTestUsers ?: "false").toBoolean()
    }

    protected suspend fun enableUserForEntitlementsRedemption(entitlementsSet: String = "integration-test") {
        val customTest = mapOf("ent" to mapOf("externalId" to UUID.randomUUID().toString()))
        setCustomAttributesAndSignInAgain(
            mapOf(
                "custom:entitlementsSet" to entitlementsSet,
                "custom:test" to JSONObject(customTest).toString()
            )
        )
    }
}
