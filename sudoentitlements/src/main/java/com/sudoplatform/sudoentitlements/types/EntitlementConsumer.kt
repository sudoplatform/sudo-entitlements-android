/*
 * Copyright © 2024 Anonyome Labs, Inc. All rights reserved.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.sudoplatform.sudoentitlements.types

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

/**
 * A representation of the sub-user level consuming resource of an entitlement
 *
 * @property id [String] ID of the resource consuming an entitlement.
 * @property issuer [String] Issuer of the ID of the consumer. For example `sudoplatform.sudoservice` for a Sudo ID.
 */
@Parcelize
data class EntitlementConsumer(val id: String, val issuer: String) : Parcelable
