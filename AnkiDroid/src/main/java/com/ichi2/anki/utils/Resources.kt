/*
 *  Copyright (c) 2023 Brayan Oliveira <brayandso.dev@gmail.com>
 *
 *  This program is free software; you can redistribute it and/or modify it under
 *  the terms of the GNU General Public License as published by the Free Software
 *  Foundation; either version 3 of the License, or (at your option) any later
 *  version.
 *
 *  This program is distributed in the hope that it will be useful, but WITHOUT ANY
 *  WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A
 *  PARTICULAR PURPOSE. See the GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License along with
 *  this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package com.ichi2.anki.utils

import android.content.Context
import android.content.res.Resources
import androidx.annotation.PluralsRes
import androidx.annotation.StringRes

/**
 * @param resId must be a [StringRes] or a [PluralsRes]
 */
fun Resources.getFormattedStringOrPlurals(resId: Int, quantity: Int): String {
    return when (getResourceTypeName(resId)) {
        "string" -> getString(resId, quantity)
        "plurals" -> getQuantityString(resId, quantity, quantity)
        else -> throw IllegalArgumentException("Provided resId is not a valid @StringRes or @PluralsRes")
    }
}

/**
 * @see [Resources.getFormattedStringOrPlurals]
 */
fun Context.getFormattedStringOrPlurals(resId: Int, quantity: Int): String {
    return resources.getFormattedStringOrPlurals(resId, quantity)
}
