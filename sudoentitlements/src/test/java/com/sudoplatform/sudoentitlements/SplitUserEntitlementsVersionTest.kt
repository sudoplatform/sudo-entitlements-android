/*
 * Copyright © 2020 Anonyome Labs, Inc. All rights reserved.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.sudoplatform.sudoentitlements

import io.kotlintest.matchers.beInstanceOf
import io.kotlintest.should
import io.kotlintest.shouldBe
import io.kotlintest.shouldNotBe
import kotlinx.coroutines.runBlocking
import org.junit.Test

/**
 * Test the correct operation of [SudoEntitlementsClient.getEntitlements] using mocks and spies.
 *
 * @since 2020-08-26
 */
class SplitUserEntitlementsVersionTest {

    @Test
    fun `splitUserEntitlementsVersion() should throw error when version is negative`() = runBlocking {
        var thrown: Throwable? = null
        try {
            splitUserEntitlementsVersion(-1.0)
        } catch (t: Throwable) {
            thrown = t
        }

        thrown shouldNotBe null
        thrown should beInstanceOf<IllegalArgumentException>()
        thrown?.message shouldBe("version negative")
    }

    @Test
    fun `splitUserEntitlementsVersion() should throw error when version is too precise`() = runBlocking {
        var thrown: Throwable? = null
        try {
            splitUserEntitlementsVersion(1.000001)
        } catch (t: Throwable) {
            thrown = t
        }

        thrown shouldNotBe null
        thrown should beInstanceOf<IllegalArgumentException>()
        thrown?.message shouldBe "version too precise"
    }

    @Test
    fun `splitUserEntitlementsVersion() should set entitlement set to zero for version with no fraction`() = runBlocking {
        val (userEntitlementsVersion, entitlementsSetVersion) = splitUserEntitlementsVersion(1.0)
        userEntitlementsVersion shouldBe 1
        entitlementsSetVersion shouldBe 0
    }

    @Test
    fun `splitUserEntitlementsVersion() should split single digit version elements`() = runBlocking {
        val (userEntitlementsVersion, entitlementsSetVersion) = splitUserEntitlementsVersion(2.00001)
        userEntitlementsVersion shouldBe 2
        entitlementsSetVersion shouldBe 1
    }

    @Test
    fun `splitUserEntitlementsVersion() should split double digit version elements`() = runBlocking {
        val (userEntitlementsVersion, entitlementsSetVersion) = splitUserEntitlementsVersion(20.0001)
        userEntitlementsVersion shouldBe 20
        entitlementsSetVersion shouldBe 10
    }
}
