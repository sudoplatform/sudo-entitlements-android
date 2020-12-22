package com.sudoplatform.sudoentitlements.types

import android.os.Parcelable
import kotlinx.android.parcel.Parcelize

/**
 * A representation of the sub-user level consuming resource of an entitlement
 *
 * @property id ID of the resource consuming an entitlement
 * @property issuer Issuer of the ID of the consumer. For example `sudoplatform.sudoservice` for a Sudo ID
 *
 * @since 2020-12-21
 */
@Parcelize
data class EntitlementConsumer(val id: String, val issuer: String) : Parcelable
