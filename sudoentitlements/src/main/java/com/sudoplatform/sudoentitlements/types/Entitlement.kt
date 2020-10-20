/*
 * Copyright © 2020 Anonyome Labs, Inc. All rights reserved.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.sudoplatform.sudoentitlements.types

import android.os.Parcelable
import kotlinx.android.parcel.Parcelize

/**
 * A representation of a single entitlement possessed by a user.
 *
 * @property name Name of the entitlement.
 * @property description Human readable description of the entitlement.
 * @property value The quantity of the entitlement.
 *
 * @since 2020-08-26
 */
@Parcelize
data class Entitlement(
    val name: String,
    val description: String? = null,
    val value: Int
) : Parcelable
