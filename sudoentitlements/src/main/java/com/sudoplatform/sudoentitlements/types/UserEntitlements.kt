/*
 * Copyright © 2022 Anonyome Labs, Inc. All rights reserved.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.sudoplatform.sudoentitlements.types

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

/**
 * A representation of the entitlements of a user and how they are assigned
 *
 * @property version [Double]
 *     Version number of the user's entitlements. This is incremented every
 *     time there is a change of entitlements set or explicit entitlements
 *     for this user. For users entitled by entitlement set, the fractional
 *     part of this version specifies the version of the entitlements set itself.
 *
 * @property entitlementsSetName [String]
 *     Name of the entitlement set assigned to the user or undefined if the user's
 *     entitlements are assigned directly.
 *
 * @property entitlements [List<Entitlement>]
 *     The full set of entitlements assigned to the user.
 */
@Parcelize
data class UserEntitlements(
    val version: Double,
    val entitlementsSetName: String?,
    val entitlements: List<Entitlement>
) : Parcelable
