/*
 * Copyright © 2022 Anonyome Labs, Inc. All rights reserved.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.sudoplatform.sudoentitlements.types.transformers

import com.sudoplatform.sudoentitlements.graphql.GetEntitlementsConsumptionQuery
import com.sudoplatform.sudoentitlements.graphql.GetEntitlementsQuery
import com.sudoplatform.sudoentitlements.graphql.RedeemEntitlementsMutation
import com.sudoplatform.sudoentitlements.types.Entitlement
import com.sudoplatform.sudoentitlements.types.EntitlementConsumer
import com.sudoplatform.sudoentitlements.types.EntitlementConsumption
import com.sudoplatform.sudoentitlements.types.EntitlementsConsumption
import com.sudoplatform.sudoentitlements.types.EntitlementsSet
import com.sudoplatform.sudoentitlements.types.UserEntitlements

/**
 * Transformer responsible for transforming the [EntitlementsSet] GraphQL data
 * types to the entity type that is exposed to users.
 */
internal object EntitlementsTransformer {
    /**
     * Transform the results of [GetEntitlementsConsumptionQuery] to the publicly visible type.
     *
     * @param result The result of the GraphQL query.
     * @return The [EntitlementsConsumption] entity type.
     */
    fun toEntityFromGetEntitlementsConsumptionQueryResult(
        result: GetEntitlementsConsumptionQuery.GetEntitlementsConsumption
    ): EntitlementsConsumption {
        return EntitlementsConsumption(
            entitlements = UserEntitlements(
                version = result.entitlements().version(),
                entitlementsSetName = result.entitlements().entitlementsSetName(),
                entitlements = fromQueryEntitlements(result.entitlements().entitlements())
            ),
            consumption = fromQueryConsumption(result.consumption())
        )
    }

    private fun fromQueryEntitlements(items: List<GetEntitlementsConsumptionQuery.Entitlement>): List<Entitlement> {
        return items.map { item ->
            Entitlement(
                name = item.name(),
                description = item.description(),
                value = item.value()
            )
        }
    }

    private fun fromQueryConsumption(items: List<GetEntitlementsConsumptionQuery.Consumption>): List<EntitlementConsumption> {
        return items.map { item ->
            EntitlementConsumption(
                name = item.name(),
                consumer = item.consumer()?.let { EntitlementConsumer(id = it.id(), issuer = it.issuer()) },
                value = item.value(),
                consumed = item.consumed(),
                available = item.available(),
                firstConsumedAtEpochMs = item.firstConsumedAtEpochMs(),
                lastConsumedAtEpochMs = item.lastConsumedAtEpochMs()
            )
        }
    }

    /**
     * Transform the results of [GetEntitlementsQuery] to the publicly visible type.
     *
     * @param result The result of the GraphQL query.
     * @return The [EntitlementsSet] entity type.
     */
    fun toEntityFromGetEntitlementsQueryResult(
        result: GetEntitlementsQuery.GetEntitlements
    ): EntitlementsSet {
        return EntitlementsSet(
            name = result.name(),
            description = result.description(),
            entitlements = fromQuerySet(result.entitlements()),
            version = result.version(),
            createdAt = result.createdAtEpochMs().toDate(),
            updatedAt = result.updatedAtEpochMs().toDate()
        )
    }

    private fun fromQuerySet(items: List<GetEntitlementsQuery.Entitlement>): Set<Entitlement> {
        return items.map { item ->
            Entitlement(
                name = item.name(),
                description = item.description(),
                value = item.value()
            )
        }.toSet()
    }

    /**
     * Transform the results of [RedeemEntitlementsMutation] to the publicly visible type.
     *
     * @param result The result of the GraphQL query.
     * @return The [EntitlementsSet] entity type.
     */
    fun toEntityFromRedeemEntitlementsMutationResult(
        result: RedeemEntitlementsMutation.RedeemEntitlements
    ): EntitlementsSet {
        return EntitlementsSet(
            name = result.name(),
            description = result.description(),
            entitlements = fromMutationSet(result.entitlements()),
            version = result.version(),
            createdAt = result.createdAtEpochMs().toDate(),
            updatedAt = result.updatedAtEpochMs().toDate()
        )
    }

    private fun fromMutationSet(items: List<RedeemEntitlementsMutation.Entitlement>): Set<Entitlement> {
        return items.map { item ->
            Entitlement(
                name = item.name(),
                description = item.description(),
                value = item.value()
            )
        }.toSet()
    }
}
