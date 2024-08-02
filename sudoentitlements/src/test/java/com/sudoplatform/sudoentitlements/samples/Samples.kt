/*
 * Copyright © 2024 Anonyome Labs, Inc. All rights reserved.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.sudoplatform.sudoentitlements.samples

import android.content.Context
import com.sudoplatform.sudoentitlements.BaseTests
import com.sudoplatform.sudoentitlements.SudoEntitlementsClient
import com.sudoplatform.sudouser.SudoUserClient
import org.junit.Test
import org.mockito.kotlin.mock

/**
 * These are sample snippets of code that are included in the generated documentation. They are
 * placed here in the test code to ensure they will compile.
 */
@Suppress("UNUSED_VARIABLE")
class Samples : BaseTests() {

    private val context by before { mock<Context>() }

    @Test
    fun mockTest() {
        // Just to keep junit happy
    }

    fun sudoEntitlementsClient(sudoUserClient: SudoUserClient) {
        val client = SudoEntitlementsClient.builder()
            .setContext(context)
            .setSudoUserClient(sudoUserClient)
            .build()
    }
}
