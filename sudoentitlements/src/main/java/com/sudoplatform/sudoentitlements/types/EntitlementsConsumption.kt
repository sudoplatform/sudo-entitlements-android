package com.sudoplatform.sudoentitlements.types

import android.os.Parcelable
import kotlinx.android.parcel.Parcelize

/**
 * A representation of the consumption of a particular entitlement
 *
 * @property entitlements
 *     The user's current assigned entitlements
 *
 * @property consumption
 *     Consumption information for consumed entitlements.
 *     Absence of an element in this array for a particular entitlement
 *     indicates that the entitlement has not been consumed at all.
 *     For sub-user level resource consumption, absence of an element in this
 *     array for a particular potential consumer indicates that the entitlement
 *     has not be consumed at all by that consumer.
 *
 * @since 2020-12-21
 */
@Parcelize
data class EntitlementsConsumption(
    val entitlements: UserEntitlements,
    val consumption: List<EntitlementConsumption>
) : Parcelable
