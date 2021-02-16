package com.sudoplatform.sudoentitlements.types

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

/**
 * A representation of the consumption of a particular entitlement
 *
 * @property name
 *     Name of the consumed entitlement.
 *
 * @property consumer
 *     Consumer of the entitlement. If present this indicates the sub-user
 *     level resource responsible for consumption of the entitlement. If not present,
 *     the entitlement is consumed directly by the user.
 *
 * @property value
 *     The maximum amount of the entitlement that can be consumed by the consumer
 *
 * @property consumed
 *     The amount of the entitlement that has been consumed
 *
 * @property available
 *     The amount of the entitlement that is yet to be consumed. Provided for convenience.
 *     `available` + `consumed` always equals `value`
 *
 * @property firstConsumedAtEpochMs
 *      Time at which this entitlement was first consumed
 *
 * @property lastConsumedAtEpochMs
 *      Time of the most recent consumption of this entitlement
 *
 * @since 2020-12-21
 */
@Parcelize
data class EntitlementConsumption(
    val name: String,
    val consumer: EntitlementConsumer?,
    val value: Int,
    val consumed: Int,
    val available: Int,
    val firstConsumedAtEpochMs: Double?,
    val lastConsumedAtEpochMs: Double?
) : Parcelable
