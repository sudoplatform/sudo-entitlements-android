package com.sudoplatform.sudoentitlements.types

import android.os.Parcelable
import kotlinx.android.parcel.Parcelize

/**
 * A representation of the entitlements of a user and how they are assigned
 *
 * @property version
 *     Version number of the user's entitlements. This is incremented every
 *     time there is a change of entitlements set or explicit entitlements
 *     for this user. For users entitled by entitlement set, the fractional
 *     part of this version specifies the version of the entitlements set itself.
 *
 * @property entitlementsSetName
 *     Name of the entitlement set assigned to the user or undefined if the user's
 *     entitlements are assigned directly.
 *
 * @property entitlements
 *     The full set of entitlements assigned to the user.
 *
 * @since 2020-12-21
 */
@Parcelize
data class UserEntitlements(
    val version: Double,
    val entitlementsSetName: String?,
    val entitlements: List<Entitlement>
) : Parcelable
