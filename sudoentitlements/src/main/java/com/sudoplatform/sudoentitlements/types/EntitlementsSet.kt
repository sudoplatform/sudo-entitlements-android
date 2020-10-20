/*
 * Copyright © 2020 Anonyome Labs, Inc. All rights reserved.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.sudoplatform.sudoentitlements.types

import android.os.Parcelable
import kotlinx.android.parcel.Parcelize
import java.util.Date

/**
 * A representation of a set of entitlements possessed by a user.
 *
 * @property name Name of the set of entitlements. This will often be a few words separated by dots like an internet domain.
 * @property description Human readable description of the set of entitlements.
 * @property entitlements The [Set] of [Entitlement]s.
 * @property version The version number of this set of entitlements.
 * @property createdAt [Date] when the set of entitlements was created.
 * @property updatedAt [Date] when the set of entitlements was last updated.
 *
 * @since 2020-08-26
 */
@Parcelize
data class EntitlementsSet(
    val name: String,
    val description: String? = null,
    val entitlements: Set<Entitlement>,
    val version: Double,
    val createdAt: Date,
    val updatedAt: Date
) : Parcelable
