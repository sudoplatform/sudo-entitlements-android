/*
 * Copyright © 2024 Anonyome Labs, Inc. All rights reserved.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.sudoplatform.sudoentitlements.types

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

/**
 * A representation of the consumption of a particular entitlement
 *
 * @property entitlements [UserEntitlements]
 *     The user's current assigned entitlements
 *
 * @property consumption [List<EntitlementsConsumption>]
 *     Consumption information for consumed entitlements.
 *     Absence of an element in this array for a particular entitlement
 *     indicates that the entitlement has not been consumed at all.
 *     For sub-user level resource consumption, absence of an element in this
 *     array for a particular potential consumer indicates that the entitlement
 *     has not be consumed at all by that consumer.
 */
@Parcelize
data class EntitlementsConsumption(
    val entitlements: UserEntitlements,
    val consumption: List<EntitlementConsumption>,
) : Parcelable
