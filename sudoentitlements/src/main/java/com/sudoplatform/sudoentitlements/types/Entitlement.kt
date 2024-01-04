/*
 * Copyright © 2022 Anonyome Labs, Inc. All rights reserved.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.sudoplatform.sudoentitlements.types

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

/**
 * A representation of a single entitlement possessed by a user.
 *
 * @property name [String] Name of the entitlement.
 * @property description [String] Human readable description of the entitlement.
 * @property value [Int] The quantity of the entitlement.
 */
@Parcelize
data class Entitlement(
    val name: String,
    val description: String? = null,
    val value: Int,
) : Parcelable
