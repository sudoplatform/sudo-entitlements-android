/*
 * Copyright © 2022 Anonyome Labs, Inc. All rights reserved.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.sudoplatform.sudoentitlements.types.transformers

import java.util.Date

/**
 * Extensions used by several data transformers.
 */
internal fun Double.toDate(): Date {
    return Date(this.toLong())
}

internal fun Double?.toDate(): Date? {
    if (this == null) {
        return null
    }
    return Date(this.toLong())
}
