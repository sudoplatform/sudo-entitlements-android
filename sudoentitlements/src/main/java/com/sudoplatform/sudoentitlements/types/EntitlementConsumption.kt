/*
 * Copyright © 2022 Anonyome Labs, Inc. All rights reserved.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.sudoplatform.sudoentitlements.types

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

/**
 * A representation of the consumption of a particular entitlement
 *
 * @property name [String]
 *     Name of the consumed entitlement.
 *
 * @property consumer [EntitlementConsumer]
 *     Consumer of the entitlement. If present this indicates the sub-user
 *     level resource responsible for consumption of the entitlement. If not present,
 *     the entitlement is consumed directly by the user.
 *
 * @property value [Long]
 *     The maximum amount of the entitlement that can be consumed by the consumer
 *
 * @property consumed [Long]
 *     The amount of the entitlement that has been consumed
 *
 * @property available [Long]
 *     The amount of the entitlement that is yet to be consumed. Provided for convenience.
 *     `available` + `consumed` always equals `value`
 *
 * @property firstConsumedAtEpochMs [Double]
 *      Time at which this entitlement was first consumed
 *
 * @property lastConsumedAtEpochMs [Double]
 *      Time of the most recent consumption of this entitlement
 */
@Parcelize
data class EntitlementConsumption(
    val name: String,
    val consumer: EntitlementConsumer?,
    val value: Long,
    val consumed: Long,
    val available: Long,
    val firstConsumedAtEpochMs: Double?,
    val lastConsumedAtEpochMs: Double?,
) : Parcelable
